package com.example.rentmate.models

data class Review(
    val id: String = "",
    val apartmentId: String = "",
    val userId: String = "",
    val rating: Int = 0,
    val comment: String = "",
    val timestamp: Long = 0L
)