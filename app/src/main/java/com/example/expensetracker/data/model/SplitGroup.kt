package com.example.expensetracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "groups")
data class SplitGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val defaultCurrency: String = "USD",
    val defaultPayerMemberId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
