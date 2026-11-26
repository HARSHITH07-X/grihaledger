package com.example.domain.model

enum class Role(val label: String, val summary: String) {
  ADMIN(
    label = "Admin",
    summary = "Manages family members, categories, invites, and all records",
  ),
  MEMBER(
    label = "Member",
    summary = "Adds transactions and edits or voids their own records",
  ),
  VIEWER(
    label = "Viewer",
    summary = "Read-only access to shared records, insights, and AI advisor",
  ),
}
