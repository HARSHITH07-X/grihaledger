package com.example.domain.rules

import com.example.domain.model.Member
import com.example.domain.model.Role
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionsTest {

  private val admin = Member("u_admin", "fam1", "Ananya", "a@test.in", Role.ADMIN, 1_000L)
  private val member = Member("u_member", "fam1", "Rohan", "r@test.in", Role.MEMBER, 1_000L)
  private val viewer = Member("u_viewer", "fam1", "Kamla", "k@test.in", Role.VIEWER, 1_000L)
  private val otherFamilyAdmin = Member("u_other", "fam2", "Other", "o@test.in", Role.ADMIN, 1_000L)
  private val inactiveAdmin = admin.copy(isActive = false)

  private fun tx(
    createdByUid: String,
    paidByUid: String = createdByUid,
    status: TransactionStatus = TransactionStatus.ACTIVE,
  ) =
    Transaction(
      id = "tx1",
      familyId = "fam1",
      type = TransactionType.EXPENSE,
      amountPaise = 50_000L,
      title = "Groceries",
      categoryId = "cat_groceries",
      occurredOn = LocalDate.of(2026, 11, 10),
      paidByUid = paidByUid,
      createdByUid = createdByUid,
      status = status,
      createdAtEpochMillis = 1_000L,
      updatedAtEpochMillis = 1_000L,
    )

  @Test
  fun `permission matrix covers every role and every action`() {
    val expected: Map<Pair<Role, LedgerAction>, Boolean> =
      mapOf(
        // ADMIN: full access
        (Role.ADMIN to LedgerAction.VIEW_RECORDS) to true,
        (Role.ADMIN to LedgerAction.VIEW_INSIGHTS) to true,
        (Role.ADMIN to LedgerAction.USE_AI_ADVISOR) to true,
        (Role.ADMIN to LedgerAction.CREATE_TRANSACTION) to true,
        (Role.ADMIN to LedgerAction.EDIT_OWN_TRANSACTION) to true,
        (Role.ADMIN to LedgerAction.EDIT_ANY_TRANSACTION) to true,
        (Role.ADMIN to LedgerAction.VOID_OWN_TRANSACTION) to true,
        (Role.ADMIN to LedgerAction.VOID_ANY_TRANSACTION) to true,
        (Role.ADMIN to LedgerAction.MANAGE_CATEGORIES) to true,
        (Role.ADMIN to LedgerAction.CREATE_INVITE) to true,
        (Role.ADMIN to LedgerAction.REVOKE_INVITE) to true,
        (Role.ADMIN to LedgerAction.CHANGE_MEMBER_ROLE) to true,
        (Role.ADMIN to LedgerAction.REMOVE_MEMBER) to true,
        (Role.ADMIN to LedgerAction.EDIT_FAMILY_SETTINGS) to true,
        (Role.ADMIN to LedgerAction.CLEAR_ALL_DATA) to true,
        // MEMBER: read + create + edit/void own only
        (Role.MEMBER to LedgerAction.VIEW_RECORDS) to true,
        (Role.MEMBER to LedgerAction.VIEW_INSIGHTS) to true,
        (Role.MEMBER to LedgerAction.USE_AI_ADVISOR) to true,
        (Role.MEMBER to LedgerAction.CREATE_TRANSACTION) to true,
        (Role.MEMBER to LedgerAction.EDIT_OWN_TRANSACTION) to true,
        (Role.MEMBER to LedgerAction.EDIT_ANY_TRANSACTION) to false,
        (Role.MEMBER to LedgerAction.VOID_OWN_TRANSACTION) to true,
        (Role.MEMBER to LedgerAction.VOID_ANY_TRANSACTION) to false,
        (Role.MEMBER to LedgerAction.MANAGE_CATEGORIES) to false,
        (Role.MEMBER to LedgerAction.CREATE_INVITE) to false,
        (Role.MEMBER to LedgerAction.REVOKE_INVITE) to false,
        (Role.MEMBER to LedgerAction.CHANGE_MEMBER_ROLE) to false,
        (Role.MEMBER to LedgerAction.REMOVE_MEMBER) to false,
        (Role.MEMBER to LedgerAction.EDIT_FAMILY_SETTINGS) to false,
        (Role.MEMBER to LedgerAction.CLEAR_ALL_DATA) to false,
        // VIEWER: read-only
        (Role.VIEWER to LedgerAction.VIEW_RECORDS) to true,
        (Role.VIEWER to LedgerAction.VIEW_INSIGHTS) to true,
        (Role.VIEWER to LedgerAction.USE_AI_ADVISOR) to true,
        (Role.VIEWER to LedgerAction.CREATE_TRANSACTION) to false,
        (Role.VIEWER to LedgerAction.EDIT_OWN_TRANSACTION) to false,
        (Role.VIEWER to LedgerAction.EDIT_ANY_TRANSACTION) to false,
        (Role.VIEWER to LedgerAction.VOID_OWN_TRANSACTION) to false,
        (Role.VIEWER to LedgerAction.VOID_ANY_TRANSACTION) to false,
        (Role.VIEWER to LedgerAction.MANAGE_CATEGORIES) to false,
        (Role.VIEWER to LedgerAction.CREATE_INVITE) to false,
        (Role.VIEWER to LedgerAction.REVOKE_INVITE) to false,
        (Role.VIEWER to LedgerAction.CHANGE_MEMBER_ROLE) to false,
        (Role.VIEWER to LedgerAction.REMOVE_MEMBER) to false,
        (Role.VIEWER to LedgerAction.EDIT_FAMILY_SETTINGS) to false,
        (Role.VIEWER to LedgerAction.CLEAR_ALL_DATA) to false,
      )

    assertEquals(Role.entries.size * LedgerAction.entries.size, expected.size)

    Role.entries.forEach { role ->
      LedgerAction.entries.forEach { action ->
        assertEquals(
          "Mismatch for $role x $action",
          expected.getValue(role to action),
          Permissions.isAllowed(role, action),
        )
      }
    }
  }

  @Test
  fun `edit and void checks respect ownership createdByUid vs paidByUid and void status`() {
    val memberCreatedTx = tx(createdByUid = member.uid, paidByUid = admin.uid)
    val adminCreatedButMemberPaidTx = tx(createdByUid = admin.uid, paidByUid = member.uid)
    val voidedTx = tx(createdByUid = member.uid, status = TransactionStatus.VOIDED)

    assertTrue(Permissions.canEditTransaction(member, memberCreatedTx))
    assertTrue(Permissions.canVoidTransaction(member, memberCreatedTx))

    // Even if member paid, they did not create it, so MEMBER cannot edit/void
    assertFalse(Permissions.canEditTransaction(member, adminCreatedButMemberPaidTx))
    assertFalse(Permissions.canVoidTransaction(member, adminCreatedButMemberPaidTx))

    // Admin can edit/void any active transaction in their family
    assertTrue(Permissions.canEditTransaction(admin, memberCreatedTx))
    assertTrue(Permissions.canVoidTransaction(admin, memberCreatedTx))

    // Nobody can edit or void an already-voided transaction
    assertFalse(Permissions.canEditTransaction(admin, voidedTx))
    assertFalse(Permissions.canVoidTransaction(admin, voidedTx))

    // Viewer, inactive actor, and cross-family admin are denied
    assertFalse(Permissions.canEditTransaction(viewer, memberCreatedTx))
    assertFalse(Permissions.canEditTransaction(inactiveAdmin, memberCreatedTx))
    assertFalse(Permissions.canEditTransaction(otherFamilyAdmin, memberCreatedTx))
  }

  @Test
  fun `last admin cannot be demoted or removed`() {
    // Only 1 active admin -> cannot demote or remove self
    assertFalse(Permissions.canChangeMemberRole(admin, admin, Role.MEMBER, activeAdminCount = 1))
    assertFalse(Permissions.canRemoveMember(admin, admin, activeAdminCount = 1))

    // With 2 active admins -> demoting or removing one is allowed
    assertTrue(Permissions.canChangeMemberRole(admin, admin, Role.MEMBER, activeAdminCount = 2))
    assertTrue(Permissions.canRemoveMember(admin, admin, activeAdminCount = 2))

    // Admin can change/remove non-admins, non-admins cannot
    assertTrue(Permissions.canChangeMemberRole(admin, member, Role.VIEWER, activeAdminCount = 1))
    assertTrue(Permissions.canRemoveMember(admin, viewer, activeAdminCount = 1))
    assertFalse(Permissions.canChangeMemberRole(member, viewer, Role.MEMBER, activeAdminCount = 1))
    assertFalse(Permissions.canRemoveMember(viewer, member, activeAdminCount = 1))
  }

  @Test
  fun `only active admin can clear all data`() {
    assertTrue(Permissions.canClearAllData(admin))
    assertTrue(Permissions.canClearAllData(otherFamilyAdmin))
    assertFalse(Permissions.canClearAllData(inactiveAdmin))
    assertFalse(Permissions.canClearAllData(member))
    assertFalse(Permissions.canClearAllData(viewer))
  }
}
