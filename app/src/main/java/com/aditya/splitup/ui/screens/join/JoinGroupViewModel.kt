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
    data class Preview(
        val invite: InviteInfo,
        val memberCount: Int,
        val claimableMembers: List<String> = emptyList()
    ) : JoinState()
    data class Rejoin(val invite: InviteInfo, val memberFsId: String, val memberName: String) : JoinState()
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
                    val uid = syncRepo.firestore.currentUid
                    val remoteMembers = try {
                        syncRepo.firestore.getGroupMembers(invite.groupFirestoreId)
                    } catch (_: Exception) {
                        emptyList()
                    }

                    // Check if current user is linked to an existing member or previously held a member doc
                    val matchedMember = if (uid != null) {
                        remoteMembers.firstOrNull { doc ->
                            (doc["linkedUid"] == uid && doc["linkedUid"] != null) ||
                            (doc["previousUid"] == uid && doc["previousUid"] != null)
                        }
                    } else null

                    if (existingGroup != null && matchedMember != null && matchedMember["linkedUid"] == uid) {
                        _state.value = JoinState.AlreadyMember(existingGroup.id, invite.groupName)
                    } else if (matchedMember != null) {
                        pendingInvite = invite
                        val fsId = matchedMember["_fsId"] as? String ?: ""
                        val memName = matchedMember["name"] as? String ?: "Member"
                        _state.value = JoinState.Rejoin(invite, memberFsId = fsId, memberName = memName)
                    } else if (existingGroup != null) {
                        _state.value = JoinState.AlreadyMember(existingGroup.id, invite.groupName)
                    } else {
                        pendingInvite = invite
                        val claimable = remoteMembers
                            .filter { it["linkedUid"] == null }
                            .mapNotNull { it["name"] as? String }
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .distinct()
                        _state.value = JoinState.Preview(
                            invite = invite,
                            memberCount = remoteMembers.size,
                            claimableMembers = claimable
                        )
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

    fun rejoinGroup(memberFsId: String, memberName: String, onSuccess: (Long) -> Unit) {
        val invite = pendingInvite ?: return
        val uid = syncRepo.firestore.currentUid ?: return

        viewModelScope.launch {
            _state.value = JoinState.Loading
            try {
                // 1. Fetch group details from Firestore
                val groupDoc = syncRepo.firestore.getGroup(invite.groupFirestoreId)
                val groupName = (groupDoc?.get("name") as? String) ?: invite.groupName
                val defaultCurrency = (groupDoc?.get("defaultCurrency") as? String) ?: "USD"
                val ownerUid = groupDoc?.get("ownerUid") as? String

                // 2. Check or create local group entry
                val existingGroup = db.groupDao().getGroupByFirestoreId(invite.groupFirestoreId)
                val localGroupId: Long = if (existingGroup != null) {
                    db.groupDao().updateGroup(
                        existingGroup.copy(
                            name = groupName,
                            defaultCurrency = defaultCurrency,
                            ownerUid = ownerUid
                        )
                    )
                    existingGroup.id
                } else {
                    db.groupDao().insertGroup(
                        SplitGroup(
                            firestoreId = invite.groupFirestoreId,
                            name = groupName,
                            defaultCurrency = defaultCurrency,
                            ownerUid = ownerUid
                        )
                    )
                }

                // 3. Reclaim member in Firestore (sets linkedUid = uid, previousUid = null, adds uid to group memberUids)
                syncRepo.firestore.reclaimMember(invite.groupFirestoreId, memberFsId, uid)

                // 4. Pre-populate all remote members into Room DB
                val remoteMembers = syncRepo.firestore.getGroupMembers(invite.groupFirestoreId)
                for (doc in remoteMembers) {
                    val fsId = doc["_fsId"] as? String ?: continue
                    val name = doc["name"] as? String ?: continue
                    val ratio = (doc["defaultRatioPart"] as? Number)?.toInt() ?: 1
                    val linkedUid = if (fsId == memberFsId) uid else doc["linkedUid"] as? String

                    val existingMem = db.memberDao().getMemberByFirestoreId(fsId)
                    if (existingMem == null) {
                        val existingByName = db.memberDao().getMembersByGroupOnce(localGroupId)
                            .firstOrNull { it.name.trim().equals(name.trim(), ignoreCase = true) }
                        if (existingByName != null) {
                            db.memberDao().updateMember(
                                existingByName.copy(
                                    firestoreId = fsId,
                                    name = name,
                                    defaultRatioPart = ratio,
                                    linkedUid = linkedUid
                                )
                            )
                        } else {
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
                    } else {
                        db.memberDao().updateMember(
                            existingMem.copy(
                                name = name,
                                defaultRatioPart = ratio,
                                linkedUid = linkedUid
                            )
                        )
                    }
                }

                // 5. Start sync
                syncRepo.startSync(invite.groupFirestoreId, localGroupId)

                _state.value = JoinState.Success
                onSuccess(localGroupId)
            } catch (e: Exception) {
                Log.e("JoinGroupVM", "Rejoin failed", e)
                _state.value = JoinState.Error("Failed to rejoin group. Please try again.")
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
                val localGroupId: Long = if (existingGroup != null) {
                    db.groupDao().updateGroup(
                        existingGroup.copy(
                            name = groupName,
                            defaultCurrency = defaultCurrency,
                            ownerUid = ownerUid
                        )
                    )
                    existingGroup.id
                } else {
                    db.groupDao().insertGroup(
                        SplitGroup(
                            firestoreId = invite.groupFirestoreId,
                            name = groupName,
                            defaultCurrency = defaultCurrency,
                            ownerUid = ownerUid
                        )
                    )
                }

                // 3. Check if there is an unlinked member matching this name or previousUid
                val remoteMembers = try {
                    syncRepo.firestore.getGroupMembers(invite.groupFirestoreId)
                } catch (_: Exception) {
                    emptyList()
                }

                val alreadyLinked = remoteMembers.firstOrNull { it["linkedUid"] == uid }
                val matchedUnlinked = if (alreadyLinked != null) null else remoteMembers.firstOrNull { doc ->
                    val mName = doc["name"] as? String ?: ""
                    val isUnlinked = doc["linkedUid"] == null
                    val isPrevious = doc["previousUid"] == uid
                    val nameMatches = mName.trim().equals(memberName.trim(), ignoreCase = true)
                    isUnlinked && (isPrevious || nameMatches)
                }

                val targetFsMemberId: String
                if (alreadyLinked != null) {
                    targetFsMemberId = alreadyLinked["_fsId"] as String
                    syncRepo.firestore.addUserToGroup(invite.groupFirestoreId, uid)
                    syncRepo.firestore.reclaimMember(invite.groupFirestoreId, targetFsMemberId, uid)
                } else if (matchedUnlinked != null) {
                    targetFsMemberId = matchedUnlinked["_fsId"] as String
                    syncRepo.firestore.claimUnlinkedMember(invite.groupFirestoreId, targetFsMemberId, uid, memberName)
                } else {
                    syncRepo.firestore.addUserToGroup(invite.groupFirestoreId, uid)
                    targetFsMemberId = syncRepo.firestore.addMember(
                        invite.groupFirestoreId,
                        Member(groupId = localGroupId, name = memberName),
                        linkedUid = uid
                    )
                }

                // 4. Pre-populate all remote members into Room DB before starting sync
                val updatedRemoteMembers = try {
                    syncRepo.firestore.getGroupMembers(invite.groupFirestoreId)
                } catch (_: Exception) {
                    remoteMembers
                }

                for (doc in updatedRemoteMembers) {
                    val fsId = doc["_fsId"] as? String ?: continue
                    val name = doc["name"] as? String ?: continue
                    val ratio = (doc["defaultRatioPart"] as? Number)?.toInt() ?: 1
                    val linkedUid = if (fsId == targetFsMemberId) uid else doc["linkedUid"] as? String
                    val existingMem = db.memberDao().getMemberByFirestoreId(fsId)
                    if (existingMem == null) {
                        val existingByName = db.memberDao().getMembersByGroupOnce(localGroupId)
                            .firstOrNull { it.name.trim().equals(name.trim(), ignoreCase = true) }
                        if (existingByName != null) {
                            db.memberDao().updateMember(
                                existingByName.copy(
                                    firestoreId = fsId,
                                    name = name,
                                    defaultRatioPart = ratio,
                                    linkedUid = linkedUid
                                )
                            )
                        } else {
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
                    } else {
                        db.memberDao().updateMember(
                            existingMem.copy(
                                name = name,
                                defaultRatioPart = ratio,
                                linkedUid = linkedUid
                            )
                        )
                    }
                }

                val currentLocalTarget = db.memberDao().getMemberByFirestoreId(targetFsMemberId)
                if (currentLocalTarget == null) {
                    val targetByName = db.memberDao().getMembersByGroupOnce(localGroupId)
                        .firstOrNull { it.name.trim().equals(memberName.trim(), ignoreCase = true) }
                    if (targetByName != null) {
                        db.memberDao().updateMember(
                            targetByName.copy(
                                firestoreId = targetFsMemberId,
                                linkedUid = uid
                            )
                        )
                    } else {
                        db.memberDao().insertMember(
                            Member(
                                groupId = localGroupId,
                                firestoreId = targetFsMemberId,
                                name = memberName,
                                linkedUid = uid
                            )
                        )
                    }
                } else {
                    db.memberDao().updateMember(
                        currentLocalTarget.copy(name = memberName, linkedUid = uid)
                    )
                }

                // 5. Start sync — members are already in Room DB, so all expenses/splits reconcile cleanly
                syncRepo.startSync(invite.groupFirestoreId, localGroupId)

                _state.value = JoinState.Success
                onSuccess(localGroupId)
            } catch (e: Exception) {
                Log.e("JoinGroupVM", "Join failed", e)
                _state.value = JoinState.Error("Failed to join group. Please try again.")
            }
        }
    }

    fun enterAsNewMember() {
        val invite = pendingInvite ?: return
        viewModelScope.launch {
            val memberCount = try {
                syncRepo.firestore.getGroupMembers(invite.groupFirestoreId).size
            } catch (_: Exception) {
                1
            }
            _state.value = JoinState.Preview(invite, memberCount)
        }
    }

    fun resetToEnterCode() {
        pendingInvite = null
        _state.value = JoinState.EnterCode
    }
}
