package com.aditya.splitup.data.sync

import android.util.Log
import com.aditya.splitup.data.model.Expense
import com.aditya.splitup.data.model.ExpenseSplit
import com.aditya.splitup.data.model.Member
import com.aditya.splitup.data.model.Payment
import com.aditya.splitup.data.model.SplitGroup
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

/**
 * Low-level Firestore CRUD layer. Translates between Room entities and Firestore documents.
 * All write operations return the Firestore document ID.
 */
class FirestoreService {
    private val db = Firebase.firestore
    val auth = Firebase.auth

    val currentUid: String? get() = auth.currentUser?.uid

    // ─── Groups ───────────────────────────────────────────────────────────────

    suspend fun createGroup(group: SplitGroup): String {
        val uid = currentUid ?: error("Not authenticated")
        val doc = db.collection("groups").add(
            hashMapOf(
                "name" to group.name,
                "defaultCurrency" to group.defaultCurrency,
                "ownerUid" to uid,
                "memberUids" to listOf(uid),
                "createdAt" to group.createdAt
            )
        ).await()
        return doc.id
    }

    suspend fun updateGroup(group: SplitGroup) {
        val fsId = group.firestoreId ?: return
        db.collection("groups").document(fsId).update(
            mapOf(
                "name" to group.name,
                "defaultCurrency" to group.defaultCurrency
            )
        ).await()
    }

    suspend fun getGroup(firestoreGroupId: String): Map<String, Any?>? {
        val snap = db.collection("groups").document(firestoreGroupId).get().await()
        return if (snap.exists()) snap.data?.plus("_fsId" to snap.id) else null
    }

    suspend fun getMember(firestoreGroupId: String, memberFsId: String): Map<String, Any?>? {
        val snap = db.collection("groups/$firestoreGroupId/members").document(memberFsId).get().await()
        return if (snap.exists()) snap.data?.plus("_fsId" to snap.id) else null
    }

    suspend fun getGroupName(firestoreGroupId: String): String? {
        return db.collection("groups").document(firestoreGroupId).get().await()
            .getString("name")
    }

    suspend fun addUserToGroup(firestoreGroupId: String, uid: String) {
        db.collection("groups").document(firestoreGroupId)
            .update("memberUids", FieldValue.arrayUnion(uid))
            .await()
    }

    // ─── Members ──────────────────────────────────────────────────────────────

    suspend fun addMember(groupId: String, member: Member, linkedUid: String? = null): String {
        val doc = db.collection("groups/$groupId/members").add(
            hashMapOf(
                "name" to member.name,
                "defaultRatioPart" to member.defaultRatioPart,
                "linkedUid" to linkedUid
            )
        ).await()
        if (linkedUid != null) {
            addUserToGroup(groupId, linkedUid)
        }
        return doc.id
    }

    suspend fun updateMember(groupId: String, member: Member) {
        val fsId = member.firestoreId ?: return
        db.collection("groups/$groupId/members").document(fsId).update(
            mapOf(
                "name" to member.name,
                "defaultRatioPart" to member.defaultRatioPart,
                "linkedUid" to member.linkedUid
            )
        ).await()
    }

    suspend fun reclaimMember(groupId: String, memberFsId: String, uid: String) {
        db.collection("groups/$groupId/members").document(memberFsId).update(
            mapOf(
                "linkedUid" to uid,
                "previousUid" to null,
                "isRemoved" to false
            )
        ).await()
        addUserToGroup(groupId, uid)
    }

    suspend fun claimUnlinkedMember(groupId: String, memberFsId: String, uid: String, name: String) {
        db.collection("groups/$groupId/members").document(memberFsId).update(
            mapOf(
                "name" to name,
                "linkedUid" to uid,
                "previousUid" to null,
                "isRemoved" to false
            )
        ).await()
        addUserToGroup(groupId, uid)
    }

