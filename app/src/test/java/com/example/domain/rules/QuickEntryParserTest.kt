package com.example.domain.rules

import com.example.data.fake.FakeSeedData
import com.example.domain.model.FundingSource
import com.example.domain.model.PaymentMethod
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickEntryParserTest {

  private val categories = FakeSeedData.categories
  private val refDate = LocalDate.of(2026, 3, 15)

  @Test
  fun `parses English natural text with amount, category keyword, method, and yesterday date`() {
    val parsed =
      QuickEntryParser.parse(
        rawText = "Milk and bread ₹180 UPI yesterday",
        categories = categories,
        referenceDate = refDate,
      )

    assertEquals(18_000L, parsed.amountPaise)
    assertEquals("180", parsed.editableRupees)
    assertEquals(PaymentMethod.UPI, parsed.paymentMethod)
    assertEquals(refDate.minusDays(1), parsed.occurredOn)
    assertEquals("Milk and bread", parsed.title)
    assertEquals("cat_groceries", parsed.suggestedCategoryId)
  }

  @Test
  fun `parses Devanagari digits and Hindi text with personal cash and date`() {
    val parsed =
      QuickEntryParser.parse(
        rawText = "दूध और सब्ज़ी १८० रुपये नकद निजी कल",
        categories = categories,
        referenceDate = refDate,
      )

    assertEquals(18_000L, parsed.amountPaise)
    assertEquals(PaymentMethod.CASH, parsed.paymentMethod)
    assertEquals(FundingSource.PERSONAL, parsed.fundingSource)
    assertEquals(refDate.minusDays(1), parsed.occurredOn)
    assertTrue(parsed.title.contains("दूध"))
  }

  @Test
  fun `parses Hindi and Hinglish spoken rupee words correctly`() {
    val dedhSau =
      QuickEntryParser.parse(
        rawText = "ऑटो डेढ़ सौ कैश",
        categories = categories,
        referenceDate = refDate,
      )
    assertEquals(15_000L, dedhSau.amountPaise)
    assertEquals(PaymentMethod.CASH, dedhSau.paymentMethod)
    assertEquals("cat_transport", dedhSau.suggestedCategoryId)

    val paanchSau =
      QuickEntryParser.parse(
        rawText = "Paanch sau auto cash",
        categories = categories,
        referenceDate = refDate,
      )
    assertEquals(50_000L, paanchSau.amountPaise)
    assertEquals(PaymentMethod.CASH, paanchSau.paymentMethod)

    val dhaiSau =
      QuickEntryParser.parse(
        rawText = "सब्जी ढाई सौ यूपीआई",
        categories = categories,
        referenceDate = refDate,
      )
    assertEquals(25_000L, dhaiSau.amountPaise)
    assertEquals(PaymentMethod.UPI, dhaiSau.paymentMethod)
  }

  @Test
  fun `detects salary income in English and Hindi`() {
    val en =
      QuickEntryParser.parse(
        rawText = "March salary credited 85000 bank",
        categories = categories,
        referenceDate = refDate,
      )
    assertEquals(TransactionType.INCOME, en.type)
    assertEquals(8_500_000L, en.amountPaise)
    assertEquals(PaymentMethod.BANK_TRANSFER, en.paymentMethod)
    assertEquals("cat_salary", en.suggestedCategoryId)

    val hi =
      QuickEntryParser.parse(
        rawText = "वेतन मिला 85000 बैंक",
        categories = categories,
        referenceDate = refDate,
      )
    assertEquals(TransactionType.INCOME, hi.type)
    assertEquals(8_500_000L, hi.amountPaise)
    assertEquals(PaymentMethod.BANK_TRANSFER, hi.paymentMethod)
  }

  @Test
  fun `returns null amount when no number or number word exists`() {
    val parsed =
      QuickEntryParser.parse(
        rawText = "Morning tea stall",
        categories = categories,
        referenceDate = refDate,
      )
    assertNull(parsed.amountPaise)
    assertNull(parsed.editableRupees)
    assertEquals("Morning tea stall", parsed.title)
  }

  @Test
  fun `buildQuickTemplates ranks frequent non-voided transactions first`() {
    val base = FakeSeedData.buildTransactions()
    val extra =
      listOf(
        Transaction(
          id = "t_repeat_1",
          familyId = FakeSeedData.FAMILY_ID,
          type = TransactionType.EXPENSE,
          amountPaise = 9_500L,
          title = "Morning Milk",
          note = "",
          categoryId = "cat_groceries",
          occurredOn = refDate,
          paidByUid = FakeSeedData.ADMIN_UID,
          createdByUid = FakeSeedData.ADMIN_UID,
          fundingSource = FundingSource.HOUSEHOLD,
          paymentMethod = PaymentMethod.UPI,
          createdAtEpochMillis = 2_000L,
          updatedAtEpochMillis = 2_000L,
        ),
        Transaction(
          id = "t_repeat_2",
          familyId = FakeSeedData.FAMILY_ID,
          type = TransactionType.EXPENSE,
          amountPaise = 10_000L,
          title = "Morning Milk",
          note = "",
          categoryId = "cat_groceries",
          occurredOn = refDate,
          paidByUid = FakeSeedData.ADMIN_UID,
          createdByUid = FakeSeedData.ADMIN_UID,
          fundingSource = FundingSource.HOUSEHOLD,
          paymentMethod = PaymentMethod.UPI,
          createdAtEpochMillis = 3_000L,
          updatedAtEpochMillis = 3_000L,
        ),
        Transaction(
          id = "t_repeat_3",
          familyId = FakeSeedData.FAMILY_ID,
          type = TransactionType.EXPENSE,
          amountPaise = 10_000L,
          title = "Morning Milk",
          note = "",
          categoryId = "cat_groceries",
          occurredOn = refDate,
          paidByUid = FakeSeedData.ADMIN_UID,
          createdByUid = FakeSeedData.ADMIN_UID,
          fundingSource = FundingSource.HOUSEHOLD,
          paymentMethod = PaymentMethod.UPI,
          createdAtEpochMillis = 3_500L,
          updatedAtEpochMillis = 3_500L,
        ),
        Transaction(
          id = "t_voided_repeat",
          familyId = FakeSeedData.FAMILY_ID,
          type = TransactionType.EXPENSE,
          amountPaise = 10_000L,
          title = "Voided Item",
          note = "",
          categoryId = "cat_groceries",
          occurredOn = refDate,
          paidByUid = FakeSeedData.ADMIN_UID,
          createdByUid = FakeSeedData.ADMIN_UID,
          status = TransactionStatus.VOIDED,
          fundingSource = FundingSource.HOUSEHOLD,
          paymentMethod = PaymentMethod.UPI,
          createdAtEpochMillis = 4_000L,
          updatedAtEpochMillis = 4_000L,
        ),
      )

    val templates = QuickEntryParser.buildQuickTemplates(base + extra, limit = 6)
    assertEquals("Morning Milk", templates.first().title)
    assertEquals(10_000L, templates.first().amountPaise)
    assertEquals(3, templates.first().frequencyCount)
    assertTrue(templates.none { it.title == "Voided Item" })
  }
}
