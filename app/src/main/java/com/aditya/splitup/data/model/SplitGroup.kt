package com.aditya.splitup.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "groups")
data class SplitGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val firestoreId: String? = null,
    val name: String,
    val defaultCurrency: String = "USD",
    val defaultPayerMemberId: Long? = null,
    val ownerUid: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
