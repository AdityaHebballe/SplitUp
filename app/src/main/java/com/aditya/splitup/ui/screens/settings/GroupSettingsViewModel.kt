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

    val currentUid: String? get() = syncRepo.firestore.currentUid

    fun loadGroup(groupId: Long) {
        viewModelScope.launch {
            repository.getGroupById(groupId).collect { grp ->
                _group.value = grp
                if (grp?.firestoreId != null) {
                    syncRepo.startSync(grp.firestoreId, grp.id)
                }
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
            val updated = currentGroup.copy(name = name)
            repository.updateGroup(updated)
            if (updated.firestoreId != null) {
                try {
                    syncRepo.firestore.updateGroup(updated)
                } catch (e: Exception) {
                    Log.e("GroupSettingsVM", "Failed to update group name in Firestore", e)
                }
            }
        }
    }

    fun updateDefaultCurrency(currency: String) {
        val currentGroup = _group.value ?: return
        viewModelScope.launch {
            val updated = currentGroup.copy(defaultCurrency = currency)
            repository.updateGroup(updated)
            if (updated.firestoreId != null) {
                try {
                    syncRepo.firestore.updateGroup(updated)
                } catch (e: Exception) {
                    Log.e("GroupSettingsVM", "Failed to update default currency in Firestore", e)
                }
            }
        }
    }

    fun updateDefaultPayer(memberId: Long) {
        val currentGroup = _group.value ?: return
        viewModelScope.launch {
            val updated = currentGroup.copy(defaultPayerMemberId = memberId)
            repository.updateGroup(updated)
            if (updated.firestoreId != null) {
                try {
                    syncRepo.firestore.updateGroup(updated)
                } catch (e: Exception) {
                    Log.e("GroupSettingsVM", "Failed to update default payer in Firestore", e)
                }
            }
        }
    }

    fun addMember(name: String) {
        val currentGroup = _group.value ?: return
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val existingLocal = db.memberDao().getMembersByGroupOnce(currentGroup.id)
            if (existingLocal.any { it.name.trim().equals(trimmed, ignoreCase = true) }) {
                Log.d("GroupSettingsVM", "Member with name $trimmed already exists")
                return@launch
            }

            var fsId: String? = null
            if (currentGroup.firestoreId != null) {
                try {
                    fsId = syncRepo.firestore.addMember(
                        currentGroup.firestoreId,
                        Member(groupId = currentGroup.id, name = trimmed)
                    )
                } catch (e: Exception) {
                    Log.e("GroupSettingsVM", "Failed to add member to Firestore", e)
                }
            }

            // If reconcileMembers already inserted via Firestore snapshot, do not re-insert
            if (fsId != null) {
                val alreadyInserted = db.memberDao().getMemberByFirestoreId(fsId)
                if (alreadyInserted == null) {
                    repository.insertMember(
                        Member(
                            groupId = currentGroup.id,
                            firestoreId = fsId,
                            name = trimmed
                        )
                    )
                }
            } else {
                repository.insertMember(
                    Member(
                        groupId = currentGroup.id,
                        name = trimmed
                    )
                )
            }
        }
    }

    fun removeMember(member: Member) {
        val currentGroup = _group.value ?: return
        viewModelScope.launch {
            val allMembers = db.memberDao().getMembersByGroupOnce(currentGroup.id)

            // If there are duplicate local records for the exact same firestoreId, only delete this local record!
            val duplicatesByFsId = if (member.firestoreId != null) {
                allMembers.filter { it.firestoreId == member.firestoreId }
            } else emptyList()

            if (duplicatesByFsId.size > 1) {
                db.memberDao().deleteMember(member)
                return@launch
            }

            // If there's an unlinked local duplicate matching an already-synced member, delete only this local row
            if (member.firestoreId == null && allMembers.any { it.name.trim().equals(member.name.trim(), ignoreCase = true) && it.id != member.id }) {
                db.memberDao().deleteMember(member)
                return@launch
            }

            val hasExpenses = db.expenseDao().getExpensesByGroupOnce(currentGroup.id).any { it.paidByMemberId == member.id }
            val hasPayments = db.paymentDao().getPaymentsByGroupOnce(currentGroup.id).any { it.fromMemberId == member.id || it.toMemberId == member.id }
            val isReferenced = hasExpenses || hasPayments

            if (currentGroup.firestoreId != null && member.firestoreId != null) {
                try {
                    if (isReferenced) {
                        syncRepo.firestore.unlinkMember(currentGroup.firestoreId, member.firestoreId, member.linkedUid)
                    } else {
                        syncRepo.firestore.deleteMember(currentGroup.firestoreId, member.firestoreId, member.linkedUid)
                    }
                } catch (e: Exception) {
                    Log.e("GroupSettingsVM", "Failed to remove member in Firestore", e)
                }
            }

            if (isReferenced) {
                repository.updateMember(member.copy(linkedUid = null))
            } else {
                try {
                    repository.deleteMember(member)
                } catch (_: Exception) {
                    repository.updateMember(member.copy(linkedUid = null))
                }
            }
        }
    }

    fun leaveGroup(onSuccess: () -> Unit) {
        val currentGroup = _group.value ?: return
        viewModelScope.launch {
            syncRepo.deleteGroup(currentGroup, isOwner = false)
            onSuccess()
        }
    }

    fun updateMemberRatio(member: Member, ratio: Int) {
        val currentGroup = _group.value ?: return
        if (ratio <= 0) return
        viewModelScope.launch {
            val updated = member.copy(defaultRatioPart = ratio)
            repository.updateMember(updated)
            if (currentGroup.firestoreId != null && updated.firestoreId != null) {
                try {
                    syncRepo.firestore.updateMember(currentGroup.firestoreId!!, updated)
                } catch (e: Exception) {
                    Log.e("GroupSettingsVM", "Failed to update member ratio in Firestore", e)
                }
            }
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
                }

                // Ensure all members, expenses, payments, and settings are synced!
                syncRepo.syncLocalUnsyncedData(currentGroup.id)
                syncRepo.startSync(firestoreId, currentGroup.id)

                val code = inviteRepo.createInvite(firestoreId, currentGroup.name)
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
