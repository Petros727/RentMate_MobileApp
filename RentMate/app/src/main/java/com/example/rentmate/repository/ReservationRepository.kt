package com.example.rentmate.repository

import android.util.Log
import com.example.rentmate.models.Reservation
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class ReservationRepository {
    private val db = FirebaseFirestore.getInstance()

    suspend fun createReservation(reservation: Reservation): Result<String> {
        return try {
            val reservationId = db.collection("reservations").document().id
            val reservationWithId = reservation.copy(id = reservationId)
            db.collection("reservations").document(reservationId).set(reservationWithId).await()
            Log.d("ReservationRepository", "Reservation created with ID: $reservationId")
            Result.success(reservationId)
        } catch (e: Exception) {
            Log.e("ReservationRepository", "Error creating reservation: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun cancelReservation(reservationId: String): Result<Unit> {
        return try {
            db.collection("reservations").document(reservationId).delete().await()
            Log.d("ReservationRepository", "Reservation deleted: $reservationId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ReservationRepository", "Error deleting reservation: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun updateReservation(reservationId: String, updatedReservation: Reservation): Result<Unit> {
        return try {
            val reservationWithId = updatedReservation.copy(id = reservationId)
            db.collection("reservations").document(reservationId).set(reservationWithId).await()
            Log.d("ReservationRepository", "Reservation updated: $reservationId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ReservationRepository", "Error updating reservation: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getReservationsByUserId(userId: String, currentDate: String): Result<List<Reservation>> {
        return try {
            val result = db.collection("reservations")
                .whereEqualTo("userId", userId)
                .whereGreaterThanOrEqualTo("endDate", currentDate)
                .get()
                .await()
            val reservations = result.map { document ->
                document.toObject(Reservation::class.java).copy(id = document.id)
            }
            Log.d("ReservationRepository", "Fetched ${reservations.size} reservations for userId: $userId")
            Result.success(reservations)
        } catch (e: Exception) {
            Log.e("ReservationRepository", "Error fetching reservations: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getReservationsForApartment(apartmentId: String): Result<List<Reservation>> {
        return try {
            val result = db.collection("reservations")
                .whereEqualTo("apartmentId", apartmentId)
                .get()
                .await()
            val reservations = result.map { document ->
                document.toObject(Reservation::class.java).copy(id = document.id)
            }
            Log.d("ReservationRepository", "Fetched ${reservations.size} reservations for apartmentId: $apartmentId")
            Result.success(reservations)
        } catch (e: Exception) {
            Log.e("ReservationRepository", "Error fetching reservations for apartment: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getPastReservationsByUserId(userId: String, currentDate: String): Result<List<Reservation>> {
        return try {
            val result = db.collection("reservations")
                .whereEqualTo("userId", userId)
                .whereLessThan("endDate", currentDate)
                .get()
                .await()
            val reservations = result.map { document ->
                document.toObject(Reservation::class.java).copy(id = document.id)
            }
            Log.d("ReservationRepository", "Fetched ${reservations.size} past reservations for userId: $userId")
            Result.success(reservations)
        } catch (e: Exception) {
            Log.e("ReservationRepository", "Error fetching past reservations: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getReservationById(reservationId: String): Result<Reservation> {
        return try {
            val document = db.collection("reservations").document(reservationId).get().await()
            val reservation = document.toObject(Reservation::class.java)?.copy(id = document.id)
            if (reservation != null) {
                Log.d("ReservationRepository", "Fetched reservation with ID: $reservationId")
                Result.success(reservation)
            } else {
                Log.w("ReservationRepository", "Reservation not found: $reservationId")
                Result.failure(Exception("Reservation not found"))
            }
        } catch (e: Exception) {
            Log.e("ReservationRepository", "Error fetching reservation: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getReservationsByApartmentId(apartmentId: String): Result<List<Reservation>> {
        return try {
            val result = db.collection("reservations")
                .whereEqualTo("apartmentId", apartmentId)
                .get()
                .await()
            val reservations = result.map { document ->
                document.toObject(Reservation::class.java).copy(id = document.id)
            }
            Log.d("ReservationRepository", "Fetched ${reservations.size} reservations for apartmentId: $apartmentId")
            Result.success(reservations)
        } catch (e: Exception) {
            Log.e("ReservationRepository", "Error fetching reservations by apartmentId: ${e.message}", e)
            Result.failure(e)
        }
    }
}