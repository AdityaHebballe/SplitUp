package com.example.expensetracker.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.expensetracker.data.model.SplitGroup
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups ORDER BY createdAt DESC")
    fun getAllGroups(): Flow<List<SplitGroup>>

    @Query("SELECT * FROM groups WHERE id = :groupId")
    fun getGroupById(groupId: Long): Flow<SplitGroup?>

    @Insert
    suspend fun insertGroup(group: SplitGroup): Long

    @Update
    suspend fun updateGroup(group: SplitGroup)

    @Delete
    suspend fun deleteGroup(group: SplitGroup)
}
