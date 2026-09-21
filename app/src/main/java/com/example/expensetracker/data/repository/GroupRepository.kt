package com.example.expensetracker.data.repository

import com.example.expensetracker.data.dao.GroupDao
import com.example.expensetracker.data.dao.MemberDao
import com.example.expensetracker.data.model.Member
import com.example.expensetracker.data.model.SplitGroup
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
