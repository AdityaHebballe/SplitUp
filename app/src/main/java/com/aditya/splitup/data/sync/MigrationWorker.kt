package com.aditya.splitup.data.sync

import android.util.Log
import com.aditya.splitup.data.AppDatabase
import com.aditya.splitup.data.model.Expense
import com.aditya.splitup.data.model.ExpenseSplit
import com.aditya.splitup.data.model.Member
import com.aditya.splitup.data.model.Payment

/**
 * Migrates all local-only Room groups (firestoreId == null) up to Firestore.
 * Called once after the user gets their anonymous Firebase UID.
 * Safe to call multiple times — already-migrated groups are skipped.
 */
class MigrationWorker(
    private val syncRepo: SyncRepository,
    private val db: AppDatabase
) {
    suspend fun migrateLocalGroups() {
        val uid = syncRepo.firestore.currentUid ?: run {
            Log.w("MigrationWorker", "No UID — skipping migration")
            return
        }

        val localGroups = db.groupDao().getAllGroupsOnce()
        for (group in localGroups) {
            if (group.firestoreId != null) {
                // Already synced — just make sure listeners are running
                syncRepo.startSync(group.firestoreId!!, group.id)
                continue
            }

            try {
                Log.d("MigrationWorker", "Migrating group '${group.name}' to Firestore")

                // 1. Create group in Firestore
                val fsGroupId = syncRepo.firestore.createGroup(group)
                db.groupDao().updateGroup(group.copy(firestoreId = fsGroupId, ownerUid = uid))

                // 2. Push members
                val members = db.memberDao().getMembersByGroupOnce(group.id)
                val memberFsIdMap = mutableMapOf<Long, String>()  // localId → firestoreId
                for (member in members) {
                    val linkedUid = if (members.size == 1 || member == members.first()) uid else null
                    val fsMemberId = syncRepo.firestore.addMember(fsGroupId, member, linkedUid)
                    db.memberDao().updateMember(
                        member.copy(firestoreId = fsMemberId, linkedUid = linkedUid)
                    )
                    memberFsIdMap[member.id] = fsMemberId
                }

                // 3. Push expenses + splits
                val expenses = db.expenseDao().getExpensesByGroupOnce(group.id)
                for (expense in expenses) {
                    val splits = db.expenseDao().getSplitsForExpense(expense.id)
                    val fsExpId = syncRepo.firestore.addExpense(
                        fsGroupId, expense, splits, memberFsIdMap
                    )
                    db.expenseDao().updateExpense(
                        expense.copy(firestoreId = fsExpId, addedByUid = uid)
                    )
                }

                // 4. Push payments
                val payments = db.paymentDao().getPaymentsByGroupOnce(group.id)
                for (payment in payments) {
                    val fsPayId = syncRepo.firestore.addPayment(fsGroupId, payment, memberFsIdMap)
                    db.paymentDao().updatePayment(
                        payment.copy(firestoreId = fsPayId, addedByUid = uid)
                    )
                }

                // 5. Start real-time listener
                syncRepo.startSync(fsGroupId, group.id)
                Log.d("MigrationWorker", "Migrated '${group.name}' → $fsGroupId")
            } catch (e: Exception) {
                Log.e("MigrationWorker", "Failed to migrate group '${group.name}'", e)
            }
        }
    }
}
