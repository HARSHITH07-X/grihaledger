const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const path = require("node:path");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";

const ADMIN_UID = "uid_ananya";
const MEMBER_UID = "uid_rohan";
const VIEWER_UID = "uid_dadi";
const JOINER_UID = "uid_vikram";
const OUTSIDER_UID = "uid_stranger";

const FAMILY_ID = "fam_sharma";
const OTHER_FAMILY_ID = "fam_other";

const VALID_CODE = "ABCD234567";
const EXPIRED_CODE = "EXPD234567";
const USED_CODE = "USED234567";
const REVOKED_CODE = "REVK234567";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function nowTs(offsetMs = 0) {
  return new Date(Date.now() + offsetMs);
}

before(async () => {
  const rulesPath = path.resolve(__dirname, "..", "firestore.rules");
  const rules = fs.readFileSync(rulesPath, "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

async function seedFamilyAndMembers() {
  const past = nowTs(-60_000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ADMIN_UID).set({
      uid: ADMIN_UID,
      displayName: "Ananya",
      email: "ananya@test.in",
      familyId: FAMILY_ID,
      createdAt: past,
      updatedAt: past,
    });
    await db.collection("users").doc(MEMBER_UID).set({
      uid: MEMBER_UID,
      displayName: "Rohan",
      email: "rohan@test.in",
      familyId: FAMILY_ID,
      createdAt: past,
      updatedAt: past,
    });
    await db.collection("users").doc(VIEWER_UID).set({
      uid: VIEWER_UID,
      displayName: "Kamla",
      email: "kamla@test.in",
      familyId: FAMILY_ID,
      createdAt: past,
      updatedAt: past,
    });
    await db.collection("users").doc(JOINER_UID).set({
      uid: JOINER_UID,
      displayName: "Vikram",
      email: "vikram@test.in",
      familyId: null,
      createdAt: past,
      updatedAt: past,
    });

    await db.collection("families").doc(FAMILY_ID).set({
      name: "Sharma Household",
      currencyCode: "INR",
      createdByUid: ADMIN_UID,
      adminCount: 1,
      createdAt: past,
      updatedAt: past,
    });
    await db.collection("families").doc(FAMILY_ID).collection("members").doc(ADMIN_UID).set({
      uid: ADMIN_UID,
      familyId: FAMILY_ID,
      displayName: "Ananya",
      email: "ananya@test.in",
      role: "admin",
      isActive: true,
      joinedAt: past,
      updatedAt: past,
    });
    await db.collection("families").doc(FAMILY_ID).collection("members").doc(MEMBER_UID).set({
      uid: MEMBER_UID,
      familyId: FAMILY_ID,
      displayName: "Rohan",
      email: "rohan@test.in",
      role: "member",
      isActive: true,
      joinedAt: past,
      updatedAt: past,
    });
    await db.collection("families").doc(FAMILY_ID).collection("members").doc(VIEWER_UID).set({
      uid: VIEWER_UID,
      familyId: FAMILY_ID,
      displayName: "Kamla",
      email: "kamla@test.in",
      role: "viewer",
      isActive: true,
      joinedAt: past,
      updatedAt: past,
    });

    await db.collection("families").doc(OTHER_FAMILY_ID).set({
      name: "Verma Household",
      currencyCode: "INR",
      createdByUid: OUTSIDER_UID,
      adminCount: 1,
      createdAt: past,
      updatedAt: past,
    });
    await db.collection("families").doc(OTHER_FAMILY_ID).collection("members").doc(OUTSIDER_UID).set({
      uid: OUTSIDER_UID,
      familyId: OTHER_FAMILY_ID,
      displayName: "Outsider",
      email: "outsider@test.in",
      role: "admin",
      isActive: true,
      joinedAt: past,
      updatedAt: past,
    });
  });
}

async function seedInvites() {
  const past = nowTs(-60_000);
  const future = nowTs(47 * 3600 * 1000);
  const expired = nowTs(-1_000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("invites").doc(VALID_CODE).set({
      code: VALID_CODE,
      familyId: FAMILY_ID,
      familyName: "Sharma Household",
      role: "member",
      status: "pending",
      createdByUid: ADMIN_UID,
      createdAt: past,
      expiresAt: future,
    });
    await db.collection("invites").doc(EXPIRED_CODE).set({
      code: EXPIRED_CODE,
      familyId: FAMILY_ID,
      familyName: "Sharma Household",
      role: "member",
      status: "pending",
      createdByUid: ADMIN_UID,
      createdAt: past,
      expiresAt: expired,
    });
    await db.collection("invites").doc(USED_CODE).set({
      code: USED_CODE,
      familyId: FAMILY_ID,
      familyName: "Sharma Household",
      role: "viewer",
      status: "used",
      createdByUid: ADMIN_UID,
      createdAt: past,
      expiresAt: future,
      usedByUid: MEMBER_UID,
      usedAt: past,
    });
    await db.collection("invites").doc(REVOKED_CODE).set({
      code: REVOKED_CODE,
      familyId: FAMILY_ID,
      familyName: "Sharma Household",
      role: "viewer",
      status: "revoked",
      createdByUid: ADMIN_UID,
      createdAt: past,
      expiresAt: future,
      revokedByUid: ADMIN_UID,
      revokedAt: past,
    });
  });
}

