package com.example.expensetracker.data.model

import androidx.room.Entity

@Entity(tableName = "exchange_rates", primaryKeys = ["baseCurrency", "quoteCurrency"])
data class ExchangeRate(
    val baseCurrency: String,
    val quoteCurrency: String,
    val rate: Double,
    val fetchedAt: Long = System.currentTimeMillis()
)
