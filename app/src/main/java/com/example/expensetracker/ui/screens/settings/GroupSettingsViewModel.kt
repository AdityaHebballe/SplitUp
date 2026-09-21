package com.example.expensetracker.ui.screens.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.data.AppDatabase
import com.example.expensetracker.data.model.Member
import com.example.expensetracker.data.model.SplitGroup
import com.example.expensetracker.data.repository.GroupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GroupSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val repository = GroupRepository(db.groupDao(), db.memberDao())

    private val _group = MutableStateFlow<SplitGroup?>(null)
    val group: StateFlow<SplitGroup?> = _group.asStateFlow()

    private val _members = MutableStateFlow<List<Member>>(emptyList())
    val members: StateFlow<List<Member>> = _members.asStateFlow()

    fun loadGroup(groupId: Long) {
        viewModelScope.launch {
            repository.getGroupById(groupId).collect { 
                _group.value = it 
            }
        }
        viewModelScope.launch {
            repository.getMembersByGroup(groupId).collect { 
                _members.value = it 
            }
        }
    }

    fun updateGroupName(name: String) {
        val currentGroup = _group.value ?: return
        viewModelScope.launch {
            repository.updateGroup(currentGroup.copy(name = name))
        }
    }

    fun updateDefaultCurrency(currency: String) {
        val currentGroup = _group.value ?: return
        viewModelScope.launch {
            repository.updateGroup(currentGroup.copy(defaultCurrency = currency))
        }
    }

    fun updateDefaultPayer(memberId: Long) {
        val currentGroup = _group.value ?: return
        viewModelScope.launch {
            repository.updateGroup(currentGroup.copy(defaultPayerMemberId = memberId))
        }
    }

    fun addMember(name: String) {
        val currentGroup = _group.value ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.insertMember(Member(groupId = currentGroup.id, name = name))
        }
    }

    fun removeMember(member: Member) {
        viewModelScope.launch {
            repository.deleteMember(member)
        }
    }

    fun updateMemberRatio(member: Member, ratio: Int) {
        if (ratio <= 0) return
        viewModelScope.launch {
            repository.updateMember(member.copy(defaultRatioPart = ratio))
        }
    }

    fun deleteGroup(onSuccess: () -> Unit) {
        val currentGroup = _group.value ?: return
        viewModelScope.launch {
            repository.deleteGroup(currentGroup)
            onSuccess()
        }
    }
}
