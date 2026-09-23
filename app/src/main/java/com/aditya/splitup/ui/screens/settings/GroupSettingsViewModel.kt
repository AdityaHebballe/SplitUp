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
import kotlinx.coroutines.Dispatchers
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

    private val _formerMembers = MutableStateFlow<List<Member>>(emptyList())
    val formerMembers: StateFlow<List<Member>> = _formerMembers.asStateFlow()

    private val _inviteCode = MutableStateFlow<String?>(null)
    val inviteCode: StateFlow<String?> = _inviteCode.asStateFlow()

    private val _isGeneratingInvite = MutableStateFlow(false)
    val isGeneratingInvite: StateFlow<Boolean> = _isGeneratingInvite.asStateFlow()

    private val _inviteErrorMessage = MutableStateFlow<String?>(null)
    val inviteErrorMessage: StateFlow<String?> = _inviteErrorMessage.asStateFlow()

    private val _isAddingMember = MutableStateFlow(false)
    val isAddingMember: StateFlow<Boolean> = _isAddingMember.asStateFlow()

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
            // Note: SyncRepository.reconcileMembers is the sole writer that heals a stale
            // isRemoved=true/linkedUid!=null combination (it always derives isRemoved from
            // linkedUid on every remote update), so this ViewModel only reads/filters here
            // rather than writing back — avoiding a second writer racing on the same row.
            repository.getMembersByGroup(groupId).collect { list ->
                _members.value = list.filter { !it.isRemoved || it.linkedUid != null }
                _formerMembers.value = list.filter { it.isRemoved && it.linkedUid == null }
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
        // Guard against double-tap/double-submit racing the check-then-act name lookup
        // below with a second in-flight add, which would otherwise create duplicate
        // local + remote member records.
        if (_isAddingMember.value) return
        _isAddingMember.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val existingLocal = db.memberDao().getMembersByGroupOnce(currentGroup.id)
                // 1. If an active member with this name already exists, do nothing
                val activeExisting = existingLocal.firstOrNull { !it.isRemoved && it.name.trim().equals(trimmed, ignoreCase = true) }
                if (activeExisting != null) {
                    Log.d("GroupSettingsVM", "Active member with name $trimmed already exists")
                    return@launch
                }

                // 2. If a former member with this name exists, restore them!
                val formerExisting = existingLocal.firstOrNull { it.isRemoved && it.name.trim().equals(trimmed, ignoreCase = true) }
                if (formerExisting != null) {
                    restoreMember(formerExisting)
                    return@launch
                }

                // 3. Insert locally first so UI updates immediately and member has a primary key
                val newMember = Member(
                    groupId = currentGroup.id,
                    name = trimmed
                )
                val localId = repository.insertMember(newMember)

                if (currentGroup.firestoreId != null) {
                    try {
                        val fsId = syncRepo.firestore.addMember(
                            currentGroup.firestoreId,
                            newMember.copy(id = localId)
                        )
                        repository.updateMember(newMember.copy(id = localId, firestoreId = fsId))
                    } catch (e: Exception) {
                        Log.e("GroupSettingsVM", "Failed to add member to Firestore", e)
                    }
                }
            } finally {
                _isAddingMember.value = false
            }
        }
    }

    fun restoreMember(member: Member) {
        val currentGroup = _group.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val restored = member.copy(isRemoved = false)
            repository.updateMember(restored)
            if (currentGroup.firestoreId != null && restored.firestoreId != null) {
                try {
                    syncRepo.firestore.restoreMemberInGroup(currentGroup.firestoreId, restored.firestoreId)
                } catch (e: Exception) {
                    Log.e("GroupSettingsVM", "Failed to restore member in Firestore", e)
                }
            }
        }
    }

    fun removeMember(member: Member) {
        val currentGroup = _group.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val allMembers = db.memberDao().getMembersByGroupOnce(currentGroup.id)

            // Check if there is another member that is a duplicate of this one
            // 1. By firestoreId:
            val duplicateByFsId = if (member.firestoreId != null) {
                allMembers.firstOrNull { it.id != member.id && it.firestoreId == member.firestoreId }
            } else null

            // 2. By name:
            val duplicateByName = if (duplicateByFsId == null) {
                allMembers.firstOrNull { it.id != member.id && it.name.trim().equals(member.name.trim(), ignoreCase = true) }
            } else null

            val survivingDuplicate = duplicateByFsId ?: duplicateByName

            if (survivingDuplicate != null) {
                // This is a duplicate! Merge all references into the surviving duplicate and remove this row.
                // DO NOT delete from Firestore because the member still exists in the group!
                try {
                    db.memberDao().mergeAndRemoveDuplicateMember(
                        keepMemberId = survivingDuplicate.id,
                        duplicateMemberId = member.id,
                        expenseDao = db.expenseDao()
                    )
                } catch (e: Exception) {
                    Log.e("GroupSettingsVM", "Failed to merge duplicate member ${member.name}", e)
                }
                return@launch
            }

            // Not a duplicate — this is a single member the user wants to remove.
            // Always soft-delete (mark as former member) rather than hard-deleting, even
            // when no history is visible right now: a concurrent SettleUpSheet write on
            // another device could be recording a payment against this member between our
            // check above and a hard delete, which would otherwise orphan that payment's
            // fromMemberId/toMemberId. Soft-delete makes that race harmless.
            val removed = member.copy(isRemoved = true, linkedUid = null)
            repository.updateMember(removed)
            if (currentGroup.firestoreId != null && member.firestoreId != null) {
                try {
                    syncRepo.firestore.removeMemberFromGroup(currentGroup.firestoreId, member.firestoreId, member.linkedUid)
                } catch (e: Exception) {
                    Log.e("GroupSettingsVM", "Failed to remove member in Firestore", e)
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
