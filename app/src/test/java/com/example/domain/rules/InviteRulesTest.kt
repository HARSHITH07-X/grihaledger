package com.example.domain.rules

import com.example.domain.model.Invite
import com.example.domain.model.InviteErrorReason
import com.example.domain.model.InviteStatus
import com.example.domain.model.InviteValidationResult
import com.example.domain.model.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InviteRulesTest {

  private val now = 1_760_000_000_000L

  private fun sampleInvite(
    code: String = "GRH2468KMP",
    role: Role = Role.MEMBER,
    status: InviteStatus = InviteStatus.PENDING,
    expiresAt: Long = now + InviteRules.EXPIRY_MILLIS,
  ) =
    Invite(
      code = code,
      familyId = "fam_test",
      familyName = "Sharma Household",
      role = role,
      status = status,
      createdByUid = "uid_admin",
      createdAtEpochMillis = now,
      expiresAtEpochMillis = expiresAt,
    )

  @Test
  fun `generateCode produces 10 chars strictly from unambiguous alphabet`() {
    val codes = (1..25).map { InviteRules.generateCode() }
    codes.forEach { code ->
      assertEquals(10, code.length)
      assertTrue(InviteRules.isValidCodeFormat(code))
      assertFalse(code.contains('0'))
      assertFalse(code.contains('O'))
      assertFalse(code.contains('1'))
      assertFalse(code.contains('I'))
    }
    assertNotEquals(codes[0], codes[1])
  }

  @Test
  fun `normalizeCode trims spaces hyphens and uppercases`() {
    assertEquals("GRH2468KMP", InviteRules.normalizeCode("  grh2-468k mp "))
  }

  @Test
  fun `isAssignableRole permits member and viewer only`() {
    assertTrue(InviteRules.isAssignableRole(Role.MEMBER))
    assertTrue(InviteRules.isAssignableRole(Role.VIEWER))
    assertFalse(InviteRules.isAssignableRole(Role.ADMIN))
  }

  @Test
  fun `validateForJoin rejects user already in family`() {
    val result =
      InviteRules.validateForJoin(
        invite = sampleInvite(),
        normalizedCode = "GRH2468KMP",
        currentUserFamilyId = "fam_other",
        nowEpochMillis = now,
      )
    assertTrue(result is InviteValidationResult.Invalid)
    assertEquals(
      InviteErrorReason.ALREADY_IN_FAMILY,
      (result as InviteValidationResult.Invalid).reason,
    )
  }

  @Test
  fun `validateForJoin rejects ambiguous or short code format`() {
    val result =
      InviteRules.validateForJoin(
        invite = null,
        normalizedCode = "GRH0O1I234",
        currentUserFamilyId = null,
        nowEpochMillis = now,
      )
    assertTrue(result is InviteValidationResult.Invalid)
    assertEquals(
      InviteErrorReason.INVALID_FORMAT,
      (result as InviteValidationResult.Invalid).reason,
    )
  }

  @Test
  fun `validateForJoin rejects missing, used, revoked, expired, and admin invites`() {
    val missing =
      InviteRules.validateForJoin(null, "GRH2468KMP", null, now) as InviteValidationResult.Invalid
    assertEquals(InviteErrorReason.NOT_FOUND, missing.reason)

    val used =
      InviteRules.validateForJoin(
        sampleInvite(status = InviteStatus.USED),
        "GRH2468KMP",
        null,
        now,
      ) as InviteValidationResult.Invalid
    assertEquals(InviteErrorReason.ALREADY_USED, used.reason)

    val revoked =
      InviteRules.validateForJoin(
        sampleInvite(status = InviteStatus.REVOKED),
        "GRH2468KMP",
        null,
        now,
      ) as InviteValidationResult.Invalid
    assertEquals(InviteErrorReason.REVOKED, revoked.reason)

    val expired =
      InviteRules.validateForJoin(
        sampleInvite(expiresAt = now - 1L),
        "GRH2468KMP",
        null,
        now,
      ) as InviteValidationResult.Invalid
    assertEquals(InviteErrorReason.EXPIRED, expired.reason)

    val adminInvite =
      InviteRules.validateForJoin(
        sampleInvite(role = Role.ADMIN),
        "GRH2468KMP",
        null,
        now,
      ) as InviteValidationResult.Invalid
    assertEquals(InviteErrorReason.NOT_FOUND, adminInvite.reason)
  }

  @Test
  fun `validateForJoin accepts valid pending invite`() {
    val invite = sampleInvite(role = Role.VIEWER)
    val result = InviteRules.validateForJoin(invite, "GRH2468KMP", null, now)
    assertTrue(result is InviteValidationResult.Valid)
    assertEquals(invite, (result as InviteValidationResult.Valid).invite)
  }
}
