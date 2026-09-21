package com.example.expensetracker.ui.screens.create

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

class CreateGroupViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val repository = GroupRepository(db.groupDao(), db.memberDao())

    private val _groupName = MutableStateFlow("")
    val groupName: StateFlow<String> = _groupName.asStateFlow()

    private val _memberNames = MutableStateFlow<List<String>>(emptyList())
    val memberNames: StateFlow<List<String>> = _memberNames.asStateFlow()

    private val _ratios = MutableStateFlow<Map<String, Int>>(emptyMap())
    val ratios: StateFlow<Map<String, Int>> = _ratios.asStateFlow()

    private val _defaultCurrency = MutableStateFlow("USD")
    val defaultCurrency: StateFlow<String> = _defaultCurrency.asStateFlow()

    private val _defaultPayerIndex = MutableStateFlow(0)
    val defaultPayerIndex: StateFlow<Int> = _defaultPayerIndex.asStateFlow()

    fun updateGroupName(name: String) {
        _groupName.value = name
    }

    fun addMember(name: String) {
        if (name.isNotBlank() && !_memberNames.value.contains(name)) {
            _memberNames.value = _memberNames.value + name
            _ratios.value = _ratios.value.toMutableMap().apply { put(name, 1) }
        }
    }

    fun removeMember(name: String) {
        _memberNames.value = _memberNames.value - name
        _ratios.value = _ratios.value.toMutableMap().apply { remove(name) }
        if (_defaultPayerIndex.value >= _memberNames.value.size) {
            _defaultPayerIndex.value = maxOf(0, _memberNames.value.size - 1)
        }
    }

    fun updateRatio(name: String, ratio: Int) {
        if (ratio > 0) {
            _ratios.value = _ratios.value.toMutableMap().apply { put(name, ratio) }
        }
    }

    fun setDefaultCurrency(currency: String) {
        _defaultCurrency.value = currency
    }

    fun setDefaultPayerIndex(index: Int) {
        _defaultPayerIndex.value = index
    }

    fun createGroup(onSuccess: (Long) -> Unit) {
        viewModelScope.launch {
            val group = SplitGroup(
                name = _groupName.value.ifBlank { "Unnamed Group" },
                defaultCurrency = _defaultCurrency.value
            )
            val groupId = repository.insertGroup(group)

            var defaultPayerId: Long? = null
            _memberNames.value.forEachIndexed { index, name ->
                val ratio = _ratios.value[name] ?: 1
                val member = Member(
                    groupId = groupId,
                    name = name,
                    defaultRatioPart = ratio
                )
                val memberId = repository.insertMember(member)
                if (index == _defaultPayerIndex.value) {
                    defaultPayerId = memberId
                }
            }

            if (defaultPayerId != null) {
                repository.updateGroup(group.copy(id = groupId, defaultPayerMemberId = defaultPayerId))
            }
            onSuccess(groupId)
        }
    }
}
