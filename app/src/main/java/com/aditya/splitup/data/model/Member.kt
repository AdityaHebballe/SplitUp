package com.aditya.splitup.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "members",
    foreignKeys = [
        ForeignKey(
            entity = SplitGroup::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["groupId"])]
)
data class Member(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val firestoreId: String? = null,
    val groupId: Long,
    val name: String,
    val defaultRatioPart: Int = 1,
    val linkedUid: String? = null,
    val isRemoved: Boolean = false
)
