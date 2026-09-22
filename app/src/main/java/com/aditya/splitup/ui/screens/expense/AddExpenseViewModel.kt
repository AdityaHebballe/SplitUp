package com.aditya.splitup.ui.screens.expense

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aditya.splitup.SplitTrackerApp
import com.aditya.splitup.data.AppDatabase
import com.aditya.splitup.data.model.Expense
import com.aditya.splitup.data.model.ExpenseSplit
import com.aditya.splitup.data.model.Member
import com.aditya.splitup.data.model.SplitGroup
import com.aditya.splitup.data.sync.SyncRepository
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AddExpenseViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val groupDao = db.groupDao()
    private val memberDao = db.memberDao()
    private val expenseDao = db.expenseDao()
    private val syncRepo: SyncRepository = (application as SplitTrackerApp).syncRepository

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
        val currentGroup = _group.value
        if (currentGroup == null) {
            Log.w("AddExpenseViewModel", "Cannot save expense: group is null")
            onSuccess()
            return
        }
        val uid = syncRepo.firestore.currentUid
        viewModelScope.launch {
            try {
                val expense = Expense(
                    groupId = currentGroup.id,
                    paidByMemberId = paidByMemberId,
                    amount = amount,
                    currency = currency,
                    category = category,
                    description = description,
                    addedByUid = uid
                )
                val splits = ratios.map { (memberId, ratioPart) ->
                    ExpenseSplit(
                        expenseId = 0, // Assigned by DAO
                        memberId = memberId,
                        ratioPart = ratioPart
                    )
                }
                syncRepo.addExpense(currentGroup, expense, splits)
            } catch (e: Exception) {
                Log.e("AddExpenseViewModel", "Error saving expense", e)
            } finally {
                onSuccess()
            }
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
        val currentGroup = _group.value
        if (currentGroup == null) {
            Log.w("AddExpenseViewModel", "Cannot update expense: group is null")
            onSuccess()
            return
        }
        viewModelScope.launch {
            try {
                val expense = Expense(
                    id = expenseId,
                    groupId = currentGroup.id,
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
                syncRepo.updateExpense(currentGroup, expense, splits)
            } catch (e: Exception) {
                Log.e("AddExpenseViewModel", "Error updating expense", e)
            } finally {
                onSuccess()
            }
        }
    }
}
