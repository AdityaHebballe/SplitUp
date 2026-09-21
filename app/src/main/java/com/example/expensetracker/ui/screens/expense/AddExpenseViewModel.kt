package com.example.expensetracker.ui.screens.expense

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.data.AppDatabase
import com.example.expensetracker.data.model.Expense
import com.example.expensetracker.data.model.ExpenseSplit
import com.example.expensetracker.data.model.Member
import com.example.expensetracker.data.model.SplitGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AddExpenseViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val groupDao = db.groupDao()
    private val memberDao = db.memberDao()
    private val expenseDao = db.expenseDao()

    private val _members = MutableStateFlow<List<Member>>(emptyList())
    val members: StateFlow<List<Member>> = _members

    private val _group = MutableStateFlow<SplitGroup?>(null)
    val group: StateFlow<SplitGroup?> = _group

    fun loadGroup(groupId: Long) {
        viewModelScope.launch {
            groupDao.getGroupById(groupId).collect { 
                _group.value = it 
            }
        }
        viewModelScope.launch {
            memberDao.getMembersByGroup(groupId).collect { 
                _members.value = it 
            }
        }
    }

    fun saveExpense(
        amount: Double,
        currency: String,
        description: String,
        category: String?,
        paidByMemberId: Long,
        ratios: Map<Long, Int>,
        onSuccess: () -> Unit
    ) {
        val groupId = _group.value?.id ?: return
        viewModelScope.launch {
            val expense = Expense(
                groupId = groupId,
                paidByMemberId = paidByMemberId,
                amount = amount,
                currency = currency,
                category = category,
                description = description
            )
            val splits = ratios.map { (memberId, ratioPart) ->
                ExpenseSplit(
                    expenseId = 0, // Assigned by DAO
                    memberId = memberId,
                    ratioPart = ratioPart
                )
            }
            expenseDao.insertExpenseWithSplits(expense, splits)
            onSuccess()
        }
    }

    suspend fun getSplitsForExpense(expenseId: Long): List<ExpenseSplit> {
        return expenseDao.getSplitsForExpense(expenseId)
    }

    fun updateExpense(
        expenseId: Long,
        amount: Double,
        currency: String,
        description: String,
        category: String?,
        paidByMemberId: Long,
        ratios: Map<Long, Int>,
        onSuccess: () -> Unit
    ) {
        val groupId = _group.value?.id ?: return
        viewModelScope.launch {
            val expense = Expense(
                id = expenseId,
                groupId = groupId,
                paidByMemberId = paidByMemberId,
                amount = amount,
                currency = currency,
                category = category,
                description = description
            )
            val splits = ratios.map { (memberId, ratioPart) ->
                ExpenseSplit(
                    expenseId = expenseId,
                    memberId = memberId,
                    ratioPart = ratioPart
                )
            }
            expenseDao.updateExpenseWithSplits(expense, splits)
            onSuccess()
        }
    }
}
