package com.aditya.splitup.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.aditya.splitup.data.model.SplitGroup
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups ORDER BY createdAt DESC")
    fun getAllGroups(): Flow<List<SplitGroup>>

    @Query("SELECT * FROM groups ORDER BY createdAt DESC")
    suspend fun getAllGroupsOnce(): List<SplitGroup>

    @Query("SELECT * FROM groups WHERE id = :groupId")
    fun getGroupById(groupId: Long): Flow<SplitGroup?>

    @Query("SELECT * FROM groups WHERE firestoreId = :firestoreId LIMIT 1")
    suspend fun getGroupByFirestoreId(firestoreId: String): SplitGroup?

    @Insert
    suspend fun insertGroup(group: SplitGroup): Long

    @Update
    suspend fun updateGroup(group: SplitGroup)

    @Upsert
    suspend fun upsertGroup(group: SplitGroup): Long

    @Delete
    suspend fun deleteGroup(group: SplitGroup)
}
