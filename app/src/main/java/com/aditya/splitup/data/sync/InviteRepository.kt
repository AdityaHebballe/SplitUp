package com.aditya.splitup.data.sync

import com.google.firebase.Timestamp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import java.util.Date

data class InviteInfo(
    val code: String,
    val groupFirestoreId: String,
    val groupName: String
)

/**
 * Creates and looks up invite codes stored in the top-level `invites` Firestore collection.
 * Codes are 6-char alphanumeric, valid for 24 hours.
 * Compatible with Firestore native TTL on `expiresAt` (Timestamp).
 */
class InviteRepository {
    private val db = Firebase.firestore
    private val auth = Firebase.auth

    /** Creates an invite code for the given Firestore group and returns the 6-char code. */
    suspend fun createInvite(groupFirestoreId: String, groupName: String = ""): String {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")

        // Clean up any existing invites for this group to keep collection lean
        try {
            val existing = db.collection("invites").whereEqualTo("groupId", groupFirestoreId).get().await()
            if (!existing.isEmpty) {
                val batch = db.batch()
                existing.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
        } catch (_: Exception) {}

        val code = generateCode()
        val now = Date()
        val expires = Date(now.time + 24L * 60 * 60 * 1000)

        db.collection("invites").document(code).set(
            hashMapOf(
                "groupId" to groupFirestoreId,
                "groupName" to groupName,
                "createdByUid" to uid,
                "createdAt" to Timestamp(now),
                "expiresAt" to Timestamp(expires)
            )
        ).await()
        return code
    }

    /**
     * Looks up an invite code. Returns [InviteInfo] on success, null if not found / expired.
     * Opportunistically deletes expired documents on access.
     */
    suspend fun lookupInvite(code: String): InviteInfo? {
        val normalised = code.trim().uppercase()
        val doc = db.collection("invites").document(normalised).get().await()
        if (!doc.exists()) return null

        val expiresTimestamp = doc.getTimestamp("expiresAt")
        val isExpired = if (expiresTimestamp != null) {
            Timestamp.now().compareTo(expiresTimestamp) > 0
        } else {
            val expiresLong = doc.getLong("expiresAt") ?: 0L
            System.currentTimeMillis() > expiresLong
        }

        if (isExpired) {
            // Delete expired doc immediately to keep Firestore clean
            try {
                doc.reference.delete()
            } catch (_: Exception) {}
            return null
        }

        val groupId = doc.getString("groupId") ?: return null

        // Fetch group name from invite doc, fallback to groups collection
        var groupName = doc.getString("groupName")
        if (groupName.isNullOrBlank()) {
            groupName = try {
                db.collection("groups").document(groupId).get().await().getString("name") ?: "Unknown Group"
            } catch (_: Exception) {
                "Unknown Group"
            }
        }

        return InviteInfo(code = normalised, groupFirestoreId = groupId, groupName = groupName)
    }

    private fun generateCode(): String {
        // Exclude ambiguous chars (0, O, 1, I, l)
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}
