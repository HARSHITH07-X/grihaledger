package com.example.ui.i18n

import com.example.domain.analytics.PeriodPreset
import com.example.domain.model.AppLanguage
import com.example.domain.model.AuditAction
import com.example.domain.model.Category
import com.example.domain.model.ExpenseClassification
import com.example.domain.model.FundingSource
import com.example.domain.model.PaymentMethod
import com.example.domain.model.Role
import com.example.ui.screens.InsightsTab
import com.example.ui.screens.RecordsFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object LocalizedLabels {

  private val HI_IN_LOCALE: Locale = Locale.forLanguageTag("hi-IN")
  private val SHORT_DATE_EN: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
  private val SHORT_DATE_HI: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", HI_IN_LOCALE)
  private val FULL_DATE_EN: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
  private val FULL_DATE_HI: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", HI_IN_LOCALE)
  private val AUDIT_TS_EN: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH)
  private val AUDIT_TS_HI: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", HI_IN_LOCALE)

  fun role(role: Role, language: AppLanguage): String =
    if (language == AppLanguage.ENGLISH) {
      role.label
    } else {
      when (role) {
        Role.ADMIN -> "एडमिन"
        Role.MEMBER -> "सदस्य"
        Role.VIEWER -> "दर्शक"
      }
    }

  fun fundingSource(source: FundingSource, language: AppLanguage): String =
    if (language == AppLanguage.ENGLISH) {
      source.label
    } else {
      when (source) {
        FundingSource.HOUSEHOLD -> "घरेलू"
        FundingSource.PERSONAL -> "निजी"
        FundingSource.FAMILY_CARD -> "फैमिली कार्ड"
      }
    }

  fun paymentMethod(method: PaymentMethod, language: AppLanguage): String =
    if (language == AppLanguage.ENGLISH) {
      method.label
    } else {
      when (method) {
        PaymentMethod.UPI -> "यूपीआई (UPI)"
        PaymentMethod.CASH -> "नकद"
        PaymentMethod.CARD -> "कार्ड"
        PaymentMethod.BANK_TRANSFER -> "बैंक ट्रांसफर"
      }
    }

  fun expenseClassification(
    classification: ExpenseClassification,
    language: AppLanguage,
  ): String =
    if (language == AppLanguage.ENGLISH) {
      when (classification) {
        ExpenseClassification.SALARY -> "Salary expense"
        ExpenseClassification.NON_SALARY -> "Non-salary expense"
      }
    } else {
      when (classification) {
        ExpenseClassification.SALARY -> "वेतन खर्च"
        ExpenseClassification.NON_SALARY -> "गैर-वेतन खर्च"
      }
    }

  fun periodPresetShort(preset: PeriodPreset, language: AppLanguage): String =
    if (language == AppLanguage.ENGLISH) {
      preset.shortLabel
    } else {
      when (preset) {
        PeriodPreset.THIS_MONTH -> "इस माह"
        PeriodPreset.LAST_MONTH -> "पिछला माह"
        PeriodPreset.LAST_3_MONTHS -> "पिछले 3 माह"
        PeriodPreset.LAST_6_MONTHS -> "पिछले 6 माह"
        PeriodPreset.CALENDAR_YEAR -> "यह वर्ष"
        PeriodPreset.FINANCIAL_YEAR -> "वित्त वर्ष"
        PeriodPreset.CUSTOM -> "कस्टम"
      }
    }

  fun recordsFilter(filter: RecordsFilter, language: AppLanguage): String =
    if (language == AppLanguage.ENGLISH) {
      filter.label
    } else {
      when (filter) {
        RecordsFilter.ALL -> "सभी"
        RecordsFilter.EXPENSE -> "खर्च"
        RecordsFilter.SALARY_EXPENSE -> "वेतन खर्च"
        RecordsFilter.NON_SALARY_EXPENSE -> "गैर-वेतन"
        RecordsFilter.INCOME -> "आय"
        RecordsFilter.VOIDED -> "रद्द"
        RecordsFilter.MINE -> "मेरे"
      }
    }

  fun insightsTab(tab: InsightsTab, language: AppLanguage): String =
    if (language == AppLanguage.ENGLISH) {
      tab.label
    } else {
      when (tab) {
        InsightsTab.OVERVIEW -> "सारांश"
        InsightsTab.COMPARISON -> "तुलना"
        InsightsTab.CATEGORIES -> "श्रेणियाँ"
        InsightsTab.FORECAST -> "अनुमान"
      }
    }

  fun auditAction(action: AuditAction, language: AppLanguage): String =
    if (language == AppLanguage.ENGLISH) {
      action.name
    } else {
      when (action) {
        AuditAction.CREATED -> "बनाया गया"
        AuditAction.EDITED -> "संपादित"
        AuditAction.VOIDED -> "रद्द किया गया"
      }
    }

  fun categoryName(category: Category?, language: AppLanguage): String {
    val rawName = category?.name ?: return if (language == AppLanguage.HINDI) "श्रेणी" else "Category"
    return categoryNameById(category.id, rawName, language)
  }

  fun categoryNameById(categoryId: String?, fallbackName: String, language: AppLanguage): String {
    if (language == AppLanguage.ENGLISH) return fallbackName
    return when (categoryId) {
      "cat_groceries" -> "राशन"
      "cat_utilities" -> "बिल और उपयोगिता"
      "cat_home" -> "किराया व घर"
      "cat_dining" -> "बाहर खाना"
      "cat_transport" -> "यात्रा"
      "cat_health" -> "स्वास्थ्य"
      "cat_education" -> "शिक्षा"
      "cat_shopping" -> "खरीदारी"
      "cat_salary" -> "वेतन"
      "cat_other_income" -> "अन्य आय"
      else -> fallbackName
    }
  }

  fun auditFieldLabel(key: String, language: AppLanguage): String =
    if (language == AppLanguage.ENGLISH) {
      when (key) {
        "type" -> "Type"
        "amount" -> "Amount"
        "title" -> "Title"
        "category" -> "Category"
        "occurredOn" -> "Date"
        "paidBy" -> "Paid by"
        "fundingSource" -> "Paid from"
        "paymentMethod" -> "Method"
        "expenseClassification" -> "Expense type"
        "note" -> "Note"
        "status" -> "Status"
        "voidReason" -> "Void reason"
        else -> key.replaceFirstChar { it.uppercaseChar() }
      }
    } else {
      when (key) {
        "type" -> "प्रकार"
        "amount" -> "राशि"
        "title" -> "शीर्षक"
        "category" -> "श्रेणी"
        "occurredOn" -> "तारीख"
        "paidBy" -> "भुगतानकर्ता"
        "fundingSource" -> "भुगतान स्रोत"
        "paymentMethod" -> "माध्यम"
        "expenseClassification" -> "खर्च प्रकार"
        "note" -> "नोट"
        "status" -> "स्थिति"
        "voidReason" -> "रद्द करने का कारण"
        else -> key
      }
    }

  fun shortDate(date: LocalDate, language: AppLanguage): String =
    date.format(if (language == AppLanguage.HINDI) SHORT_DATE_HI else SHORT_DATE_EN)

  fun fullDate(date: LocalDate, language: AppLanguage): String =
    date.format(if (language == AppLanguage.HINDI) FULL_DATE_HI else FULL_DATE_EN)

  fun auditTimestamp(epochMillis: Long, language: AppLanguage): String {
    if (epochMillis <= 0L) {
      return if (language == AppLanguage.HINDI) "सर्वर समय की प्रतीक्षा में" else "Pending server time"
    }
    return Instant
      .ofEpochMilli(epochMillis)
      .atZone(ZoneId.systemDefault())
      .format(if (language == AppLanguage.HINDI) AUDIT_TS_HI else AUDIT_TS_EN)
  }
}
