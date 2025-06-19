package com.example.rentmate.models

data class Reservation(
    val id: String = "",
    val apartmentId: String = "",
    val userId: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val status: String = "confirmed",
    val isPaid: Boolean = false
)