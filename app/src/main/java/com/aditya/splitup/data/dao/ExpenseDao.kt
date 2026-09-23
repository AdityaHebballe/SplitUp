package com.aditya.splitup.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.aditya.splitup.data.model.Expense
import com.aditya.splitup.data.model.ExpenseSplit
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE groupId = :groupId ORDER BY createdAt DESC")
    fun getExpensesByGroup(groupId: Long): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE groupId = :groupId ORDER BY createdAt DESC")
    suspend fun getExpensesByGroupOnce(groupId: Long): List<Expense>

    @Query("SELECT * FROM expenses WHERE firestoreId = :firestoreId LIMIT 1")
    suspend fun getExpenseByFirestoreId(firestoreId: String): Expense?

    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    suspend fun getExpenseById(id: Long): Expense?

    @Insert
    suspend fun insertExpense(expense: Expense): Long

    @Update
    suspend fun updateExpense(expense: Expense)

    @Upsert
    suspend fun upsertExpense(expense: Expense): Long

    @Delete
    suspend fun deleteExpense(expense: Expense)

    @Query("DELETE FROM expenses WHERE groupId = :groupId AND firestoreId = :firestoreId")
    suspend fun deleteExpenseByFirestoreId(groupId: Long, firestoreId: String)

    @Insert
    suspend fun insertSplits(splits: List<ExpenseSplit>)

    @Query("DELETE FROM expense_splits WHERE expenseId = :expenseId")
    suspend fun deleteSplitsForExpense(expenseId: Long)

    @Query("SELECT * FROM expense_splits WHERE expenseId = :expenseId")
    suspend fun getSplitsForExpense(expenseId: Long): List<ExpenseSplit>

    @Query("SELECT es.* FROM expense_splits es INNER JOIN expenses e ON es.expenseId = e.id WHERE e.groupId = :groupId")
    fun getSplitsForGroup(groupId: Long): Flow<List<ExpenseSplit>>

    @Transaction
    suspend fun insertExpenseWithSplits(expense: Expense, splits: List<ExpenseSplit>) {
        val expenseId = insertExpense(expense)
        insertSplits(splits.map { it.copy(expenseId = expenseId) })
    }

    @Transaction
    suspend fun updateExpenseWithSplits(expense: Expense, splits: List<ExpenseSplit>) {
        updateExpense(expense)
        deleteSplitsForExpense(expense.id)
        insertSplits(splits.map { it.copy(expenseId = expense.id) })
    }

    @Query("SELECT * FROM expense_splits WHERE memberId = :memberId")
    suspend fun getSplitsByMemberId(memberId: Long): List<ExpenseSplit>

    @Query("DELETE FROM expense_splits WHERE id = :id")
    suspend fun deleteSplitById(id: Long)

    @Query("UPDATE expense_splits SET memberId = :newMemberId WHERE id = :splitId")
    suspend fun updateSplitMember(splitId: Long, newMemberId: Long)

    @Query("SELECT * FROM expense_splits WHERE expenseId = :expenseId AND memberId = :memberId LIMIT 1")
    suspend fun getSplitForExpenseAndMember(expenseId: Long, memberId: Long): ExpenseSplit?

    @Query("UPDATE expense_splits SET ratioPart = :ratioPart WHERE id = :id")
    suspend fun updateSplitRatio(id: Long, ratioPart: Int)
}
