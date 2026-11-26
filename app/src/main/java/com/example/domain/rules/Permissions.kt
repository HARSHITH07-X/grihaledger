package com.example.domain.rules

import com.example.domain.model.Member
import com.example.domain.model.Role
import com.example.domain.model.Transaction

enum class LedgerAction {
  VIEW_RECORDS,
  VIEW_INSIGHTS,
  USE_AI_ADVISOR,
  CREATE_TRANSACTION,
  EDIT_OWN_TRANSACTION,
  EDIT_ANY_TRANSACTION,
  VOID_OWN_TRANSACTION,
  VOID_ANY_TRANSACTION,
  MANAGE_CATEGORIES,
  CREATE_INVITE,
  REVOKE_INVITE,
  CHANGE_MEMBER_ROLE,
  REMOVE_MEMBER,
  EDIT_FAMILY_SETTINGS,
  CLEAR_ALL_DATA,
}

object Permissions {

  fun isAllowed(role: Role, action: LedgerAction): Boolean =
    when (role) {
      Role.ADMIN -> true
      Role.MEMBER ->
        when (action) {
          LedgerAction.VIEW_RECORDS,
          LedgerAction.VIEW_INSIGHTS,
          LedgerAction.USE_AI_ADVISOR,
          LedgerAction.CREATE_TRANSACTION,
          LedgerAction.EDIT_OWN_TRANSACTION,
          LedgerAction.VOID_OWN_TRANSACTION -> true
          LedgerAction.EDIT_ANY_TRANSACTION,
          LedgerAction.VOID_ANY_TRANSACTION,
          LedgerAction.MANAGE_CATEGORIES,
          LedgerAction.CREATE_INVITE,
          LedgerAction.REVOKE_INVITE,
          LedgerAction.CHANGE_MEMBER_ROLE,
          LedgerAction.REMOVE_MEMBER,
          LedgerAction.EDIT_FAMILY_SETTINGS,
          LedgerAction.CLEAR_ALL_DATA -> false
        }
      Role.VIEWER ->
        when (action) {
          LedgerAction.VIEW_RECORDS,
          LedgerAction.VIEW_INSIGHTS,
          LedgerAction.USE_AI_ADVISOR -> true
          LedgerAction.CREATE_TRANSACTION,
          LedgerAction.EDIT_OWN_TRANSACTION,
          LedgerAction.EDIT_ANY_TRANSACTION,
          LedgerAction.VOID_OWN_TRANSACTION,
          LedgerAction.VOID_ANY_TRANSACTION,
          LedgerAction.MANAGE_CATEGORIES,
          LedgerAction.CREATE_INVITE,
          LedgerAction.REVOKE_INVITE,
          LedgerAction.CHANGE_MEMBER_ROLE,
          LedgerAction.REMOVE_MEMBER,
          LedgerAction.EDIT_FAMILY_SETTINGS,
          LedgerAction.CLEAR_ALL_DATA -> false
        }
    }

  fun canClearAllData(actor: Member): Boolean =
    actor.isActive && isAllowed(actor.role, LedgerAction.CLEAR_ALL_DATA)

  fun canCreateTransaction(actor: Member): Boolean =
    actor.isActive && isAllowed(actor.role, LedgerAction.CREATE_TRANSACTION)

  fun canEditTransaction(actor: Member, transaction: Transaction): Boolean {
    if (!actor.isActive || actor.familyId != transaction.familyId || transaction.isVoided) {
      return false
    }
    if (isAllowed(actor.role, LedgerAction.EDIT_ANY_TRANSACTION)) {
      return true
    }
    return isAllowed(actor.role, LedgerAction.EDIT_OWN_TRANSACTION) &&
      transaction.createdByUid == actor.uid
  }

  fun canVoidTransaction(actor: Member, transaction: Transaction): Boolean {
    if (!actor.isActive || actor.familyId != transaction.familyId || transaction.isVoided) {
      return false
    }
    if (isAllowed(actor.role, LedgerAction.VOID_ANY_TRANSACTION)) {
      return true
    }
    return isAllowed(actor.role, LedgerAction.VOID_OWN_TRANSACTION) &&
      transaction.createdByUid == actor.uid
  }

  fun canChangeMemberRole(
    actor: Member,
    target: Member,
    newRole: Role,
    activeAdminCount: Int,
  ): Boolean {
    if (!actor.isActive || !target.isActive || actor.familyId != target.familyId) {
      return false
    }
    if (!isAllowed(actor.role, LedgerAction.CHANGE_MEMBER_ROLE)) {
      return false
    }
    if (target.role == newRole) {
      return false
    }
    if (target.role == Role.ADMIN && newRole != Role.ADMIN && activeAdminCount <= 1) {
      return false
    }
    return true
  }

  fun canRemoveMember(
    actor: Member,
    target: Member,
    activeAdminCount: Int,
  ): Boolean {
    if (!actor.isActive || !target.isActive || actor.familyId != target.familyId) {
      return false
    }
    if (!isAllowed(actor.role, LedgerAction.REMOVE_MEMBER)) {
      return false
    }
    if (target.role == Role.ADMIN && activeAdminCount <= 1) {
      return false
    }
    return true
  }
}
