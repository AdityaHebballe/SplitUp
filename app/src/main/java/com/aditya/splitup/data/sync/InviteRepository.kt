package com.aditya.splitup.data.sync

import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

data class InviteInfo(
    val code: String,
    val groupFirestoreId: String,
    val groupName: String
)

/**
 * Creates and looks up invite codes stored in the top-level `invites` Firestore collection.
 * Codes are 6-char alphanumeric, valid for 24 hours.
 */
class InviteRepository {
    private val db = Firebase.firestore
    private val auth = Firebase.auth

    /** Creates an invite code for the given Firestore group and returns the 6-char code. */
    suspend fun createInvite(groupFirestoreId: String): String {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        val code = generateCode()
        db.collection("invites").document(code).set(
            hashMapOf(
                "groupId" to groupFirestoreId,
                "createdByUid" to uid,
                "createdAt" to System.currentTimeMillis(),
                "expiresAt" to System.currentTimeMillis() + 24L * 60 * 60 * 1000
            )
        ).await()
        return code
    }

    /**
     * Looks up an invite code. Returns [InviteInfo] on success, null if not found / expired.
     */
    suspend fun lookupInvite(code: String): InviteInfo? {
        val normalised = code.trim().uppercase()
        val doc = db.collection("invites").document(normalised).get().await()
        if (!doc.exists()) return null
        val expiresAt = doc.getLong("expiresAt") ?: return null
        if (System.currentTimeMillis() > expiresAt) return null
        val groupId = doc.getString("groupId") ?: return null

        // Fetch group name for preview
        val groupName = db.collection("groups").document(groupId).get().await()
            .getString("name") ?: "Unknown Group"

        return InviteInfo(code = normalised, groupFirestoreId = groupId, groupName = groupName)
    }

    private fun generateCode(): String {
        // Exclude ambiguous chars (0, O, 1, I, l)
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}
