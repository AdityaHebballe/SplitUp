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
        // 1. Insert into local Room DB immediately for instant UI responsiveness
        val localExpenseId = db.expenseDao().insertExpense(expense)
        db.expenseDao().insertSplits(splits.map { it.copy(expenseId = localExpenseId) })

        // 2. If group is synced to Firestore, upload in background and link firestoreId
        if (group.firestoreId != null) {
            try {
                val members = db.memberDao().getMembersByGroupOnce(group.id)
                val memberFsIdMap = members
                    .filter { it.firestoreId != null }
                    .associate { it.id to it.firestoreId!! }
                    .toMutableMap()

                // If any member lacks a firestoreId, sync them first to prevent empty IDs
                for (m in members) {
                    if (m.firestoreId == null) {
                        try {
                            val fsId = firestore.addMember(group.firestoreId!!, m)
                            db.memberDao().updateMember(m.copy(firestoreId = fsId))
                            memberFsIdMap[m.id] = fsId
                        } catch (e: Exception) {
                            Log.e("SyncRepository", "Failed to sync member ${m.name}", e)
                        }
                    }
                }

                val fsExpId = firestore.addExpense(group.firestoreId!!, expense, splits, memberFsIdMap)
                // Update local row with the new firestoreId (do NOT insert a second row!)
                val current = db.expenseDao().getExpenseById(localExpenseId)
                if (current != null && current.firestoreId == null) {
                    db.expenseDao().updateExpense(current.copy(firestoreId = fsExpId, addedByUid = firestore.currentUid))
                }
            } catch (e: Exception) {
                Log.e("SyncRepository", "Failed to upload expense to Firestore", e)
            }
        }
    }

    suspend fun updateExpense(group: SplitGroup, expense: Expense, splits: List<ExpenseSplit>) {
        db.expenseDao().updateExpenseWithSplits(expense, splits)
        // For synced groups, the listener will pick up any Firestore-side changes.
        // Direct local edit is fine since we are the source of truth for our own changes.
    }

    suspend fun deleteExpense(group: SplitGroup, expense: Expense) {
        if (group.firestoreId != null && expense.firestoreId != null) {
            try {
                firestore.deleteExpense(group.firestoreId!!, expense.firestoreId!!)
            } catch (e: Exception) {
                Log.e("SyncRepository", "Failed to delete expense from Firestore", e)
            }
        }
        db.expenseDao().deleteExpense(expense)
    }

    suspend fun recordPayment(group: SplitGroup, payment: Payment) {
        // 1. Insert into local Room DB immediately
        val localPaymentId = db.paymentDao().insertPayment(payment)

        if (group.firestoreId != null) {
            try {
                val members = db.memberDao().getMembersByGroupOnce(group.id)
                val memberFsIdMap = members
                    .filter { it.firestoreId != null }
                    .associate { it.id to it.firestoreId!! }
                val fsPay = firestore.addPayment(group.firestoreId!!, payment, memberFsIdMap)
                val current = db.paymentDao().getPaymentById(localPaymentId)
                if (current != null && current.firestoreId == null) {
                    db.paymentDao().updatePayment(
                        current.copy(firestoreId = fsPay, addedByUid = firestore.currentUid)
                    )
                }
            } catch (e: Exception) {
                Log.e("SyncRepository", "Failed to upload payment to Firestore", e)
            }
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
        val memberByFsId = localMembers.filter { it.firestoreId != null }.associateBy { it.firestoreId!! }
        var localExpenses = db.expenseDao().getExpensesByGroupOnce(localGroupId)

        // ── Phase 1: Clean up any duplicate rows in Room DB ──
        // 1a. Deduplicate duplicate firestoreIds in Room
        val seenFsIds = mutableSetOf<String>()
        val toDelete = mutableListOf<Expense>()
        for (exp in localExpenses) {
            val fsId = exp.firestoreId
            if (fsId != null) {
                if (seenFsIds.contains(fsId)) {
                    toDelete.add(exp)
                } else {
                    seenFsIds.add(fsId)
                }
            }
        }
        for (dup in toDelete) {
            db.expenseDao().deleteExpense(dup)
        }
        if (toDelete.isNotEmpty()) {
            localExpenses = db.expenseDao().getExpensesByGroupOnce(localGroupId)
        }

        // 1b. Deduplicate identical local expenses (e.g. one with firestoreId and one without)
        val seenSignatures = mutableMapOf<String, Expense>()
        val sigDuplicates = mutableListOf<Expense>()
        for (exp in localExpenses) {
            val timeBucket = exp.createdAt / 120_000L // 2-minute bucket
            val sig = "${exp.amount}_${exp.currency}_${exp.description}_${exp.paidByMemberId}_$timeBucket"
            val existing = seenSignatures[sig]
            if (existing != null) {
                if (existing.firestoreId == null && exp.firestoreId != null) {
                    sigDuplicates.add(existing)
                    seenSignatures[sig] = exp
                } else {
                    sigDuplicates.add(exp)
                }
            } else {
                seenSignatures[sig] = exp
            }
        }
        for (dup in sigDuplicates) {
            db.expenseDao().deleteExpense(dup)
        }
        if (sigDuplicates.isNotEmpty()) {
            localExpenses = db.expenseDao().getExpensesByGroupOnce(localGroupId)
        }

        // ── Phase 2: Reconcile with remote Firestore docs ──
        val localByFsId = localExpenses.filter { it.firestoreId != null }.associateBy { it.firestoreId!! }.toMutableMap()
        val localUnsynced = localExpenses.filter { it.firestoreId == null }.toMutableList()
        val remoteFsIds = remoteDocs.mapNotNull { it["_fsId"] as? String }.toSet()

        for (doc in remoteDocs) {
            val fsId = doc["_fsId"] as? String ?: continue
            val paidByFsId = doc["paidByMemberFsId"] as? String ?: ""
            val paidByMember = memberByFsId[paidByFsId] ?: localMembers.firstOrNull() ?: continue
            val amount = (doc["amount"] as? Double) ?: (doc["amount"] as? Long)?.toDouble() ?: continue
            val currency = doc["currency"] as? String ?: continue
            val category = doc["category"] as? String
            val description = doc["description"] as? String ?: ""
            val addedByUid = doc["addedByUid"] as? String
            val createdAt = (doc["createdAt"] as? Long) ?: System.currentTimeMillis()

            if (localByFsId.containsKey(fsId)) {
                // Already present locally
                continue
            }

            // Check if there is an unsynced local expense that matches this remote expense
            val matchingLocal = localUnsynced.firstOrNull { un ->
                Math.abs(un.amount - amount) < 0.001 &&
                un.currency == currency &&
                un.description == description &&
                Math.abs(un.createdAt - createdAt) < 120_000L
            }

            if (matchingLocal != null) {
                val updated = matchingLocal.copy(firestoreId = fsId)
                db.expenseDao().updateExpense(updated)
                localUnsynced.remove(matchingLocal)
                localByFsId[fsId] = updated
            } else {
                // Remote expense created on another device
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
        }

        // Delete locals no longer in remote (only if they had a firestoreId)
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
        val memberByFsId = localMembers.filter { it.firestoreId != null }.associateBy { it.firestoreId!! }
        var localPayments = db.paymentDao().getPaymentsByGroupOnce(localGroupId)

        // Deduplicate duplicate firestoreIds in Room
        val seenFsIds = mutableSetOf<String>()
        val toDelete = mutableListOf<Payment>()
        for (pay in localPayments) {
            val fsId = pay.firestoreId
            if (fsId != null) {
                if (seenFsIds.contains(fsId)) {
                    toDelete.add(pay)
                } else {
                    seenFsIds.add(fsId)
                }
            }
        }
        for (dup in toDelete) {
            db.paymentDao().deletePayment(dup)
        }
        if (toDelete.isNotEmpty()) {
            localPayments = db.paymentDao().getPaymentsByGroupOnce(localGroupId)
        }

        // Deduplicate identical local payments
        val seenSignatures = mutableMapOf<String, Payment>()
        val sigDuplicates = mutableListOf<Payment>()
        for (pay in localPayments) {
            val timeBucket = pay.createdAt / 120_000L
            val sig = "${pay.amount}_${pay.currency}_${pay.fromMemberId}_${pay.toMemberId}_$timeBucket"
            val existing = seenSignatures[sig]
            if (existing != null) {
                if (existing.firestoreId == null && pay.firestoreId != null) {
                    sigDuplicates.add(existing)
                    seenSignatures[sig] = pay
                } else {
                    sigDuplicates.add(pay)
                }
            } else {
                seenSignatures[sig] = pay
            }
        }
        for (dup in sigDuplicates) {
            db.paymentDao().deletePayment(dup)
        }
        if (sigDuplicates.isNotEmpty()) {
            localPayments = db.paymentDao().getPaymentsByGroupOnce(localGroupId)
        }

        val localByFsId = localPayments.filter { it.firestoreId != null }.associateBy { it.firestoreId!! }.toMutableMap()
        val localUnsynced = localPayments.filter { it.firestoreId == null }.toMutableList()
        val remoteFsIds = remoteDocs.mapNotNull { it["_fsId"] as? String }.toSet()

        for (doc in remoteDocs) {
            val fsId = doc["_fsId"] as? String ?: continue
            if (localByFsId.containsKey(fsId)) continue

            val fromFsId = doc["fromMemberFsId"] as? String ?: continue
            val toFsId = doc["toMemberFsId"] as? String ?: continue
            val fromMember = memberByFsId[fromFsId] ?: continue
            val toMember = memberByFsId[toFsId] ?: continue
            val amount = (doc["amount"] as? Double) ?: (doc["amount"] as? Long)?.toDouble() ?: continue
            val currency = doc["currency"] as? String ?: continue
            val addedByUid = doc["addedByUid"] as? String
            val createdAt = (doc["createdAt"] as? Long) ?: System.currentTimeMillis()

            val matchingLocal = localUnsynced.firstOrNull { un ->
                un.fromMemberId == fromMember.id &&
                un.toMemberId == toMember.id &&
                Math.abs(un.amount - amount) < 0.001 &&
                un.currency == currency &&
                Math.abs(un.createdAt - createdAt) < 120_000L
            }

            if (matchingLocal != null) {
                val updated = matchingLocal.copy(firestoreId = fsId)
                db.paymentDao().updatePayment(updated)
                localUnsynced.remove(matchingLocal)
                localByFsId[fsId] = updated
            } else {
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
        }

        // Delete locals no longer in remote
        localPayments
            .filter { it.firestoreId != null && it.firestoreId !in remoteFsIds }
            .forEach { db.paymentDao().deletePayment(it) }
    }
}
