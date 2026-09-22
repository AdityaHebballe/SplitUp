package com.aditya.splitup.ui.screens.settings

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aditya.splitup.SplitTrackerApp
import com.aditya.splitup.data.AppDatabase
import com.aditya.splitup.data.model.Member
import com.aditya.splitup.data.model.SplitGroup
import com.aditya.splitup.data.repository.GroupRepository
import com.aditya.splitup.data.sync.InviteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GroupSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val repository = GroupRepository(db.groupDao(), db.memberDao())
    private val syncRepo = (application as SplitTrackerApp).syncRepository
    private val inviteRepo = InviteRepository()

    private val _group = MutableStateFlow<SplitGroup?>(null)
    val group: StateFlow<SplitGroup?> = _group.asStateFlow()

    private val _members = MutableStateFlow<List<Member>>(emptyList())
    val members: StateFlow<List<Member>> = _members.asStateFlow()

    private val _inviteCode = MutableStateFlow<String?>(null)
    val inviteCode: StateFlow<String?> = _inviteCode.asStateFlow()

    private val _isGeneratingInvite = MutableStateFlow(false)
    val isGeneratingInvite: StateFlow<Boolean> = _isGeneratingInvite.asStateFlow()

    private val _inviteErrorMessage = MutableStateFlow<String?>(null)
    val inviteErrorMessage: StateFlow<String?> = _inviteErrorMessage.asStateFlow()

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

    fun generateInvite() {
        val currentGroup = _group.value ?: return
        _isGeneratingInvite.value = true
        _inviteErrorMessage.value = null
        viewModelScope.launch {
            try {
                var firestoreId = currentGroup.firestoreId
                if (firestoreId == null) {
                    // Group not yet uploaded to Firestore — upload it now
                    val uid = syncRepo.firestore.currentUid
                    firestoreId = syncRepo.firestore.createGroup(currentGroup)
                    val updatedGroup = currentGroup.copy(firestoreId = firestoreId, ownerUid = uid)
                    repository.updateGroup(updatedGroup)
                    _group.value = updatedGroup

                    // Also upload any members missing firestoreId
                    val members = db.memberDao().getMembersByGroupOnce(currentGroup.id)
                    for (m in members) {
                        if (m.firestoreId == null) {
                            val fsMemId = syncRepo.firestore.addMember(firestoreId, m, m.linkedUid)
                            val updatedMember = m.copy(firestoreId = fsMemId)
                            db.memberDao().updateMember(updatedMember)
                        }
                    }
                    syncRepo.startSync(firestoreId, currentGroup.id)
                }

                val code = inviteRepo.createInvite(firestoreId)
                _inviteCode.value = code
            } catch (e: Exception) {
                Log.e("GroupSettingsVM", "Failed to generate invite", e)
                _inviteErrorMessage.value = e.localizedMessage ?: "Failed to generate invite. Check connection."
            } finally {
                _isGeneratingInvite.value = false
            }
        }
    }

    fun clearInviteCode() {
        _inviteCode.value = null
        _inviteErrorMessage.value = null
    }

    val isOwner: Boolean
        get() {
            val currentGroup = _group.value ?: return true
            val uid = syncRepo.firestore.currentUid ?: return true
            return currentGroup.ownerUid == null || currentGroup.ownerUid == uid
        }

    fun deleteGroup(onSuccess: () -> Unit) {
        val currentGroup = _group.value ?: return
        viewModelScope.launch {
            syncRepo.deleteGroup(currentGroup, isOwner = isOwner)
            onSuccess()
        }
    }
}
