package com.example.expensetracker.network

import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface MeaningCloudService {

    /*
    @POST("class-1.1")
    suspend fun classifyText(
        @Field("key") apiKey: String,
        @Field("txt") text: String,
        @Field("lang") language: String,
        @Field("model") model: String*/
    @FormUrlEncoded
    @POST("deepcategorization-1.0")
    suspend fun classifyText(
        @Field("key") apiKey: String,
        @Field("txt") text: String,
        @Field("lang") language: String,
        @Field("model") model: String
    ): Response<ClassificationResponse>
}