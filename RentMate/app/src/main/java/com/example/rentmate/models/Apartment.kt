package com.example.rentmate.models

data class Apartment(
    val id: String = "",
    val name: String = "",
    val address: String = "",
    val price: Double = 0.0,
    val photoUrls: List<String> = emptyList(),
    val description: String = "",
    val landlordId: String = "",
    val features: ApartmentFeatures = ApartmentFeatures()
)