package com.aditya.splitup.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.aditya.splitup.data.model.Payment
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments WHERE groupId = :groupId ORDER BY createdAt DESC")
    fun getPaymentsByGroup(groupId: Long): Flow<List<Payment>>

    @Query("SELECT * FROM payments WHERE groupId = :groupId ORDER BY createdAt DESC")
    suspend fun getPaymentsByGroupOnce(groupId: Long): List<Payment>

    @Query("SELECT * FROM payments WHERE firestoreId = :firestoreId LIMIT 1")
    suspend fun getPaymentByFirestoreId(firestoreId: String): Payment?

    @Insert
    suspend fun insertPayment(payment: Payment): Long

    @Update
    suspend fun updatePayment(payment: Payment)

    @Upsert
    suspend fun upsertPayment(payment: Payment): Long

    @Delete
    suspend fun deletePayment(payment: Payment)

    @Query("DELETE FROM payments WHERE groupId = :groupId AND firestoreId = :firestoreId")
    suspend fun deletePaymentByFirestoreId(groupId: Long, firestoreId: String)
}