test("DENIED: signed-out reads across users, families, members, categories, and invites", async () => {
  await seedFamilyAndMembers();
  await seedInvites();
  const unauthDb = testEnv.unauthenticatedContext().firestore();

  await assertFails(unauthDb.collection("users").doc(ADMIN_UID).get());
  await assertFails(unauthDb.collection("families").doc(FAMILY_ID).get());
  await assertFails(unauthDb.collection("families").doc(FAMILY_ID).collection("members").get());
  await assertFails(unauthDb.collection("families").doc(FAMILY_ID).collection("categories").get());
  await assertFails(unauthDb.collection("invites").doc(VALID_CODE).get());
  await assertFails(unauthDb.collection("invites").where("familyId", "==", FAMILY_ID).get());
});

test("DENIED: cross-family reads by authenticated outsider", async () => {
  await seedFamilyAndMembers();
  await seedInvites();
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();

  await assertFails(outsiderDb.collection("users").doc(ADMIN_UID).get());
  await assertFails(outsiderDb.collection("families").doc(FAMILY_ID).get());
  await assertFails(outsiderDb.collection("families").doc(FAMILY_ID).collection("members").get());
  await assertFails(outsiderDb.collection("families").doc(FAMILY_ID).collection("categories").get());
  await assertFails(outsiderDb.collection("invites").where("familyId", "==", FAMILY_ID).get());
});

test("DENIED: non-admin invite creation (member and viewer)", async () => {
  await seedFamilyAndMembers();
  const now = nowTs(-5_000);
  const expires = nowTs(47 * 3600 * 1000);

  for (const [uid] of [[MEMBER_UID], [VIEWER_UID]]) {
    const db = testEnv.authenticatedContext(uid).firestore();
    await assertFails(
      db.collection("invites").doc("NEWC234567").set({
        code: "NEWC234567",
        familyId: FAMILY_ID,
        familyName: "Sharma Household",
        role: "member",
        status: "pending",
        createdByUid: uid,
        createdAt: now,
        expiresAt: expires,
      })
    );
  }
});

test("DENIED: admin cannot create an invite granting admin role", async () => {
  await seedFamilyAndMembers();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  await assertFails(
    adminDb.collection("invites").doc("ADMN234567").set({
      code: "ADMN234567",
      familyId: FAMILY_ID,
      familyName: "Sharma Household",
      role: "admin",
      status: "pending",
      createdByUid: ADMIN_UID,
      createdAt: nowTs(-5_000),
      expiresAt: nowTs(47 * 3600 * 1000),
    })
  );
});

test("DENIED: joining with expired, used, or revoked invite codes", async () => {
  await seedFamilyAndMembers();
  await seedInvites();
  const joinerDb = testEnv.authenticatedContext(JOINER_UID).firestore();
  const now = nowTs(-5_000);

  for (const [code, role] of [
    [EXPIRED_CODE, "member"],
    [USED_CODE, "viewer"],
    [REVOKED_CODE, "viewer"],
  ]) {
    const batch = joinerDb.batch();
    batch.set(
      joinerDb.collection("families").doc(FAMILY_ID).collection("members").doc(JOINER_UID),
      {
        uid: JOINER_UID,
        familyId: FAMILY_ID,
        displayName: "Vikram",
        email: "vikram@test.in",
        role,
        isActive: true,
        inviteCode: code,
        joinedAt: now,
        updatedAt: now,
      }
    );
    batch.update(joinerDb.collection("invites").doc(code), {
      status: "used",
      usedByUid: JOINER_UID,
      usedAt: now,
    });
    batch.update(joinerDb.collection("users").doc(JOINER_UID), {
      familyId: FAMILY_ID,
      updatedAt: now,
    });
    await assertFails(batch.commit());
  }
});

test("DENIED: joining as admin even with a valid member invite", async () => {
  await seedFamilyAndMembers();
  await seedInvites();
  const joinerDb = testEnv.authenticatedContext(JOINER_UID).firestore();
  const now = nowTs(-5_000);

  const batch = joinerDb.batch();
  batch.set(
    joinerDb.collection("families").doc(FAMILY_ID).collection("members").doc(JOINER_UID),
    {
      uid: JOINER_UID,
      familyId: FAMILY_ID,
      displayName: "Vikram",
      email: "vikram@test.in",
      role: "admin",
      isActive: true,
      inviteCode: VALID_CODE,
      joinedAt: now,
      updatedAt: now,
    }
  );
  batch.update(joinerDb.collection("invites").doc(VALID_CODE), {
    status: "used",
    usedByUid: JOINER_UID,
    usedAt: now,
  });
  batch.update(joinerDb.collection("users").doc(JOINER_UID), {
    familyId: FAMILY_ID,
    updatedAt: now,
  });
  await assertFails(batch.commit());
});

