package com.aditya.splitup.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.aditya.splitup.data.model.Member
import kotlinx.coroutines.flow.Flow

@Dao
interface MemberDao {
    @Query("SELECT * FROM members WHERE groupId = :groupId")
    fun getMembersByGroup(groupId: Long): Flow<List<Member>>

    @Query("SELECT * FROM members WHERE groupId = :groupId")
    suspend fun getMembersByGroupOnce(groupId: Long): List<Member>

    @Query("SELECT * FROM members WHERE firestoreId = :firestoreId LIMIT 1")
    suspend fun getMemberByFirestoreId(firestoreId: String): Member?

    @Insert
    suspend fun insertMember(member: Member): Long

    @Update
    suspend fun updateMember(member: Member)

    @Upsert
    suspend fun upsertMember(member: Member): Long

    @Delete
    suspend fun deleteMember(member: Member)

    @Query("DELETE FROM members WHERE groupId = :groupId AND firestoreId = :firestoreId")
    suspend fun deleteMemberByFirestoreId(groupId: Long, firestoreId: String)
}
