package com.josua0056.pemainbola.data

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @GET("images")
    suspend fun getPlayers(
        @Header("x-api-key") apiKey: String,
        @Query("sub_id") userId: String
    ): List<PetResponse>

    @Multipart
    @POST("images/upload")
    suspend fun addPlayer(
        @Header("x-api-key") apiKey: String,
        @Part("name") name: RequestBody,
        @Part("description") club: RequestBody,
        @Part("sub_id") userId: RequestBody,
        @Part image: MultipartBody.Part
    ): PetResponse

    @DELETE("images/{id}")
    suspend fun deletePlayer(
        @Header("x-api-key") apiKey: String,
        @Path("id") id: String
    ): Response<Unit>
}