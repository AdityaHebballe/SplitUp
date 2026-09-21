package com.example.expensetracker.data.repository

import android.util.Log
import com.example.expensetracker.data.dao.ExchangeRateDao
import com.example.expensetracker.data.model.ExchangeRate
import com.example.expensetracker.data.network.FrankfurterApi

class ExchangeRateRepository(
    private val api: FrankfurterApi,
    private val dao: ExchangeRateDao
) {
    suspend fun getRate(base: String, quote: String): Double {
        val baseNorm = base.trim().uppercase()
        val quoteNorm = quote.trim().uppercase()

        if (baseNorm == quoteNorm) return 1.0

        val cached = dao.getRate(baseNorm, quoteNorm)
        if (cached != null && System.currentTimeMillis() - cached.fetchedAt < 3_600_000) {
            return cached.rate
        }

        try {
            val response = api.getRate(baseNorm, quoteNorm)
            val rate = response.rate
            dao.insertRate(ExchangeRate(baseNorm, quoteNorm, rate, System.currentTimeMillis()))
            return rate
        } catch (e: Exception) {
            Log.e("ExchangeRateRepo", "Failed to fetch rate for $baseNorm -> $quoteNorm", e)
            if (cached != null) return cached.rate
            throw e
        }
    }

    suspend fun convert(amount: Double, from: String, to: String): Double {
        return amount * getRate(from, to)
    }
}
