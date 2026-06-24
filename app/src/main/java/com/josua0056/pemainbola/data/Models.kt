package com.josua0056.pemainbola.data

import com.squareup.moshi.Json

data class PetResponse(
    @field:Json(name = "id") val id: String,
    @field:Json(name = "name") val name: String?,
    @field:Json(name = "description") val description: String?,
    @field:Json(name = "sub_id") val subId: String?,
    @field:Json(name = "url") val url: String?,
    @field:Json(name = "images") val images: List<PetImage>?
)

data class PetImage(
    @field:Json(name = "url") val url: String
)

data class User(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val photoUrl: String = ""
)

data class Player(
    val id: String = "",
    val name: String,
    val club: String,
    val imageUrl: String,
    val userId: String
)