test("DENIED: joiner marking an invite used for someone else", async () => {
  await seedFamilyAndMembers();
  await seedInvites();
  const joinerDb = testEnv.authenticatedContext(JOINER_UID).firestore();
  const now = nowTs(-5_000);

  // Case 1: update invite alone with usedByUid = OUTSIDER_UID
  await assertFails(
    joinerDb.collection("invites").doc(VALID_CODE).update({
      status: "used",
      usedByUid: OUTSIDER_UID,
      usedAt: now,
    })
  );

  // Case 2: batch creating own member doc while claiming invite under OUTSIDER_UID
  const batch = joinerDb.batch();
  batch.set(
    joinerDb.collection("families").doc(FAMILY_ID).collection("members").doc(JOINER_UID),
    {
      uid: JOINER_UID,
      familyId: FAMILY_ID,
      displayName: "Vikram",
      email: "vikram@test.in",
      role: "member",
      isActive: true,
      inviteCode: VALID_CODE,
      joinedAt: now,
      updatedAt: now,
    }
  );
  batch.update(joinerDb.collection("invites").doc(VALID_CODE), {
    status: "used",
    usedByUid: OUTSIDER_UID,
    usedAt: now,
  });
  await assertFails(batch.commit());
});

test("DENIED: role changes by non-admins (member and viewer)", async () => {
  await seedFamilyAndMembers();
  const now = nowTs(-5_000);

  const memberDb = testEnv.authenticatedContext(MEMBER_UID).firestore();
  await assertFails(
    memberDb.collection("families").doc(FAMILY_ID).collection("members").doc(VIEWER_UID).update({
      role: "admin",
      updatedAt: now,
    })
  );

  const viewerDb = testEnv.authenticatedContext(VIEWER_UID).firestore();
  await assertFails(
    viewerDb.collection("families").doc(FAMILY_ID).collection("members").doc(VIEWER_UID).update({
      role: "member",
      updatedAt: now,
    })
  );
});

test("DENIED: admin cannot demote or remove the last admin", async () => {
  await seedFamilyAndMembers();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const now = nowTs(-5_000);

  const demoteBatch = adminDb.batch();
  demoteBatch.update(
    adminDb.collection("families").doc(FAMILY_ID).collection("members").doc(ADMIN_UID),
    { role: "member", updatedAt: now }
  );
  demoteBatch.update(adminDb.collection("families").doc(FAMILY_ID), {
    adminCount: 0,
    updatedAt: now,
  });
  await assertFails(demoteBatch.commit());

  const removeBatch = adminDb.batch();
  removeBatch.delete(
    adminDb.collection("families").doc(FAMILY_ID).collection("members").doc(ADMIN_UID)
  );
  removeBatch.update(adminDb.collection("families").doc(FAMILY_ID), {
    adminCount: 0,
    updatedAt: now,
  });
  await assertFails(removeBatch.commit());
});

test("ALLOWED: valid family creation batch, admin invite, and single-use join batch", async () => {
  const past = nowTs(-5_000);
  const founderDb = testEnv.authenticatedContext("uid_founder").firestore();
  const newFamId = "fam_new_123";

  await assertSucceeds(
    founderDb.collection("users").doc("uid_founder").set({
      uid: "uid_founder",
      displayName: "Founder",
      email: "founder@test.in",
      familyId: null,
      createdAt: past,
      updatedAt: past,
    })
  );

  const createFamilyBatch = founderDb.batch();
  createFamilyBatch.set(founderDb.collection("families").doc(newFamId), {
    name: "Patel Home",
    currencyCode: "INR",
    createdByUid: "uid_founder",
    adminCount: 1,
    createdAt: past,
    updatedAt: past,
  });
  createFamilyBatch.set(
    founderDb.collection("families").doc(newFamId).collection("members").doc("uid_founder"),
    {
      uid: "uid_founder",
      familyId: newFamId,
      displayName: "Founder",
      email: "founder@test.in",
      role: "admin",
      isActive: true,
      joinedAt: past,
      updatedAt: past,
    }
  );
  createFamilyBatch.set(
    founderDb.collection("families").doc(newFamId).collection("categories").doc("cat_groceries"),
    {
      name: "Groceries",
      familyId: newFamId,
      type: "expense",
      iconKey: "shopping_basket",
      colorHex: 0x1E9E6A,
      isDefault: true,
      sortOrder: 1,
      createdAt: past,
    }
  );
  createFamilyBatch.update(founderDb.collection("users").doc("uid_founder"), {
    familyId: newFamId,
    updatedAt: past,
  });
  await assertSucceeds(createFamilyBatch.commit());

  // Admin creates invite
  const inviteCode = "PATEL23456";
  await assertSucceeds(
    founderDb.collection("invites").doc(inviteCode).set({
      code: inviteCode,
      familyId: newFamId,
      familyName: "Patel Home",
      role: "member",
      status: "pending",
      createdByUid: "uid_founder",
      createdAt: past,
      expiresAt: nowTs(47 * 3600 * 1000),
    })
  );

  // Joiner redeems invite once
  const joinerDb = testEnv.authenticatedContext(JOINER_UID).firestore();
  await assertSucceeds(
    joinerDb.collection("users").doc(JOINER_UID).set({
      uid: JOINER_UID,
      displayName: "Vikram",
      email: "vikram@test.in",
      familyId: null,
      createdAt: past,
      updatedAt: past,
    })
  );

  const joinBatch = joinerDb.batch();
  joinBatch.set(
    joinerDb.collection("families").doc(newFamId).collection("members").doc(JOINER_UID),
    {
      uid: JOINER_UID,
      familyId: newFamId,
      displayName: "Vikram",
      email: "vikram@test.in",
      role: "member",
      isActive: true,
      inviteCode,
      joinedAt: past,
      updatedAt: past,
    }
  );
  joinBatch.update(joinerDb.collection("invites").doc(inviteCode), {
    status: "used",
    usedByUid: JOINER_UID,
    usedAt: past,
  });
  joinBatch.update(joinerDb.collection("users").doc(JOINER_UID), {
    familyId: newFamId,
    updatedAt: past,
  });
  await assertSucceeds(joinBatch.commit());

  // Cannot reuse the same invite code
  const secondJoinerDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();
  const reuseBatch = secondJoinerDb.batch();
  reuseBatch.set(
    secondJoinerDb.collection("families").doc(newFamId).collection("members").doc(OUTSIDER_UID),
    {
      uid: OUTSIDER_UID,
      familyId: newFamId,
      displayName: "Second",
      email: "second@test.in",
      role: "member",
      isActive: true,
      inviteCode,
      joinedAt: past,
      updatedAt: past,
    }
  );
  reuseBatch.update(secondJoinerDb.collection("invites").doc(inviteCode), {
    status: "used",
    usedByUid: OUTSIDER_UID,
    usedAt: past,
  });
  await assertFails(reuseBatch.commit());
});

