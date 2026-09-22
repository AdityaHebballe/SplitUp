package com.aditya.splitup.data.sync

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

    suspend fun deleteMember(groupId: String, memberFirestoreId: String) {
        db.collection("groups/$groupId/members").document(memberFirestoreId).delete().await()
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

    // ─── Listeners ────────────────────────────────────────────────────────────

    fun observeGroup(groupId: String, onUpdate: (Map<String, Any?>) -> Unit): ListenerRegistration {
        return db.collection("groups").document(groupId)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null || !snap.exists()) return@addSnapshotListener
                val data = snap.data?.plus("_fsId" to snap.id) ?: mapOf("_fsId" to snap.id)
                onUpdate(data)
            }
    }

    fun observeMembers(groupId: String, onUpdate: (List<Map<String, Any?>>) -> Unit): ListenerRegistration {
        return db.collection("groups/$groupId/members")
            .addSnapshotListener { snaps, err ->
                if (err != null || snaps == null) return@addSnapshotListener
                val list = snaps.documents.map { doc ->
                    doc.data?.plus("_fsId" to doc.id) ?: mapOf("_fsId" to doc.id)
                }
                onUpdate(list)
            }
    }

    fun observeExpenses(groupId: String, onUpdate: (List<Map<String, Any?>>) -> Unit): ListenerRegistration {
        return db.collection("groups/$groupId/expenses")
            .addSnapshotListener { snaps, err ->
                if (err != null || snaps == null) return@addSnapshotListener
                val list = snaps.documents.map { doc ->
                    doc.data?.plus("_fsId" to doc.id) ?: mapOf("_fsId" to doc.id)
                }
                onUpdate(list)
            }
    }

    fun observePayments(groupId: String, onUpdate: (List<Map<String, Any?>>) -> Unit): ListenerRegistration {
        return db.collection("groups/$groupId/payments")
            .addSnapshotListener { snaps, err ->
                if (err != null || snaps == null) return@addSnapshotListener
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
        db.collection("groups").document(firestoreGroupId)
            .update("memberUids", FieldValue.arrayRemove(uid)).await()

        val memberDocs = db.collection("groups/$firestoreGroupId/members")
            .whereEqualTo("linkedUid", uid).get().await()
        for (doc in memberDocs.documents) {
            doc.reference.update("linkedUid", null).await()
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
