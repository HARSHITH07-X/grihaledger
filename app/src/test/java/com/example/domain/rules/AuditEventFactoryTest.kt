package com.example.domain.rules

import com.example.data.fake.FakeSeedData
import com.example.domain.model.AuditAction
import com.example.domain.model.FundingSource
import com.example.domain.model.PaymentMethod
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuditEventFactoryTest {

  private val categoriesById = FakeSeedData.categories.associateBy { it.id }
  private val membersByUid = FakeSeedData.members.associateBy { it.uid }

  private val sampleTx =
    Transaction(
      id = "tx_sample_1",
      familyId = FakeSeedData.FAMILY_ID,
      type = TransactionType.EXPENSE,
      amountPaise = 45_000L,
      title = "BigBasket Milk",
      note = "Morning order",
      categoryId = "cat_groceries",
      occurredOn = LocalDate.of(2026, 5, 10),
      paidByUid = FakeSeedData.MEMBER_UID,
      createdByUid = FakeSeedData.ADMIN_UID,
      fundingSource = FundingSource.HOUSEHOLD,
      paymentMethod = PaymentMethod.UPI,
      createdAtEpochMillis = 1_000L,
      updatedAtEpochMillis = 1_000L,
    )

  @Test
  fun `buildCreatedEvent populates empty beforeState and full afterState`() {
    val event =
      AuditEventFactory.buildCreatedEvent(
        eventId = "aud_1",
        created = sampleTx,
        actorUid = FakeSeedData.ADMIN_UID,
        occurredAtEpochMillis = 1_000L,
        categoriesById = categoriesById,
        membersByUid = membersByUid,
      )

    assertEquals(AuditAction.CREATED, event.action)
    assertTrue(event.beforeState.isEmpty())
    assertEquals("₹450", event.afterState["amount"])
    assertEquals("BigBasket Milk", event.afterState["title"])
    assertEquals("Groceries", event.afterState["category"])
    assertEquals("Rohan Sharma", event.afterState["paidBy"])
    assertEquals("Active", event.afterState["status"])
  }

  @Test
  fun `buildEditedEvent captures only changed before and after keys`() {
    val updated =
      sampleTx.copy(
        amountPaise = 52_500L,
        title = "BigBasket Dairy & Fruits",
        paymentMethod = PaymentMethod.CARD,
        updatedAtEpochMillis = 2_000L,
      )

    val event =
      AuditEventFactory.buildEditedEvent(
        eventId = "aud_2",
        before = sampleTx,
        after = updated,
        actorUid = FakeSeedData.ADMIN_UID,
        occurredAtEpochMillis = 2_000L,
        categoriesById = categoriesById,
        membersByUid = membersByUid,
      )

    assertEquals(AuditAction.EDITED, event.action)
    assertEquals(setOf("amount", "title", "paymentMethod"), event.beforeState.keys)
    assertEquals(setOf("amount", "title", "paymentMethod"), event.afterState.keys)
    assertEquals("₹450", event.beforeState["amount"])
    assertEquals("₹525", event.afterState["amount"])
    assertEquals("UPI", event.beforeState["paymentMethod"])
    assertEquals("Card", event.afterState["paymentMethod"])
  }

  @Test
  fun `buildVoidedEvent records status transition and trimmed reason`() {
    val voided =
      sampleTx.copy(
        status = TransactionStatus.VOIDED,
        voidReason = "Duplicate entry",
        voidedByUid = FakeSeedData.ADMIN_UID,
        updatedAtEpochMillis = 3_000L,
      )

    val event =
      AuditEventFactory.buildVoidedEvent(
        eventId = "aud_3",
        before = sampleTx,
        afterVoided = voided,
        actorUid = FakeSeedData.ADMIN_UID,
        reason = "  Duplicate entry  ",
        occurredAtEpochMillis = 3_000L,
      )

    assertEquals(AuditAction.VOIDED, event.action)
    assertEquals("Active", event.beforeState["status"])
    assertEquals("Voided", event.afterState["status"])
    assertEquals("Duplicate entry", event.afterState["voidReason"])
  }
}