async function seedCategoriesAndTransaction() {
  const past = nowTs(-60_000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("families").doc(FAMILY_ID).collection("categories").doc("cat_groceries").set({
      name: "Groceries",
      familyId: FAMILY_ID,
      type: "expense",
      iconKey: "shopping_basket",
      colorHex: 0x1E9E6A,
      isDefault: true,
      sortOrder: 1,
      createdAt: past,
    });
    await db.collection("families").doc(FAMILY_ID).collection("transactions").doc("tx_admin_1").set({
      familyId: FAMILY_ID,
      type: "expense",
      amountPaise: 125000,
      title: "BigBasket Weekly",
      note: "Paid via UPI",
      categoryId: "cat_groceries",
      occurredOn: "2026-11-12",
      paidByUid: MEMBER_UID,
      createdByUid: ADMIN_UID,
      status: "active",
      voidReason: null,
      voidedByUid: null,
      fundingSource: "household",
      paymentMethod: "upi",
      lastAuditEventId: "aud_tx_admin_1_init",
      createdAt: past,
      updatedAt: past,
    });
    await db.collection("families").doc(FAMILY_ID).collection("auditEvents").doc("aud_tx_admin_1_init").set({
      familyId: FAMILY_ID,
      transactionId: "tx_admin_1",
      action: "created",
      actorUid: ADMIN_UID,
      summary: "Created \"BigBasket Weekly\"",
      beforeState: {},
      afterState: { amount: "₹1,250", title: "BigBasket Weekly" },
      occurredAt: past,
    });
  });
}

test("DENIED: viewers cannot write transactions or auditEvents", async () => {
  await seedFamilyAndMembers();
  await seedCategoriesAndTransaction();
  const viewerDb = testEnv.authenticatedContext(VIEWER_UID).firestore();
  const now = nowTs(-5_000);

  const batch = viewerDb.batch();
  batch.set(viewerDb.collection("families").doc(FAMILY_ID).collection("transactions").doc("tx_v_1"), {
    familyId: FAMILY_ID,
    type: "expense",
    amountPaise: 5000,
    title: "Milk",
    note: "",
    categoryId: "cat_groceries",
    occurredOn: "2026-11-13",
    paidByUid: VIEWER_UID,
    createdByUid: VIEWER_UID,
    status: "active",
    voidReason: null,
    voidedByUid: null,
    fundingSource: "household",
    paymentMethod: "cash",
    lastAuditEventId: "aud_v_1",
    createdAt: now,
    updatedAt: now,
  });
  batch.set(viewerDb.collection("families").doc(FAMILY_ID).collection("auditEvents").doc("aud_v_1"), {
    familyId: FAMILY_ID,
    transactionId: "tx_v_1",
    action: "created",
    actorUid: VIEWER_UID,
    summary: "Created \"Milk\"",
    beforeState: {},
    afterState: { amount: "₹50" },
    occurredAt: now,
  });
  await assertFails(batch.commit());
});

