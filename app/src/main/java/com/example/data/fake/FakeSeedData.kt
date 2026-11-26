package com.example.data.fake

import com.example.domain.model.AuditEvent
import com.example.domain.model.Category
import com.example.domain.model.ExpenseClassification
import com.example.domain.model.Family
import com.example.domain.model.Member
import com.example.domain.model.Role
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

object FakeSeedData {
  const val FAMILY_ID = "fam_sharma"
  const val ADMIN_UID = "uid_ananya"
  const val MEMBER_UID = "uid_rohan"
  const val VIEWER_UID = "uid_dadi"

  private val seedEpoch = 1_760_000_000_000L

  val family =
    Family(
      id = FAMILY_ID,
      name = "Sharma Household",
      currencyCode = "INR",
      createdAtEpochMillis = seedEpoch,
    )

  val members: List<Member> =
    listOf(
      Member(
        uid = ADMIN_UID,
        familyId = FAMILY_ID,
        displayName = "Ananya Sharma",
        email = "ananya@sharma.family",
        role = Role.ADMIN,
        joinedAtEpochMillis = seedEpoch,
      ),
      Member(
        uid = MEMBER_UID,
        familyId = FAMILY_ID,
        displayName = "Rohan Sharma",
        email = "rohan@sharma.family",
        role = Role.MEMBER,
        joinedAtEpochMillis = seedEpoch + 86_400_000L,
      ),
      Member(
        uid = VIEWER_UID,
        familyId = FAMILY_ID,
        displayName = "Kamla Devi",
        email = "kamla@sharma.family",
        role = Role.VIEWER,
        joinedAtEpochMillis = seedEpoch + 172_800_000L,
      ),
    )

  val categories: List<Category> =
    listOf(
      Category("cat_groceries", FAMILY_ID, "Groceries", TransactionType.EXPENSE, "shopping_basket", 0xFF1E9E6AL, sortOrder = 1),
      Category("cat_utilities", FAMILY_ID, "Utilities & Bills", TransactionType.EXPENSE, "bolt", 0xFFE0A21BL, sortOrder = 2),
      Category("cat_home", FAMILY_ID, "Home & Rent", TransactionType.EXPENSE, "home", 0xFF0F2E24L, sortOrder = 3),
      Category("cat_dining", FAMILY_ID, "Dining Out", TransactionType.EXPENSE, "restaurant", 0xFFE2574CL, sortOrder = 4),
      Category("cat_transport", FAMILY_ID, "Transport", TransactionType.EXPENSE, "directions_car", 0xFF2D6A4FL, sortOrder = 5),
      Category("cat_health", FAMILY_ID, "Healthcare", TransactionType.EXPENSE, "medical_services", 0xFF3B82F6L, sortOrder = 6),
      Category("cat_education", FAMILY_ID, "Education", TransactionType.EXPENSE, "school", 0xFF8B5CF6L, sortOrder = 7),
      Category("cat_shopping", FAMILY_ID, "Household & Kids", TransactionType.EXPENSE, "local_mall", 0xFFD97706L, sortOrder = 8),
      Category("cat_salary", FAMILY_ID, "Salary", TransactionType.INCOME, "account_balance_wallet", 0xFF1E9E6AL, sortOrder = 9),
      Category("cat_other_income", FAMILY_ID, "Other Income", TransactionType.INCOME, "payments", 0xFF0D9488L, sortOrder = 10),
    )

