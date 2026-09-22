package com.aditya.splitup.data.network

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface FrankfurterApi {
    @GET("v2/rate/{base}/{quote}")
    suspend fun getRate(
        @Path("base") base: String,
        @Path("quote") quote: String
    ): SingleRateResponse

    @GET("v2/rates")
    suspend fun getRates(
        @Query("base") base: String,
        @Query("quotes") quotes: String
    ): List<SingleRateResponse>
}

@Serializable
data class SingleRateResponse(
    val date: String,
    val base: String,
    val quote: String,
    val rate: Double
)
