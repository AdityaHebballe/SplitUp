package com.aditya.splitup.ui.screens.create

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aditya.splitup.SplitTrackerApp
import com.aditya.splitup.data.AppDatabase
import com.aditya.splitup.data.model.Member
import com.aditya.splitup.data.model.SplitGroup
import com.aditya.splitup.data.repository.GroupRepository
import com.aditya.splitup.data.sync.FirestoreService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CreateGroupViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val repository = GroupRepository(db.groupDao(), db.memberDao())
    private val syncRepo = (application as SplitTrackerApp).syncRepository
    private val firestoreService: FirestoreService = syncRepo.firestore

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
            val uid = firestoreService.currentUid
            val group = SplitGroup(
                name = _groupName.value.ifBlank { "Unnamed Group" },
                defaultCurrency = _defaultCurrency.value,
                ownerUid = uid
            )
            val groupId = repository.insertGroup(group)

            // Push group to Firestore
            val fsGroupId = try {
                firestoreService.createGroup(group.copy(id = groupId))
            } catch (e: Exception) {
                Log.e("CreateGroupVM", "Firestore group creation failed", e)
                null
            }

            if (fsGroupId != null) {
                repository.updateGroup(group.copy(id = groupId, firestoreId = fsGroupId, ownerUid = uid))
            }

            var defaultPayerId: Long? = null
            _memberNames.value.forEachIndexed { index, name ->
                val ratio = _ratios.value[name] ?: 1
                // First member gets linked to current user, rest are unlinked
                val linkedUid = if (index == 0) uid else null
                val member = Member(
                    groupId = groupId,
                    name = name,
                    defaultRatioPart = ratio,
                    linkedUid = linkedUid
                )
                val memberId = repository.insertMember(member)

                if (fsGroupId != null) {
                    val fsMemberId = try {
                        firestoreService.addMember(fsGroupId, member.copy(id = memberId), linkedUid)
                    } catch (e: Exception) {
                        Log.e("CreateGroupVM", "Firestore member creation failed", e)
                        null
                    }
                    if (fsMemberId != null) {
                        repository.updateMember(
                            member.copy(id = memberId, firestoreId = fsMemberId, linkedUid = linkedUid)
                        )
                    }
                }

                if (index == _defaultPayerIndex.value) {
                    defaultPayerId = memberId
                }
            }

            if (defaultPayerId != null) {
                val updatedGroup = db.groupDao().getAllGroupsOnce().find { it.id == groupId }
                if (updatedGroup != null) {
                    repository.updateGroup(updatedGroup.copy(defaultPayerMemberId = defaultPayerId))
                }
            }

            // Start sync listeners for the new group
            if (fsGroupId != null) {
                syncRepo.startSync(fsGroupId, groupId)
            }

            onSuccess(groupId)
        }
    }
}
