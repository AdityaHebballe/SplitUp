package com.aditya.splitup.data.repository

import com.aditya.splitup.data.dao.GroupDao
import com.aditya.splitup.data.dao.MemberDao
import com.aditya.splitup.data.model.Member
import com.aditya.splitup.data.model.SplitGroup
import kotlinx.coroutines.flow.Flow

class GroupRepository(
    private val groupDao: GroupDao,
    private val memberDao: MemberDao
) {
    fun getAllGroups(): Flow<List<SplitGroup>> = groupDao.getAllGroups()

    fun getGroupById(groupId: Long): Flow<SplitGroup?> = groupDao.getGroupById(groupId)

    fun getMembersByGroup(groupId: Long): Flow<List<Member>> = memberDao.getMembersByGroup(groupId)

    suspend fun insertGroup(group: SplitGroup): Long = groupDao.insertGroup(group)

    suspend fun updateGroup(group: SplitGroup) = groupDao.updateGroup(group)

    suspend fun deleteGroup(group: SplitGroup) = groupDao.deleteGroup(group)

    suspend fun insertMember(member: Member): Long = memberDao.insertMember(member)

    suspend fun updateMember(member: Member) = memberDao.updateMember(member)

    suspend fun deleteMember(member: Member) = memberDao.deleteMember(member)
}
