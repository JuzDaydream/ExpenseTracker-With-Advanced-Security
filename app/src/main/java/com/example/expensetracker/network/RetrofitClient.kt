package com.example.expensetracker.network


import com.google.gson.GsonBuilder
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
   private const val BASE_URL = "https://api.meaningcloud.com/"

    private val gson = GsonBuilder()
        .setLenient()
        .create()


    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
    val service: MeaningCloudService = retrofit.create(MeaningCloudService::class.java)
}