  fun buildTransactions(referenceDate: LocalDate = LocalDate.now()): List<Transaction> {
    val currentMonth = YearMonth.from(referenceDate)
    val prevMonth = currentMonth.minusMonths(1)

    fun currentMonthDay(day: Int): LocalDate =
      currentMonth.atDay(day.coerceAtMost(referenceDate.dayOfMonth).coerceAtLeast(1))

    fun prevMonthDay(day: Int): LocalDate =
      prevMonth.atDay(day.coerceAtMost(prevMonth.lengthOfMonth()).coerceAtLeast(1))

    data class Spec(
      val id: String,
      val type: TransactionType,
      val amountPaise: Long,
      val title: String,
      val note: String,
      val categoryId: String,
      val date: LocalDate,
      val paidBy: String,
      val createdBy: String,
      val status: TransactionStatus = TransactionStatus.ACTIVE,
      val voidReason: String? = null,
      val voidedBy: String? = null,
      val expenseClassification: ExpenseClassification = ExpenseClassification.NON_SALARY,
    )

    val specs =
      listOf(
        // Current month (15 transactions)
        Spec("tx_01", TransactionType.INCOME, 9_500_000L, "Ananya Salary", "Monthly tech lead salary", "cat_salary", currentMonthDay(1), ADMIN_UID, ADMIN_UID),
        Spec("tx_02", TransactionType.INCOME, 6_800_000L, "Rohan Salary", "Monthly design salary", "cat_salary", currentMonthDay(1), MEMBER_UID, MEMBER_UID),
        Spec("tx_03", TransactionType.EXPENSE, 2_800_000L, "Flat Rent", "November apartment rent", "cat_home", currentMonthDay(2), ADMIN_UID, ADMIN_UID),
        Spec("tx_04", TransactionType.EXPENSE, 245_050L, "BigBasket Weekly", "Fruits, dairy, staples", "cat_groceries", currentMonthDay(2), MEMBER_UID, ADMIN_UID),
        Spec("tx_05", TransactionType.EXPENSE, 189_000L, "BESCOM Electricity", "Monthly power bill", "cat_utilities", currentMonthDay(3), ADMIN_UID, ADMIN_UID),
        Spec("tx_06", TransactionType.EXPENSE, 99_900L, "ACT Fibernet", "Home broadband", "cat_utilities", currentMonthDay(4), MEMBER_UID, MEMBER_UID),
        Spec("tx_07", TransactionType.EXPENSE, 64_500L, "Apollo Pharmacy", "Kamla Devi's BP refill", "cat_health", currentMonthDay(5), MEMBER_UID, ADMIN_UID),
        Spec("tx_08", TransactionType.EXPENSE, 134_000L, "MTR Family Breakfast", "Sunday dosa outing", "cat_dining", currentMonthDay(6), ADMIN_UID, ADMIN_UID),
        Spec("tx_09", TransactionType.EXPENSE, 42_000L, "Metro Card Top-up", "Commute balance", "cat_transport", currentMonthDay(7), MEMBER_UID, MEMBER_UID),
        Spec("tx_10", TransactionType.EXPENSE, 312_075L, "Reliance Fresh", "Atta, oil, pulses, snacks", "cat_groceries", currentMonthDay(8), ADMIN_UID, ADMIN_UID),
        Spec("tx_11", TransactionType.EXPENSE, 450_000L, "School Activity Fee", "Term library & sports", "cat_education", currentMonthDay(9), ADMIN_UID, ADMIN_UID),
        Spec("tx_12", TransactionType.EXPENSE, 85_000L, "Indane Gas Cylinder", "Kitchen LPG refill", "cat_utilities", currentMonthDay(10), MEMBER_UID, MEMBER_UID),
        Spec("tx_13", TransactionType.EXPENSE, 145_000L, "Duplicate Swiggy Test", "Entered twice by mistake", "cat_dining", currentMonthDay(10), MEMBER_UID, MEMBER_UID, TransactionStatus.VOIDED, "Entered twice for same order", MEMBER_UID),
        Spec("tx_14", TransactionType.EXPENSE, 168_500L, "HPCL Fuel", "Scooter & car petrol", "cat_transport", currentMonthDay(11), MEMBER_UID, MEMBER_UID),
        Spec("tx_15", TransactionType.EXPENSE, 550_000L, "Cook & Maid Salary", "Monthly househelp wages", "cat_home", currentMonthDay(12), ADMIN_UID, ADMIN_UID, expenseClassification = ExpenseClassification.SALARY),

        // Previous month (15 transactions)
        Spec("tx_16", TransactionType.INCOME, 9_500_000L, "Ananya Salary", "October salary", "cat_salary", prevMonthDay(1), ADMIN_UID, ADMIN_UID),
        Spec("tx_17", TransactionType.INCOME, 6_800_000L, "Rohan Salary", "October salary", "cat_salary", prevMonthDay(1), MEMBER_UID, MEMBER_UID),
        Spec("tx_18", TransactionType.EXPENSE, 2_800_000L, "Flat Rent", "October apartment rent", "cat_home", prevMonthDay(2), ADMIN_UID, ADMIN_UID),
        Spec("tx_19", TransactionType.EXPENSE, 218_000L, "BigBasket Monthly", "Pantry restock", "cat_groceries", prevMonthDay(3), ADMIN_UID, ADMIN_UID),
        Spec("tx_20", TransactionType.EXPENSE, 174_500L, "BESCOM Electricity", "October power bill", "cat_utilities", prevMonthDay(4), ADMIN_UID, ADMIN_UID),
        Spec("tx_21", TransactionType.EXPENSE, 99_900L, "ACT Fibernet", "Home broadband", "cat_utilities", prevMonthDay(5), MEMBER_UID, MEMBER_UID),
        Spec("tx_22", TransactionType.EXPENSE, 125_000L, "Nandini Milk & Curd", "Morning dairy subscription", "cat_groceries", prevMonthDay(7), MEMBER_UID, ADMIN_UID),
        Spec("tx_23", TransactionType.EXPENSE, 550_000L, "Cook & Maid Salary", "October househelp wages", "cat_home", prevMonthDay(10), ADMIN_UID, ADMIN_UID, expenseClassification = ExpenseClassification.SALARY),
        Spec("tx_24", TransactionType.EXPENSE, 152_000L, "A2B Family Dinner", "Mid-month dinner", "cat_dining", prevMonthDay(12), MEMBER_UID, MEMBER_UID),
        Spec("tx_25", TransactionType.EXPENSE, 82_000L, "Dr. Rao Clinic", "Routine checkup", "cat_health", prevMonthDay(14), ADMIN_UID, ADMIN_UID),
        Spec("tx_26", TransactionType.INCOME, 450_000L, "Dividend Payout", "Mutual fund quarterly payout", "cat_other_income", prevMonthDay(18), ADMIN_UID, ADMIN_UID),
        Spec("tx_27", TransactionType.EXPENSE, 210_000L, "HPCL Fuel", "Highway trip fuel", "cat_transport", prevMonthDay(20), MEMBER_UID, MEMBER_UID),
        Spec("tx_28", TransactionType.EXPENSE, 265_000L, "DMart Bulk Run", "Detergents, rice, snacks", "cat_groceries", prevMonthDay(22), MEMBER_UID, MEMBER_UID),
        Spec("tx_29", TransactionType.EXPENSE, 320_000L, "Wrong Amount Fuel", "Typed extra zero", "cat_transport", prevMonthDay(24), MEMBER_UID, MEMBER_UID, TransactionStatus.VOIDED, "Wrong amount entered", ADMIN_UID),
        Spec("tx_30", TransactionType.EXPENSE, 148_000L, "Tuition Books", "Reference workbooks", "cat_education", prevMonthDay(27), ADMIN_UID, ADMIN_UID),
      )

    return specs.mapIndexed { index, spec ->
      val epoch = spec.date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() + (index * 60_000L)
      Transaction(
        id = spec.id,
        familyId = FAMILY_ID,
        type = spec.type,
        amountPaise = spec.amountPaise,
        title = spec.title,
        note = spec.note,
        categoryId = spec.categoryId,
        occurredOn = spec.date,
        paidByUid = spec.paidBy,
        createdByUid = spec.createdBy,
        status = spec.status,
        voidReason = spec.voidReason,
        voidedByUid = spec.voidedBy,
        createdAtEpochMillis = epoch,
        updatedAtEpochMillis = if (spec.status == TransactionStatus.VOIDED) epoch + 300_000L else epoch,
        expenseClassification = spec.expenseClassification,
      )
    }
  }