    /**
     * Atomically claims a member doc for [uid], re-reading its current `linkedUid` inside a
     * Firestore transaction rather than trusting a caller's earlier (non-transactional) read.
     * Returns false — instead of writing — if the doc was already claimed by a different uid
     * in the meantime, so the caller can fall back to creating a brand-new member instead of
     * silently overwriting someone else's claim (the race JoinGroupViewModel.joinGroup used to
     * be exposed to when two joins targeted the same candidate doc concurrently).
     */
    suspend fun claimMemberTransactional(
        groupId: String,
        memberFsId: String,
        uid: String,
        newName: String? = null
    ): Boolean {
        val docRef = db.collection("groups/$groupId/members").document(memberFsId)
        val claimed = db.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            val currentLinkedUid = snapshot.getString("linkedUid")
            if (currentLinkedUid != null && currentLinkedUid != uid) {
                return@runTransaction false
            }
            val updates = mutableMapOf<String, Any?>(
                "linkedUid" to uid,
                "previousUid" to null,
                "isRemoved" to false
            )
            if (newName != null) updates["name"] = newName
            transaction.update(docRef, updates)
            true
        }.await()
        if (claimed) {
            addUserToGroup(groupId, uid)
        }
        return claimed
    }

    suspend fun unlinkMember(groupId: String, memberFirestoreId: String, linkedUid: String? = null) {
        if (linkedUid != null) {
            try {
                db.collection("groups").document(groupId)
                    .update("memberUids", FieldValue.arrayRemove(linkedUid)).await()
            } catch (_: Exception) {}
        }
        db.collection("groups/$groupId/members").document(memberFirestoreId).update(
            mapOf(
                "linkedUid" to null,
                "previousUid" to linkedUid
            )
        ).await()
    }

    suspend fun removeMemberFromGroup(groupId: String, memberFirestoreId: String, linkedUid: String? = null) {
        if (linkedUid != null) {
            try {
                db.collection("groups").document(groupId)
                    .update("memberUids", FieldValue.arrayRemove(linkedUid)).await()
            } catch (_: Exception) {}
        }
        db.collection("groups/$groupId/members").document(memberFirestoreId).update(
            mapOf(
                "isRemoved" to true,
                "linkedUid" to null,
                "previousUid" to linkedUid
            )
        ).await()
    }

    suspend fun restoreMemberInGroup(groupId: String, memberFirestoreId: String) {
        db.collection("groups/$groupId/members").document(memberFirestoreId).update(
            mapOf("isRemoved" to false)
        ).await()
    }

    suspend fun deleteMember(groupId: String, memberFirestoreId: String, linkedUid: String? = null) {
        if (linkedUid != null) {
            try {
                db.collection("groups").document(groupId)
                    .update("memberUids", FieldValue.arrayRemove(linkedUid)).await()
            } catch (_: Exception) {}
        }
        db.collection("groups/$groupId/members").document(memberFirestoreId).delete().await()
    }

    suspend fun mergeDuplicateMemberInFirestore(groupId: String, keepFsId: String, duplicateFsId: String) {
        if (keepFsId == duplicateFsId) return
        try {
            val pendingUpdates = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, Map<String, Any>>>()

            // 1. Reassign expenses paid by duplicate
            val expensesSnapshot = db.collection("groups/$groupId/expenses").get().await()
            for (doc in expensesSnapshot.documents) {
                var needsUpdate = false
                val updates = mutableMapOf<String, Any>()
                if (doc.getString("paidByMemberFsId") == duplicateFsId) {
                    updates["paidByMemberFsId"] = keepFsId
                    needsUpdate = true
                }
                @Suppress("UNCHECKED_CAST")
                val splits = doc.get("splits") as? List<Map<String, Any>>
                if (splits != null && splits.any { it["memberFsId"] == duplicateFsId }) {
                    val newSplits = mutableListOf<Map<String, Any>>()
                    var keepIncluded = splits.any { it["memberFsId"] == keepFsId }
                    for (s in splits) {
                        val mId = s["memberFsId"] as? String
                        if (mId == duplicateFsId) {
                            if (!keepIncluded) {
                                newSplits.add(s + mapOf("memberFsId" to keepFsId))
                                keepIncluded = true
                            }
                        } else {
                            newSplits.add(s)
                        }
                    }
                    updates["splits"] = newSplits
                    needsUpdate = true
                }
                if (needsUpdate) {
                    pendingUpdates.add(doc.reference to updates)
                }
            }

            // 2. Reassign payments involving duplicate
            val paymentsSnapshot = db.collection("groups/$groupId/payments").get().await()
            for (doc in paymentsSnapshot.documents) {
                val updates = mutableMapOf<String, Any>()
                if (doc.getString("fromMemberFsId") == duplicateFsId) {
                    updates["fromMemberFsId"] = keepFsId
                }
                if (doc.getString("toMemberFsId") == duplicateFsId) {
                    updates["toMemberFsId"] = keepFsId
                }
                if (updates.isNotEmpty()) {
                    pendingUpdates.add(doc.reference to updates)
                }
            }

            // 3. Apply all reassignments plus the duplicate-member delete atomically, so a
            // crash or dropped connection never leaves a partially-reassigned state.
            val dupDocRef = db.collection("groups/$groupId/members").document(duplicateFsId)
            if (pendingUpdates.isEmpty()) {
                val batch = db.batch()
                batch.delete(dupDocRef)
                batch.commit().await()
            } else {
                val chunks = pendingUpdates.chunked(499)
                for (i in chunks.indices) {
                    val batch = db.batch()
                    chunks[i].forEach { (ref, updates) -> batch.update(ref, updates) }
                    if (i == chunks.lastIndex) {
                        batch.delete(dupDocRef)
                    }
                    batch.commit().await()
                }
            }
        } catch (e: Exception) {
            Log.e("FirestoreService", "Failed to merge duplicate member $duplicateFsId into $keepFsId in Firestore", e)
        }
    }

    // ─── Expenses ─────────────────────────────────────────────────────────────

    suspend fun addExpense(
        groupId: String,
        expense: Expense,
        splits: List<ExpenseSplit>,
        memberFsIdMap: Map<Long, String>  // localMemberId → firestoreId
    ): String {
        val uid = currentUid
        val splitPayload = splits.map { split ->
            hashMapOf(
                "memberFsId" to (memberFsIdMap[split.memberId] ?: ""),
                "ratioPart" to split.ratioPart
            )
        }
        val expRef = db.collection("groups/$groupId/expenses").add(
            hashMapOf(
                "paidByMemberFsId" to (memberFsIdMap[expense.paidByMemberId] ?: ""),
                "amount" to expense.amount,
                "currency" to expense.currency,
                "category" to expense.category,
                "description" to expense.description,
                "addedByUid" to uid,
                "createdAt" to expense.createdAt,
                "splits" to splitPayload
            )
        ).await()
        return expRef.id
    }

    suspend fun updateExpense(
        groupId: String,
        expense: Expense,
        splits: List<ExpenseSplit>,
        memberFsIdMap: Map<Long, String>
    ) {
        val fsId = expense.firestoreId ?: return
        val splitPayload = splits.map { split ->
            hashMapOf(
                "memberFsId" to (memberFsIdMap[split.memberId] ?: ""),
                "ratioPart" to split.ratioPart
            )
        }
        db.collection("groups/$groupId/expenses").document(fsId).update(
            mapOf(
                "paidByMemberFsId" to (memberFsIdMap[expense.paidByMemberId] ?: ""),
                "amount" to expense.amount,
                "currency" to expense.currency,
                "category" to expense.category,
                "description" to expense.description,
                "splits" to splitPayload
            )
        ).await()
    }

    suspend fun deleteExpense(groupId: String, expenseFirestoreId: String) {
        db.collection("groups/$groupId/expenses").document(expenseFirestoreId).delete().await()
    }

    // ─── Payments ─────────────────────────────────────────────────────────────

    suspend fun addPayment(
        groupId: String,
        payment: Payment,
        memberFsIdMap: Map<Long, String>
    ): String {
        val uid = currentUid
        val doc = db.collection("groups/$groupId/payments").add(
            hashMapOf(
                "fromMemberFsId" to (memberFsIdMap[payment.fromMemberId] ?: ""),
                "toMemberFsId" to (memberFsIdMap[payment.toMemberId] ?: ""),
                "amount" to payment.amount,
                "currency" to payment.currency,
                "addedByUid" to uid,
                "createdAt" to payment.createdAt
            )
        ).await()
        return doc.id
    }

    suspend fun updatePayment(
        groupId: String,
        payment: Payment,
        memberFsIdMap: Map<Long, String>
    ) {
        val fsId = payment.firestoreId ?: return
        db.collection("groups/$groupId/payments").document(fsId).update(
            mapOf(
                "fromMemberFsId" to (memberFsIdMap[payment.fromMemberId] ?: ""),
                "toMemberFsId" to (memberFsIdMap[payment.toMemberId] ?: ""),
                "amount" to payment.amount,
                "currency" to payment.currency
            )
        ).await()
    }

    suspend fun deletePayment(groupId: String, paymentFirestoreId: String) {
        db.collection("groups/$groupId/payments").document(paymentFirestoreId).delete().await()
    }

    // ─── Listeners ────────────────────────────────────────────────────────────

    fun observeGroup(groupId: String, onUpdate: (Map<String, Any?>) -> Unit): ListenerRegistration {
        return db.collection("groups").document(groupId)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    android.util.Log.e("FirestoreService", "observeGroup error for $groupId", err)
                    return@addSnapshotListener
                }
                if (snap == null || !snap.exists()) return@addSnapshotListener
                val data = snap.data?.plus("_fsId" to snap.id) ?: mapOf("_fsId" to snap.id)
                onUpdate(data)
            }
    }

    fun observeMembers(groupId: String, onUpdate: (List<Map<String, Any?>>) -> Unit): ListenerRegistration {
        return db.collection("groups/$groupId/members")
            .addSnapshotListener { snaps, err ->
                if (err != null) {
                    android.util.Log.e("FirestoreService", "observeMembers error for $groupId", err)
                    return@addSnapshotListener
                }
                if (snaps == null) return@addSnapshotListener
                val list = snaps.documents.map { doc ->
                    doc.data?.plus("_fsId" to doc.id) ?: mapOf("_fsId" to doc.id)
                }
                onUpdate(list)
            }
    }

    fun observeExpenses(groupId: String, onUpdate: (List<Map<String, Any?>>) -> Unit): ListenerRegistration {
        return db.collection("groups/$groupId/expenses")
            .addSnapshotListener { snaps, err ->
                if (err != null) {
                    android.util.Log.e("FirestoreService", "observeExpenses error for $groupId", err)
                    return@addSnapshotListener
                }
                if (snaps == null) return@addSnapshotListener
                val list = snaps.documents.map { doc ->
                    doc.data?.plus("_fsId" to doc.id) ?: mapOf("_fsId" to doc.id)
                }
                onUpdate(list)
            }
    }

    fun observePayments(groupId: String, onUpdate: (List<Map<String, Any?>>) -> Unit): ListenerRegistration {
        return db.collection("groups/$groupId/payments")
            .addSnapshotListener { snaps, err ->
                if (err != null) {
                    android.util.Log.e("FirestoreService", "observePayments error for $groupId", err)
                    return@addSnapshotListener
                }
                if (snaps == null) return@addSnapshotListener
                val list = snaps.documents.map { doc ->
                    doc.data?.plus("_fsId" to doc.id) ?: mapOf("_fsId" to doc.id)
                }
                onUpdate(list)
            }
    }

    // ─── Group Cleanup & Deletion ─────────────────────────────────────────────

    suspend fun deleteGroupCascading(firestoreGroupId: String) {
        val members = db.collection("groups/$firestoreGroupId/members").get().await()
        val expenses = db.collection("groups/$firestoreGroupId/expenses").get().await()
        val payments = db.collection("groups/$firestoreGroupId/payments").get().await()
        val invites = db.collection("invites").whereEqualTo("groupId", firestoreGroupId).get().await()

        val docsToDelete = mutableListOf<com.google.firebase.firestore.DocumentReference>()
        members.documents.forEach { docsToDelete.add(it.reference) }
        expenses.documents.forEach { docsToDelete.add(it.reference) }
        payments.documents.forEach { docsToDelete.add(it.reference) }
        invites.documents.forEach { docsToDelete.add(it.reference) }
        docsToDelete.add(db.collection("groups").document(firestoreGroupId))

        docsToDelete.chunked(450).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { ref -> batch.delete(ref) }
            batch.commit().await()
        }
    }

    suspend fun leaveGroup(firestoreGroupId: String, uid: String) {
        try {
            db.collection("groups").document(firestoreGroupId)
                .update("memberUids", FieldValue.arrayRemove(uid)).await()
        } catch (e: Exception) {
            android.util.Log.e("FirestoreService", "Failed to remove $uid from memberUids", e)
        }

        val memberDocs = db.collection("groups/$firestoreGroupId/members")
            .whereEqualTo("linkedUid", uid).get().await()
        for (doc in memberDocs.documents) {
            doc.reference.update(mapOf("linkedUid" to null, "previousUid" to uid, "isRemoved" to true)).await()
        }
    }

    suspend fun cleanupGroupInvites(firestoreGroupId: String) {
        val invites = db.collection("invites").whereEqualTo("groupId", firestoreGroupId).get().await()
        if (!invites.isEmpty) {
            val batch = db.batch()
            invites.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
    }

    // ─── Full group fetch (for join preview) ──────────────────────────────────

    suspend fun getGroupMembers(firestoreGroupId: String): List<Map<String, Any?>> {
        return db.collection("groups/$firestoreGroupId/members")
            .get().await().documents.map { doc ->
                doc.data?.plus("_fsId" to doc.id) ?: mapOf("_fsId" to doc.id)
            }
    }
}
