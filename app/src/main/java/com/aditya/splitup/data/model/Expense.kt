package com.aditya.splitup.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = SplitGroup::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Member::class,
            parentColumns = ["id"],
            childColumns = ["paidByMemberId"]
        )
    ],
    indices = [Index(value = ["groupId"]), Index(value = ["paidByMemberId"])]
)
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val firestoreId: String? = null,
    val groupId: Long,
    val paidByMemberId: Long,
    val amount: Double,
    val currency: String,
    val category: String? = null,
    val description: String = "",
    val addedByUid: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