  fun buildAuditEvents(transactions: List<Transaction>): List<AuditEvent> {
    val categoriesById = categories.associateBy { it.id }
    val membersByUid = members.associateBy { it.uid }
    val events = mutableListOf<AuditEvent>()
    transactions.forEach { tx ->
      val createdSnapshot = tx.copy(
        status = TransactionStatus.ACTIVE,
        voidReason = null,
        voidedByUid = null,
      )
      events.add(
        com.example.domain.rules.AuditEventFactory.buildCreatedEvent(
          eventId = "aud_create_${tx.id}",
          created = createdSnapshot,
          actorUid = tx.createdByUid,
          occurredAtEpochMillis = tx.createdAtEpochMillis,
          categoriesById = categoriesById,
          membersByUid = membersByUid,
        )
      )
      if (tx.isVoided) {
        events.add(
          com.example.domain.rules.AuditEventFactory.buildVoidedEvent(
            eventId = "aud_void_${tx.id}",
            before = createdSnapshot,
            afterVoided = tx,
            actorUid = tx.voidedByUid ?: tx.createdByUid,
            reason = tx.voidReason ?: "No reason provided",
            occurredAtEpochMillis = tx.updatedAtEpochMillis,
          )
        )
      }
    }
    return events.sortedByDescending { it.occurredAtEpochMillis }
  }
}
