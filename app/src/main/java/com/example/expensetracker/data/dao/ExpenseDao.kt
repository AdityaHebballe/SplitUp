package com.example.expensetracker.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.expensetracker.data.model.Expense
import com.example.expensetracker.data.model.ExpenseSplit
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE groupId = :groupId ORDER BY createdAt DESC")
    fun getExpensesByGroup(groupId: Long): Flow<List<Expense>>

    @Insert
    suspend fun insertExpense(expense: Expense): Long

    @Update
    suspend fun updateExpense(expense: Expense)

    @Delete
    suspend fun deleteExpense(expense: Expense)

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
}
