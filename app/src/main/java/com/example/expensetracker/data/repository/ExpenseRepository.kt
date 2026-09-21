package com.example.expensetracker.data.repository

import com.example.expensetracker.data.dao.ExpenseDao
import com.example.expensetracker.data.dao.PaymentDao
import com.example.expensetracker.data.model.Expense
import com.example.expensetracker.data.model.ExpenseSplit
import com.example.expensetracker.data.model.Payment
import kotlinx.coroutines.flow.Flow

class ExpenseRepository(
    private val expenseDao: ExpenseDao,
    private val paymentDao: PaymentDao
) {
    fun getExpensesByGroup(groupId: Long): Flow<List<Expense>> = expenseDao.getExpensesByGroup(groupId)

    suspend fun insertExpenseWithSplits(expense: Expense, splits: List<ExpenseSplit>) {
        expenseDao.insertExpenseWithSplits(expense, splits)
    }

    suspend fun deleteExpense(expense: Expense) = expenseDao.deleteExpense(expense)

    suspend fun getSplitsForExpense(expenseId: Long): List<ExpenseSplit> = expenseDao.getSplitsForExpense(expenseId)

    fun getPaymentsByGroup(groupId: Long): Flow<List<Payment>> = paymentDao.getPaymentsByGroup(groupId)

    suspend fun insertPayment(payment: Payment): Long = paymentDao.insertPayment(payment)

    suspend fun deletePayment(payment: Payment) = paymentDao.deletePayment(payment)
}
