package com.example.rentmate.models

data class ApartmentFeatures(
    val hasWifi: Boolean = false,
    val hasParking: Boolean = false,
    val numberOfRooms: Int = 0,
    val numberOfBathrooms: Int = 0,
    val hasAirConditioning: Boolean = false,
    val hasKitchen: Boolean = false
)