package com.aditya.splitup.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.aditya.splitup.data.model.Member
import kotlinx.coroutines.flow.Flow

@Dao
interface MemberDao {
    @Query("SELECT * FROM members WHERE groupId = :groupId")
    fun getMembersByGroup(groupId: Long): Flow<List<Member>>

    @Query("SELECT * FROM members WHERE groupId = :groupId")
    suspend fun getMembersByGroupOnce(groupId: Long): List<Member>

    @Query("SELECT * FROM members WHERE firestoreId = :firestoreId LIMIT 1")
    suspend fun getMemberByFirestoreId(firestoreId: String): Member?

    @Insert
    suspend fun insertMember(member: Member): Long

    @Update
    suspend fun updateMember(member: Member)

    @Upsert
    suspend fun upsertMember(member: Member): Long

    @Delete
    suspend fun deleteMember(member: Member)

    @Query("DELETE FROM members WHERE groupId = :groupId AND firestoreId = :firestoreId")
    suspend fun deleteMemberByFirestoreId(groupId: Long, firestoreId: String)

    @Query("UPDATE expenses SET paidByMemberId = :keepMemberId WHERE paidByMemberId = :duplicateMemberId")
    suspend fun reassignExpensePayer(keepMemberId: Long, duplicateMemberId: Long)

    @Query("UPDATE payments SET fromMemberId = :keepMemberId WHERE fromMemberId = :duplicateMemberId")
    suspend fun reassignPaymentFromMember(keepMemberId: Long, duplicateMemberId: Long)

    @Query("UPDATE payments SET toMemberId = :keepMemberId WHERE toMemberId = :duplicateMemberId")
    suspend fun reassignPaymentToMember(keepMemberId: Long, duplicateMemberId: Long)

    @Query("UPDATE groups SET defaultPayerMemberId = :keepMemberId WHERE defaultPayerMemberId = :duplicateMemberId")
    suspend fun reassignGroupDefaultPayer(keepMemberId: Long, duplicateMemberId: Long)

    @Query("DELETE FROM members WHERE id = :memberId")
    suspend fun deleteMemberById(memberId: Long)

    @Transaction
    suspend fun mergeAndRemoveDuplicateMember(
        keepMemberId: Long,
        duplicateMemberId: Long,
        expenseDao: ExpenseDao
    ) {
        if (keepMemberId == duplicateMemberId) return
        reassignExpensePayer(keepMemberId, duplicateMemberId)
        reassignPaymentFromMember(keepMemberId, duplicateMemberId)
        reassignPaymentToMember(keepMemberId, duplicateMemberId)
        reassignGroupDefaultPayer(keepMemberId, duplicateMemberId)

        val duplicateSplits = expenseDao.getSplitsByMemberId(duplicateMemberId)
        val keepSplits = expenseDao.getSplitsByMemberId(keepMemberId)
        val keepExpenseIds = keepSplits.map { it.expenseId }.toSet()

        for (split in duplicateSplits) {
            if (split.expenseId in keepExpenseIds) {
                expenseDao.deleteSplitById(split.id)
            } else {
                expenseDao.updateSplitMember(split.id, keepMemberId)
            }
        }

        deleteMemberById(duplicateMemberId)
    }
}