test("DENIED: members cannot edit or void records created by someone else even if paidByUid is member", async () => {
  await seedFamilyAndMembers();
  await seedCategoriesAndTransaction();
  const memberDb = testEnv.authenticatedContext(MEMBER_UID).firestore();
  const now = nowTs(-5_000);

  const editBatch = memberDb.batch();
  editBatch.update(memberDb.collection("families").doc(FAMILY_ID).collection("transactions").doc("tx_admin_1"), {
    title: "Tampered Title",
    lastAuditEventId: "aud_m_edit_1",
    updatedAt: now,
  });
  editBatch.set(memberDb.collection("families").doc(FAMILY_ID).collection("auditEvents").doc("aud_m_edit_1"), {
    familyId: FAMILY_ID,
    transactionId: "tx_admin_1",
    action: "edited",
    actorUid: MEMBER_UID,
    summary: "Edited title",
    beforeState: { title: "BigBasket Weekly" },
    afterState: { title: "Tampered Title" },
    occurredAt: now,
  });
  await assertFails(editBatch.commit());

  const voidBatch = memberDb.batch();
  voidBatch.update(memberDb.collection("families").doc(FAMILY_ID).collection("transactions").doc("tx_admin_1"), {
    status: "voided",
    voidReason: "Not mine",
    voidedByUid: MEMBER_UID,
    lastAuditEventId: "aud_m_void_1",
    updatedAt: now,
  });
  voidBatch.set(memberDb.collection("families").doc(FAMILY_ID).collection("auditEvents").doc("aud_m_void_1"), {
    familyId: FAMILY_ID,
    transactionId: "tx_admin_1",
    action: "voided",
    actorUid: MEMBER_UID,
    summary: "Voided",
    beforeState: { status: "Active" },
    afterState: { status: "Voided" },
    occurredAt: now,
  });
  await assertFails(voidBatch.commit());
});

test("DENIED: no hard deletes on transactions and auditEvents are strictly append-only", async () => {
  await seedFamilyAndMembers();
  await seedCategoriesAndTransaction();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();

  await assertFails(
    adminDb.collection("families").doc(FAMILY_ID).collection("transactions").doc("tx_admin_1").delete()
  );
  await assertFails(
    adminDb.collection("families").doc(FAMILY_ID).collection("auditEvents").doc("aud_tx_admin_1_init").delete()
  );
  await assertFails(
    adminDb.collection("families").doc(FAMILY_ID).collection("auditEvents").doc("aud_tx_admin_1_init").update({
      summary: "Rewritten history",
    })
  );
});

