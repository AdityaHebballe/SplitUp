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
        val app = getApplication<SplitTrackerApp>()
        viewModelScope.launch {
            val uid = firestoreService.currentUid
            val group = SplitGroup(
                name = _groupName.value.ifBlank { "Unnamed Group" },
                defaultCurrency = _defaultCurrency.value,
                ownerUid = uid
            )
            // 1. Insert group into local Room database
            val groupId = repository.insertGroup(group)

            // 2. Insert members into local Room database
            var defaultPayerId: Long? = null
            val memberIdList = mutableListOf<Long>()
            _memberNames.value.forEachIndexed { index, name ->
                val ratio = _ratios.value[name] ?: 1
                val linkedUid = if (index == 0) uid else null
                val member = Member(
                    groupId = groupId,
                    name = name,
                    defaultRatioPart = ratio,
                    linkedUid = linkedUid
                )
                val memberId = repository.insertMember(member)
                memberIdList.add(memberId)

                if (index == _defaultPayerIndex.value) {
                    defaultPayerId = memberId
                }
            }

            if (defaultPayerId != null) {
                repository.updateGroup(group.copy(id = groupId, defaultPayerMemberId = defaultPayerId))
            }

            // 3. Close the screen immediately and navigate to the new group
            onSuccess(groupId)

            // 4. Background sync to Firestore (does not block UI or navigation)
            app.appScope.launch {
                try {
                    val fsGroupId = firestoreService.createGroup(group.copy(id = groupId))
                    repository.updateGroup(
                        group.copy(
                            id = groupId,
                            firestoreId = fsGroupId,
                            defaultPayerMemberId = defaultPayerId,
                            ownerUid = uid
                        )
                    )

                    _memberNames.value.forEachIndexed { index, name ->
                        val memberId = memberIdList.getOrNull(index) ?: return@forEachIndexed
                        val ratio = _ratios.value[name] ?: 1
                        val linkedUid = if (index == 0) uid else null
                        val fsMemberId = firestoreService.addMember(
                            fsGroupId,
                            Member(id = memberId, groupId = groupId, name = name, defaultRatioPart = ratio),
                            linkedUid
                        )
                        repository.updateMember(
                            Member(
                                id = memberId,
                                groupId = groupId,
                                firestoreId = fsMemberId,
                                name = name,
                                defaultRatioPart = ratio,
                                linkedUid = linkedUid
                            )
                        )
                    }

                    syncRepo.startSync(fsGroupId, groupId)
                    Log.d("CreateGroupVM", "Group '$groupId' successfully synced to Firestore as '$fsGroupId'")
                } catch (e: Exception) {
                    Log.e("CreateGroupVM", "Background Firestore sync failed (will retry on next app launch)", e)
                }
            }
        }
    }
}
