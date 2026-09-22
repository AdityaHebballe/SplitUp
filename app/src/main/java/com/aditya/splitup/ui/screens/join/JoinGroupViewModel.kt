package com.aditya.splitup.ui.screens.join

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aditya.splitup.SplitTrackerApp
import com.aditya.splitup.data.AppDatabase
import com.aditya.splitup.data.model.Member
import com.aditya.splitup.data.model.SplitGroup
import com.aditya.splitup.data.sync.InviteInfo
import com.aditya.splitup.data.sync.InviteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class JoinState {
    object EnterCode : JoinState()
    object Loading : JoinState()
    data class Preview(val invite: InviteInfo, val memberCount: Int) : JoinState()
    data class AlreadyMember(val localGroupId: Long, val groupName: String) : JoinState()
    data class Error(val message: String) : JoinState()
    object Success : JoinState()
}

class JoinGroupViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val app = application as SplitTrackerApp
    private val syncRepo = app.syncRepository
    private val inviteRepo = InviteRepository()

    private val _state = MutableStateFlow<JoinState>(JoinState.EnterCode)
    val state: StateFlow<JoinState> = _state.asStateFlow()

    private var pendingInvite: InviteInfo? = null

    fun lookupCode(code: String) {
        if (code.isBlank()) return
        viewModelScope.launch {
            _state.value = JoinState.Loading
            try {
                val invite = inviteRepo.lookupInvite(code)
                if (invite != null) {
                    val existingGroup = db.groupDao().getGroupByFirestoreId(invite.groupFirestoreId)
                    if (existingGroup != null) {
                        _state.value = JoinState.AlreadyMember(existingGroup.id, invite.groupName)
                    } else {
                        val memberCount = try {
                            syncRepo.firestore.getGroupMembers(invite.groupFirestoreId).size
                        } catch (_: Exception) {
                            1
                        }
                        pendingInvite = invite
                        _state.value = JoinState.Preview(invite, memberCount)
                    }
                } else {
                    _state.value = JoinState.Error("Invalid or expired code. Check the code and try again.")
                }
            } catch (e: Exception) {
                Log.e("JoinGroupVM", "Lookup failed", e)
                _state.value = JoinState.Error("Something went wrong. Are you connected to the internet?")
            }
        }
    }

    fun joinGroup(memberName: String, onSuccess: (Long) -> Unit) {
        val invite = pendingInvite ?: return
        if (memberName.isBlank()) return
        val uid = syncRepo.firestore.currentUid ?: return

        viewModelScope.launch {
            _state.value = JoinState.Loading
            try {
                // 1. Fetch group details from Firestore (for correct currency, name, owner)
                val groupDoc = syncRepo.firestore.getGroup(invite.groupFirestoreId)
                val groupName = (groupDoc?.get("name") as? String) ?: invite.groupName
                val defaultCurrency = (groupDoc?.get("defaultCurrency") as? String) ?: "USD"
                val ownerUid = groupDoc?.get("ownerUid") as? String

                // 2. Check if this group already exists locally
                val existingGroup = db.groupDao().getGroupByFirestoreId(invite.groupFirestoreId)
                val localGroupId: Long

                if (existingGroup != null) {
                    localGroupId = existingGroup.id
                    db.groupDao().updateGroup(
                        existingGroup.copy(
                            name = groupName,
                            defaultCurrency = defaultCurrency,
                            ownerUid = ownerUid
                        )
                    )
                } else {
                    // Create a local group entry with Firestore currency and ownerUid
                    val newGroup = SplitGroup(
                        firestoreId = invite.groupFirestoreId,
                        name = groupName,
                        defaultCurrency = defaultCurrency,
                        ownerUid = ownerUid
                    )
                    localGroupId = db.groupDao().insertGroup(newGroup)
                }

                // 3. Add user's UID to Firestore group memberUids
                syncRepo.firestore.addUserToGroup(invite.groupFirestoreId, uid)

                // 4. Add new member in Firestore (linked to this UID)
                val fsMemberId = syncRepo.firestore.addMember(
                    invite.groupFirestoreId,
                    Member(groupId = localGroupId, name = memberName),
                    linkedUid = uid
                )

                // 5. Insert the new member locally
                db.memberDao().insertMember(
                    Member(
                        groupId = localGroupId,
                        firestoreId = fsMemberId,
                        name = memberName,
                        linkedUid = uid
                    )
                )

                // 6. Pre-populate all remote members into Room DB before starting sync
                // to eliminate the race condition where expenses arrive before members exist
                try {
                    val remoteMembers = syncRepo.firestore.getGroupMembers(invite.groupFirestoreId)
                    for (doc in remoteMembers) {
                        val fsId = doc["_fsId"] as? String ?: continue
                        val name = doc["name"] as? String ?: continue
                        val ratio = (doc["defaultRatioPart"] as? Long)?.toInt() ?: 1
                        val linkedUid = doc["linkedUid"] as? String
                        val existingMem = db.memberDao().getMemberByFirestoreId(fsId)
                        if (existingMem == null) {
                            db.memberDao().insertMember(
                                Member(
                                    groupId = localGroupId,
                                    firestoreId = fsId,
                                    name = name,
                                    defaultRatioPart = ratio,
                                    linkedUid = linkedUid
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e("JoinGroupVM", "Error pre-populating remote members", e)
                }

                // 7. Start sync — members are already in Room DB, so all expenses/splits reconcile cleanly
                syncRepo.startSync(invite.groupFirestoreId, localGroupId)

                _state.value = JoinState.Success
                onSuccess(localGroupId)
            } catch (e: Exception) {
                Log.e("JoinGroupVM", "Join failed", e)
                _state.value = JoinState.Error("Failed to join group. Please try again.")
            }
        }
    }

    fun resetToEnterCode() {
        pendingInvite = null
        _state.value = JoinState.EnterCode
    }
}