test("DENIED: transaction write without paired auditEvent and cross-family reads/writes", async () => {
  await seedFamilyAndMembers();
  await seedCategoriesAndTransaction();
  const memberDb = testEnv.authenticatedContext(MEMBER_UID).firestore();
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();
  const now = nowTs(-5_000);

  // Transaction create without audit event fails
  await assertFails(
    memberDb.collection("families").doc(FAMILY_ID).collection("transactions").doc("tx_no_audit").set({
      familyId: FAMILY_ID,
      type: "expense",
      amountPaise: 5000,
      title: "No Audit",
      note: "",
      categoryId: "cat_groceries",
      occurredOn: "2026-11-13",
      paidByUid: MEMBER_UID,
      createdByUid: MEMBER_UID,
      status: "active",
      voidReason: null,
      voidedByUid: null,
      fundingSource: "household",
      paymentMethod: "upi",
      lastAuditEventId: "aud_missing",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Cross-family read and write denied
  await assertFails(
    outsiderDb.collection("families").doc(FAMILY_ID).collection("transactions").get()
  );
  await assertFails(
    outsiderDb.collection("families").doc(FAMILY_ID).collection("auditEvents").get()
  );
});

test("ALLOWED: member creates own record, edits it, and voids it atomically with auditEvents; admin can edit/void any", async () => {
  await seedFamilyAndMembers();
  await seedCategoriesAndTransaction();
  const memberDb = testEnv.authenticatedContext(MEMBER_UID).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const t1 = nowTs(-10_000);
  const t2 = nowTs(-5_000);

  // Member creates own transaction + auditEvent
  const createBatch = memberDb.batch();
  createBatch.set(memberDb.collection("families").doc(FAMILY_ID).collection("transactions").doc("tx_m_1"), {
    familyId: FAMILY_ID,
    type: "expense",
    amountPaise: 24000,
    title: "Nandini Milk",
    note: "Weekly milk",
    categoryId: "cat_groceries",
    occurredOn: "2026-11-13",
    paidByUid: MEMBER_UID,
    createdByUid: MEMBER_UID,
    status: "active",
    voidReason: null,
    voidedByUid: null,
    fundingSource: "household",
    paymentMethod: "upi",
    lastAuditEventId: "aud_m_create_1",
    createdAt: t1,
    updatedAt: t1,
  });
  createBatch.set(memberDb.collection("families").doc(FAMILY_ID).collection("auditEvents").doc("aud_m_create_1"), {
    familyId: FAMILY_ID,
    transactionId: "tx_m_1",
    action: "created",
    actorUid: MEMBER_UID,
    summary: "Created \"Nandini Milk\"",
    beforeState: {},
    afterState: { amount: "₹240", title: "Nandini Milk" },
    occurredAt: t1,
  });
  await assertSucceeds(createBatch.commit());

  // Member edits own transaction + auditEvent
  const editBatch = memberDb.batch();
  editBatch.update(memberDb.collection("families").doc(FAMILY_ID).collection("transactions").doc("tx_m_1"), {
    amountPaise: 26000,
    lastAuditEventId: "aud_m_edit_2",
    updatedAt: t2,
  });
  editBatch.set(memberDb.collection("families").doc(FAMILY_ID).collection("auditEvents").doc("aud_m_edit_2"), {
    familyId: FAMILY_ID,
    transactionId: "tx_m_1",
    action: "edited",
    actorUid: MEMBER_UID,
    summary: "Edited amount",
    beforeState: { amount: "₹240" },
    afterState: { amount: "₹260" },
    occurredAt: t2,
  });
  await assertSucceeds(editBatch.commit());

  // Admin voids member's transaction + auditEvent
  const voidBatch = adminDb.batch();
  voidBatch.update(adminDb.collection("families").doc(FAMILY_ID).collection("transactions").doc("tx_m_1"), {
    status: "voided",
    voidReason: "Duplicate milk entry",
    voidedByUid: ADMIN_UID,
    lastAuditEventId: "aud_a_void_3",
    updatedAt: nowTs(-1_000),
  });
  voidBatch.set(adminDb.collection("families").doc(FAMILY_ID).collection("auditEvents").doc("aud_a_void_3"), {
    familyId: FAMILY_ID,
    transactionId: "tx_m_1",
    action: "voided",
    actorUid: ADMIN_UID,
    summary: "Voided \"Nandini Milk\"",
    beforeState: { status: "Active" },
    afterState: { status: "Voided", voidReason: "Duplicate milk entry" },
    occurredAt: nowTs(-1_000),
  });
  await assertSucceeds(voidBatch.commit());
});

test("ALLOWED: viewer and admin can read family transactions and auditEvents", async () => {
  await seedFamilyAndMembers();
  await seedCategoriesAndTransaction();
  const viewerDb = testEnv.authenticatedContext(VIEWER_UID).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();

  await assertSucceeds(viewerDb.collection("families").doc(FAMILY_ID).get());
  await assertSucceeds(viewerDb.collection("families").doc(FAMILY_ID).collection("members").get());
  await assertSucceeds(viewerDb.collection("families").doc(FAMILY_ID).collection("categories").get());
  await assertSucceeds(viewerDb.collection("families").doc(FAMILY_ID).collection("transactions").get());
  await assertSucceeds(viewerDb.collection("families").doc(FAMILY_ID).collection("auditEvents").get());
  await assertSucceeds(adminDb.collection("families").doc(FAMILY_ID).collection("transactions").doc("tx_admin_1").get());
});

test("DENIED: listing top-level users or families collections is blocked for all roles", async () => {
  await seedFamilyAndMembers();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();

  await assertFails(adminDb.collection("users").get());
  await assertFails(adminDb.collection("families").get());
});

test("DENIED: users cross-write, delete, and self-assigning familyId without a member doc", async () => {
  await seedFamilyAndMembers();
  const joinerDb = testEnv.authenticatedContext(JOINER_UID).firestore();
  const now = nowTs(-5_000);

  await assertFails(
    joinerDb.collection("users").doc(MEMBER_UID).update({
      displayName: "Hijacked",
      updatedAt: now,
    })
  );
  await assertFails(joinerDb.collection("users").doc(JOINER_UID).delete());
  await assertFails(
    joinerDb.collection("users").doc(JOINER_UID).update({
      familyId: FAMILY_ID,
      updatedAt: now,
    })
  );
});

test("ALLOWED/DENIED: family rename, currency lock, and delete denial", async () => {
  await seedFamilyAndMembers();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const memberDb = testEnv.authenticatedContext(MEMBER_UID).firestore();
  const viewerDb = testEnv.authenticatedContext(VIEWER_UID).firestore();
  const now = nowTs(-5_000);

  await assertSucceeds(
    adminDb.collection("families").doc(FAMILY_ID).update({
      name: "Sharma Family Updated",
      updatedAt: now,
    })
  );
  await assertFails(
    adminDb.collection("families").doc(FAMILY_ID).update({
      currencyCode: "USD",
      updatedAt: now,
    })
  );
  await assertFails(
    memberDb.collection("families").doc(FAMILY_ID).update({
      name: "Member Rename",
      updatedAt: now,
    })
  );
  await assertFails(
    viewerDb.collection("families").doc(FAMILY_ID).update({
      name: "Viewer Rename",
      updatedAt: now,
    })
  );
  await assertFails(adminDb.collection("families").doc(FAMILY_ID).delete());
});

test("ALLOWED/DENIED: admin promotes second admin, removes viewer, and member/viewer cannot remove", async () => {
  await seedFamilyAndMembers();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const memberDb = testEnv.authenticatedContext(MEMBER_UID).firestore();
  const now = nowTs(-5_000);

  // Member cannot delete viewer
  await assertFails(
    memberDb.collection("families").doc(FAMILY_ID).collection("members").doc(VIEWER_UID).delete()
  );

  // Admin promotes member to admin (adminCount 1 -> 2)
  const promoteBatch = adminDb.batch();
  promoteBatch.update(adminDb.collection("families").doc(FAMILY_ID).collection("members").doc(MEMBER_UID), {
    role: "admin",
    updatedAt: now,
  });
  promoteBatch.update(adminDb.collection("families").doc(FAMILY_ID), {
    adminCount: 2,
    updatedAt: now,
  });
  await assertSucceeds(promoteBatch.commit());

  // With 2 admins, original admin can demote the second admin back to member (adminCount 2 -> 1)
  const demoteBatch = adminDb.batch();
  demoteBatch.update(adminDb.collection("families").doc(FAMILY_ID).collection("members").doc(MEMBER_UID), {
    role: "member",
    updatedAt: nowTs(-2_000),
  });
  demoteBatch.update(adminDb.collection("families").doc(FAMILY_ID), {
    adminCount: 1,
    updatedAt: nowTs(-2_000),
  });
  await assertSucceeds(demoteBatch.commit());

  // Admin removes viewer
  await assertSucceeds(
    adminDb.collection("families").doc(FAMILY_ID).collection("members").doc(VIEWER_UID).delete()
  );
});

test("ALLOWED/DENIED: categories CRUD restricted to admin, readable by all family roles", async () => {
  await seedFamilyAndMembers();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const memberDb = testEnv.authenticatedContext(MEMBER_UID).firestore();
  const viewerDb = testEnv.authenticatedContext(VIEWER_UID).firestore();
  const now = nowTs(-5_000);

  const catRef = adminDb.collection("families").doc(FAMILY_ID).collection("categories").doc("cat_pets");
  await assertSucceeds(
    catRef.set({
      name: "Pets",
      familyId: FAMILY_ID,
      type: "expense",
      iconKey: "pets",
      colorHex: 0x0EA5E9,
      isDefault: false,
      sortOrder: 99,
      createdAt: now,
    })
  );
  await assertSucceeds(catRef.update({ name: "Pet Care", sortOrder: 100 }));
  // Cannot mutate immutable category type
  await assertFails(catRef.update({ type: "income" }));

  // Member and viewer cannot create, update, or delete categories
  await assertFails(
    memberDb.collection("families").doc(FAMILY_ID).collection("categories").doc("cat_m").set({
      name: "Gaming",
      familyId: FAMILY_ID,
      type: "expense",
      iconKey: "sports_esports",
      colorHex: 0x1E9E6A,
      isDefault: false,
      sortOrder: 101,
      createdAt: now,
    })
  );
  await assertFails(
    viewerDb.collection("families").doc(FAMILY_ID).collection("categories").doc("cat_pets").delete()
  );
  await assertSucceeds(catRef.delete());
});

test("ALLOWED/DENIED: pending invites list and revoke allowed for admin only", async () => {
  await seedFamilyAndMembers();
  await seedInvites();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const memberDb = testEnv.authenticatedContext(MEMBER_UID).firestore();
  const now = nowTs(-5_000);

  await assertSucceeds(
    adminDb.collection("invites").where("familyId", "==", FAMILY_ID).where("status", "==", "pending").get()
  );
  await assertFails(
    memberDb.collection("invites").where("familyId", "==", FAMILY_ID).where("status", "==", "pending").get()
  );

  await assertFails(
    memberDb.collection("invites").doc(VALID_CODE).update({
      status: "revoked",
      revokedByUid: MEMBER_UID,
      revokedAt: now,
    })
  );
  await assertSucceeds(
    adminDb.collection("invites").doc(VALID_CODE).update({
      status: "revoked",
      revokedByUid: ADMIN_UID,
      revokedAt: now,
    })
  );
  await assertFails(adminDb.collection("invites").doc(VALID_CODE).delete());
});

test("DENIED: invalid transaction amountPaise or voiding without reason", async () => {
  await seedFamilyAndMembers();
  await seedCategoriesAndTransaction();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const now = nowTs(-5_000);

  const badAmountBatch = adminDb.batch();
  badAmountBatch.update(adminDb.collection("families").doc(FAMILY_ID).collection("transactions").doc("tx_admin_1"), {
    amountPaise: 0,
    lastAuditEventId: "aud_bad_amt",
    updatedAt: now,
  });
  badAmountBatch.set(adminDb.collection("families").doc(FAMILY_ID).collection("auditEvents").doc("aud_bad_amt"), {
    familyId: FAMILY_ID,
    transactionId: "tx_admin_1",
    action: "edited",
    actorUid: ADMIN_UID,
    summary: "Zero amount",
    beforeState: { amount: "₹1,250" },
    afterState: { amount: "₹0" },
    occurredAt: now,
  });
  await assertFails(badAmountBatch.commit());

  const badVoidBatch = adminDb.batch();
  badVoidBatch.update(adminDb.collection("families").doc(FAMILY_ID).collection("transactions").doc("tx_admin_1"), {
    status: "voided",
    voidReason: "",
    voidedByUid: ADMIN_UID,
    lastAuditEventId: "aud_bad_void",
    updatedAt: now,
  });
  badVoidBatch.set(adminDb.collection("families").doc(FAMILY_ID).collection("auditEvents").doc("aud_bad_void"), {
    familyId: FAMILY_ID,
    transactionId: "tx_admin_1",
    action: "voided",
    actorUid: ADMIN_UID,
    summary: "Voided without reason",
    beforeState: { status: "Active" },
    afterState: { status: "Voided" },
    occurredAt: now,
  });
  await assertFails(badVoidBatch.commit());
});

test("ALLOWED/DENIED: expenseClassification optional validation and immutability on void", async () => {
  await seedFamilyAndMembers();
  await seedCategoriesAndTransaction();
  const memberDb = testEnv.authenticatedContext(MEMBER_UID).firestore();
  const now = nowTs(-5_000);

  // Create expense classified as 'salary'
  const validBatch = memberDb.batch();
  validBatch.set(memberDb.collection("families").doc(FAMILY_ID).collection("transactions").doc("tx_salary_1"), {
    familyId: FAMILY_ID,
    type: "expense",
    amountPaise: 550000,
    title: "Cook & Maid Salary",
    note: "Monthly wages",
    categoryId: "cat_groceries",
    occurredOn: "2026-11-15",
    paidByUid: MEMBER_UID,
    createdByUid: MEMBER_UID,
    status: "active",
    voidReason: null,
    voidedByUid: null,
    fundingSource: "household",
    paymentMethod: "upi",
    expenseClassification: "salary",
    lastAuditEventId: "aud_sal_1",
    createdAt: now,
    updatedAt: now,
  });
  validBatch.set(memberDb.collection("families").doc(FAMILY_ID).collection("auditEvents").doc("aud_sal_1"), {
    familyId: FAMILY_ID,
    transactionId: "tx_salary_1",
    action: "created",
    actorUid: MEMBER_UID,
    summary: "Created \"Cook & Maid Salary\"",
    beforeState: {},
    afterState: { amount: "₹5,500", expenseClassification: "Salary" },
    occurredAt: now,
  });
  await assertSucceeds(validBatch.commit());

  // Invalid expenseClassification value denied
  const invalidBatch = memberDb.batch();
  invalidBatch.set(memberDb.collection("families").doc(FAMILY_ID).collection("transactions").doc("tx_bad_class"), {
    familyId: FAMILY_ID,
    type: "expense",
    amountPaise: 10000,
    title: "Bad Class",
    note: "",
    categoryId: "cat_groceries",
    occurredOn: "2026-11-15",
    paidByUid: MEMBER_UID,
    createdByUid: MEMBER_UID,
    status: "active",
    voidReason: null,
    voidedByUid: null,
    fundingSource: "household",
    paymentMethod: "upi",
    expenseClassification: "invalid_bucket",
    lastAuditEventId: "aud_bad_class",
    createdAt: now,
    updatedAt: now,
  });
  invalidBatch.set(memberDb.collection("families").doc(FAMILY_ID).collection("auditEvents").doc("aud_bad_class"), {
    familyId: FAMILY_ID,
    transactionId: "tx_bad_class",
    action: "created",
    actorUid: MEMBER_UID,
    summary: "Created",
    beforeState: {},
    afterState: { amount: "₹100" },
    occurredAt: now,
  });
  await assertFails(invalidBatch.commit());

  // Cannot mutate expenseClassification while voiding
  const mutateOnVoidBatch = memberDb.batch();
  mutateOnVoidBatch.update(memberDb.collection("families").doc(FAMILY_ID).collection("transactions").doc("tx_salary_1"), {
    status: "voided",
    expenseClassification: "non_salary",
    voidReason: "Testing void immutability",
    voidedByUid: MEMBER_UID,
    lastAuditEventId: "aud_sal_void_fail",
    updatedAt: nowTs(-1_000),
  });
  mutateOnVoidBatch.set(memberDb.collection("families").doc(FAMILY_ID).collection("auditEvents").doc("aud_sal_void_fail"), {
    familyId: FAMILY_ID,
    transactionId: "tx_salary_1",
    action: "voided",
    actorUid: MEMBER_UID,
    summary: "Voided",
    beforeState: { status: "Active" },
    afterState: { status: "Voided" },
    occurredAt: nowTs(-1_000),
  });
  await assertFails(mutateOnVoidBatch.commit());
});

test("ALLOWED/DENIED: only family admin can increment ledgerEpoch by 1 to clear data", async () => {
  await seedFamilyAndMembers();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const memberDb = testEnv.authenticatedContext(MEMBER_UID).firestore();
  const viewerDb = testEnv.authenticatedContext(VIEWER_UID).firestore();
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();
  const familyRefAdmin = adminDb.collection("families").doc(FAMILY_ID);
  const now = nowTs(-5_000);

  // Member, Viewer, and Outsider are denied
  await assertFails(
    memberDb.collection("families").doc(FAMILY_ID).update({
      ledgerEpoch: 1,
      updatedAt: now,
    })
  );
  await assertFails(
    viewerDb.collection("families").doc(FAMILY_ID).update({
      ledgerEpoch: 1,
      updatedAt: now,
    })
  );
  await assertFails(
    outsiderDb.collection("families").doc(FAMILY_ID).update({
      ledgerEpoch: 1,
      updatedAt: now,
    })
  );

  // Admin cannot jump by +2 or set negative epoch
  await assertFails(
    familyRefAdmin.update({
      ledgerEpoch: 2,
      updatedAt: now,
    })
  );
  await assertFails(
    familyRefAdmin.update({
      ledgerEpoch: -1,
      updatedAt: now,
    })
  );

  // Admin can increment ledgerEpoch cleanly by +1
  await assertSucceeds(
    familyRefAdmin.update({
      ledgerEpoch: 1,
      updatedAt: now,
    })
  );

  // Admin cannot decrement back to 0 once at 1
  await assertFails(
    familyRefAdmin.update({
      ledgerEpoch: 0,
      updatedAt: nowTs(-2_000),
    })
  );
});

