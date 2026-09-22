package com.aditya.splitup.data.sync

import android.util.Log
import com.aditya.splitup.data.AppDatabase
import com.aditya.splitup.data.model.Expense
import com.aditya.splitup.data.model.ExpenseSplit
import com.aditya.splitup.data.model.Member
import com.aditya.splitup.data.model.Payment
import com.aditya.splitup.data.model.SplitGroup
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Orchestrates Firestore ↔ Room sync.
 *
 * Write-through pattern:
 *  - For synced groups: writes go to Firestore; snapshot listeners reconcile back into Room.
 *  - For local-only groups: writes go directly to Room (unchanged behaviour).
 *
 * Reconcile pattern:
 *  - On each Firestore snapshot: diff remote docs vs local rows by firestoreId, then upsert/delete.
 */
class SyncRepository(
    val firestore: FirestoreService,
    private val db: AppDatabase
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeListeners = mutableMapOf<String, List<ListenerRegistration>>()

    // ─── Sync lifecycle ───────────────────────────────────────────────────────

    fun startSync(firestoreGroupId: String, localGroupId: Long) {
        if (activeListeners.containsKey(firestoreGroupId)) return   // already listening
        Log.d("SyncRepository", "Starting sync for group $firestoreGroupId")

        val listeners = listOf(
            firestore.observeMembers(firestoreGroupId) { remoteDocs ->
                scope.launch { reconcileMembers(localGroupId, firestoreGroupId, remoteDocs) }
            },
            firestore.observeExpenses(firestoreGroupId) { remoteDocs ->
                scope.launch { reconcileExpenses(localGroupId, firestoreGroupId, remoteDocs) }
            },
            firestore.observePayments(firestoreGroupId) { remoteDocs ->
                scope.launch { reconcilePayments(localGroupId, firestoreGroupId, remoteDocs) }
            }
        )
        activeListeners[firestoreGroupId] = listeners
    }

    fun stopSync(firestoreGroupId: String) {
        activeListeners[firestoreGroupId]?.forEach { it.remove() }
        activeListeners.remove(firestoreGroupId)
    }

    fun stopAllSync() {
        activeListeners.values.forEach { list -> list.forEach { it.remove() } }
        activeListeners.clear()
    }

    // ─── Write-through helpers ────────────────────────────────────────────────

    suspend fun addExpense(group: SplitGroup, expense: Expense, splits: List<ExpenseSplit>) {
        if (group.firestoreId != null) {
            val members = db.memberDao().getMembersByGroupOnce(group.id)
            val memberFsIdMap = members
                .filter { it.firestoreId != null }
                .associate { it.id to it.firestoreId!! }
            val fsExpId = firestore.addExpense(group.firestoreId!!, expense, splits, memberFsIdMap)
            // Room will be updated automatically by the snapshot listener.
            // But for immediate local consistency, also write locally with the fsId.
            val expenseId = db.expenseDao().insertExpense(
                expense.copy(firestoreId = fsExpId, addedByUid = firestore.currentUid)
            )
            db.expenseDao().insertSplits(splits.map { it.copy(expenseId = expenseId) })
        } else {
            db.expenseDao().insertExpenseWithSplits(expense, splits)
        }
    }

    suspend fun updateExpense(group: SplitGroup, expense: Expense, splits: List<ExpenseSplit>) {
        db.expenseDao().updateExpenseWithSplits(expense, splits)
        // For synced groups, the listener will pick up any Firestore-side changes.
        // Direct local edit is fine since we are the source of truth for our own changes.
    }

    suspend fun deleteExpense(group: SplitGroup, expense: Expense) {
        if (group.firestoreId != null && expense.firestoreId != null) {
            firestore.deleteExpense(group.firestoreId!!, expense.firestoreId!!)
        }
        db.expenseDao().deleteExpense(expense)
    }

    suspend fun recordPayment(group: SplitGroup, payment: Payment) {
        if (group.firestoreId != null) {
            val members = db.memberDao().getMembersByGroupOnce(group.id)
            val memberFsIdMap = members
                .filter { it.firestoreId != null }
                .associate { it.id to it.firestoreId!! }
            val fsPay = firestore.addPayment(group.firestoreId!!, payment, memberFsIdMap)
            db.paymentDao().insertPayment(
                payment.copy(firestoreId = fsPay, addedByUid = firestore.currentUid)
            )
        } else {
            db.paymentDao().insertPayment(payment)
        }
    }

    // ─── Reconciliation ───────────────────────────────────────────────────────

    private suspend fun reconcileMembers(
        localGroupId: Long,
        firestoreGroupId: String,
        remoteDocs: List<Map<String, Any?>>
    ) {
        val localMembers = db.memberDao().getMembersByGroupOnce(localGroupId)
        val localByFsId = localMembers.associateBy { it.firestoreId }
        val remoteFsIds = remoteDocs.map { it["_fsId"] as String }.toSet()

        for (doc in remoteDocs) {
            val fsId = doc["_fsId"] as String
            val name = doc["name"] as? String ?: continue
            val ratio = (doc["defaultRatioPart"] as? Long)?.toInt() ?: 1
            val linkedUid = doc["linkedUid"] as? String

            val existing = localByFsId[fsId]
            if (existing == null) {
                db.memberDao().insertMember(
                    Member(
                        groupId = localGroupId,
                        firestoreId = fsId,
                        name = name,
                        defaultRatioPart = ratio,
                        linkedUid = linkedUid
                    )
                )
            } else if (existing.name != name || existing.defaultRatioPart != ratio || existing.linkedUid != linkedUid) {
                db.memberDao().updateMember(
                    existing.copy(name = name, defaultRatioPart = ratio, linkedUid = linkedUid)
                )
            }
        }

        // Delete locals no longer in remote
        localMembers
            .filter { it.firestoreId != null && it.firestoreId !in remoteFsIds }
            .forEach { db.memberDao().deleteMember(it) }
    }

    private suspend fun reconcileExpenses(
        localGroupId: Long,
        firestoreGroupId: String,
        remoteDocs: List<Map<String, Any?>>
    ) {
        val localMembers = db.memberDao().getMembersByGroupOnce(localGroupId)
        val memberByFsId = localMembers.associateBy { it.firestoreId }
        val localExpenses = db.expenseDao().getExpensesByGroupOnce(localGroupId)
        val localByFsId = localExpenses.associateBy { it.firestoreId }
        val remoteFsIds = remoteDocs.map { it["_fsId"] as String }.toSet()

        for (doc in remoteDocs) {
            val fsId = doc["_fsId"] as String
            val paidByFsId = doc["paidByMemberFsId"] as? String ?: continue
            val paidByMember = memberByFsId[paidByFsId] ?: continue
            val amount = (doc["amount"] as? Double) ?: (doc["amount"] as? Long)?.toDouble() ?: continue
            val currency = doc["currency"] as? String ?: continue
            val category = doc["category"] as? String
            val description = doc["description"] as? String ?: ""
            val addedByUid = doc["addedByUid"] as? String
            val createdAt = (doc["createdAt"] as? Long) ?: System.currentTimeMillis()

            val existing = localByFsId[fsId]
            if (existing == null) {
                // Fetch splits from Firestore
                val splitDocs = try {
                    firestore.getExpenseSplits(firestoreGroupId, fsId)
                } catch (e: Exception) { emptyList() }

                val expenseId = db.expenseDao().insertExpense(
                    Expense(
                        groupId = localGroupId,
                        firestoreId = fsId,
                        paidByMemberId = paidByMember.id,
                        amount = amount,
                        currency = currency,
                        category = category,
                        description = description,
                        addedByUid = addedByUid,
                        createdAt = createdAt
                    )
                )
                val splits = splitDocs.mapNotNull { splitDoc ->
                    val mFsId = splitDoc["memberFsId"] as? String ?: return@mapNotNull null
                    val member = memberByFsId[mFsId] ?: return@mapNotNull null
                    ExpenseSplit(
                        firestoreId = splitDoc["_fsId"] as? String,
                        expenseId = expenseId,
                        memberId = member.id,
                        ratioPart = (splitDoc["ratioPart"] as? Long)?.toInt() ?: 1
                    )
                }
                if (splits.isNotEmpty()) db.expenseDao().insertSplits(splits)
            }
            // Note: We don't update existing expenses from remote (local wins for own changes)
        }

        // Delete locals no longer in remote
        localExpenses
            .filter { it.firestoreId != null && it.firestoreId !in remoteFsIds }
            .forEach { db.expenseDao().deleteExpense(it) }
    }

    private suspend fun reconcilePayments(
        localGroupId: Long,
        firestoreGroupId: String,
        remoteDocs: List<Map<String, Any?>>
    ) {
        val localMembers = db.memberDao().getMembersByGroupOnce(localGroupId)
        val memberByFsId = localMembers.associateBy { it.firestoreId }
        val localPayments = db.paymentDao().getPaymentsByGroupOnce(localGroupId)
        val localByFsId = localPayments.associateBy { it.firestoreId }
        val remoteFsIds = remoteDocs.map { it["_fsId"] as String }.toSet()

        for (doc in remoteDocs) {
            val fsId = doc["_fsId"] as String
            if (localByFsId.containsKey(fsId)) continue   // already have it

            val fromFsId = doc["fromMemberFsId"] as? String ?: continue
            val toFsId = doc["toMemberFsId"] as? String ?: continue
            val fromMember = memberByFsId[fromFsId] ?: continue
            val toMember = memberByFsId[toFsId] ?: continue
            val amount = (doc["amount"] as? Double) ?: (doc["amount"] as? Long)?.toDouble() ?: continue
            val currency = doc["currency"] as? String ?: continue
            val addedByUid = doc["addedByUid"] as? String
            val createdAt = (doc["createdAt"] as? Long) ?: System.currentTimeMillis()

            db.paymentDao().insertPayment(
                Payment(
                    groupId = localGroupId,
                    firestoreId = fsId,
                    fromMemberId = fromMember.id,
                    toMemberId = toMember.id,
                    amount = amount,
                    currency = currency,
                    addedByUid = addedByUid,
                    createdAt = createdAt
                )
            )
        }

        // Delete locals no longer in remote
        localPayments
            .filter { it.firestoreId != null && it.firestoreId !in remoteFsIds }
            .forEach { db.paymentDao().deletePayment(it) }
    }
}
