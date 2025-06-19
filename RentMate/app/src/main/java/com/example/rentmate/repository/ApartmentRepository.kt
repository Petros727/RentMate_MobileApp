package com.example.rentmate.repository

import android.content.Context
import android.util.Log
import com.example.rentmate.models.Apartment
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID





class ApartmentRepository(private val context: Context? = null) {
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    suspend fun getApartments(): Result<List<Apartment>> {
        return try {
            val result = db.collection("apartments").get().await()
            val apartments = result.map { document ->
                document.toObject(Apartment::class.java).copy(id = document.id)
            }
            Log.d("ApartmentRepository", "Fetched ${apartments.size} apartments")
            Result.success(apartments)
        } catch (e: Exception) {
            Log.e("ApartmentRepository", "Error fetching apartments: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getApartmentById(apartmentId: String): Result<Apartment> {
        return try {
            val document = db.collection("apartments").document(apartmentId).get().await()
            val apartment = document.toObject(Apartment::class.java)?.copy(id = document.id)
            if (apartment != null) {
                Result.success(apartment)
            } else {
                Result.failure(Exception("Apartment not found"))
            }
        } catch (e: Exception) {
            Log.e("ApartmentRepository", "Error fetching apartment: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getApartmentsByLandlordId(landlordId: String): Result<List<Apartment>> {
        return try {
            val result = db.collection("apartments")
                .whereEqualTo("landlordId", landlordId)
                .get()
                .await()
            val apartments = result.map { document ->
                document.toObject(Apartment::class.java).copy(id = document.id)
            }
            Log.d("ApartmentRepository", "Fetched ${apartments.size} apartments for landlord $landlordId")
            Result.success(apartments)
        } catch (e: Exception) {
            Log.e("ApartmentRepository", "Error fetching landlord apartments: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun addApartment(apartment: Apartment, imageUris: List<android.net.Uri>): Result<String> {
        return try {
            val photoUrls = withContext(Dispatchers.IO + NonCancellable) {
                imageUris.mapIndexed { index, uri ->
                    async {
                        try {
                            Log.d("ApartmentRepository", "Uploading image $index: $uri")
                            val fileName = "images/${UUID.randomUUID()}.jpg"
                            val storageRef = storage.reference.child(fileName)
                            val uploadTask = storageRef.putFile(uri)
                            uploadTask.await()
                            val downloadUrl = storageRef.downloadUrl.await()
                            val url = downloadUrl.toString()
                            Log.d("ApartmentRepository", "Image $index uploaded, URL: $url")
                            url
                        } catch (e: Exception) {
                            Log.e("ApartmentRepository", "Error uploading image $index: ${e.message}", e)
                            null
                        }
                    }
                }.mapNotNull { it.await() }
            }

            if (photoUrls.isEmpty() && imageUris.isNotEmpty()) {
                return Result.failure(Exception("Failed to upload images"))
            }

            val apartmentId = db.collection("apartments").document().id
            val apartmentWithId = apartment.copy(id = apartmentId, photoUrls = photoUrls)
            db.collection("apartments").document(apartmentId).set(apartmentWithId).await()
            Log.d("ApartmentRepository", "Apartment added with ID: $apartmentId")
            Result.success(apartmentId)
        } catch (e: Exception) {
            Log.e("ApartmentRepository", "Error adding apartment: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun updateApartment(
        apartmentId: String,
        updatedApartment: Apartment,
        newImageUris: List<android.net.Uri>,
        existingPhotoUrls: List<String>,
        photosToDelete: List<String>
    ): Result<Unit> {
        return try {
            if (photosToDelete.isNotEmpty()) {
                withContext(Dispatchers.IO + NonCancellable) {
                    for (url in photosToDelete) {
                        try {
                            val storageRef = storage.getReferenceFromUrl(url)
                            storageRef.delete().await()
                            Log.d("ApartmentRepository", "Deleted image during update: $url")
                        } catch (e: Exception) {
                            Log.e("ApartmentRepository", "Error deleting image $url: ${e.message}", e)
                        }
                    }
                }
            }

            val updatedPhotoUrls = mutableListOf<String>().apply { addAll(existingPhotoUrls - photosToDelete.toSet()) }
            if (newImageUris.isNotEmpty()) {
                val newUrls = withContext(Dispatchers.IO + NonCancellable) {
                    newImageUris.mapIndexed { index, uri ->
                        async {
                            try {
                                Log.d("ApartmentRepository", "Uploading new image $index: $uri")
                                val fileName = "images/${UUID.randomUUID()}.jpg"
                                val storageRef = storage.reference.child(fileName)
                                val uploadTask = storageRef.putFile(uri)
                                uploadTask.await()
                                val downloadUrl = storageRef.downloadUrl.await()
                                val url = downloadUrl.toString()
                                Log.d("ApartmentRepository", "New image $index uploaded, URL: $url")
                                url
                            } catch (e: Exception) {
                                Log.e("ApartmentRepository", "Error uploading new image $index: ${e.message}", e)
                                null
                            }
                        }
                    }.mapNotNull { it.await() }
                }
                updatedPhotoUrls.addAll(newUrls)
            }

            val finalApartment = updatedApartment.copy(id = apartmentId, photoUrls = updatedPhotoUrls)
            db.collection("apartments").document(apartmentId).set(finalApartment).await()
            Log.d("ApartmentRepository", "Apartment updated with ID: $apartmentId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ApartmentRepository", "Error updating apartment: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun deleteApartment(apartmentId: String, photoUrls: List<String>): Result<Unit> {
        return try {
            if (photoUrls.isNotEmpty()) {
                withContext(Dispatchers.IO + NonCancellable) {
                    for (url in photoUrls) {
                        try {
                            val storageRef = storage.getReferenceFromUrl(url)
                            storageRef.delete().await()
                            Log.d("ApartmentRepository", "Deleted image: $url")
                        } catch (e: Exception) {
                            Log.e("ApartmentRepository", "Error deleting image $url: ${e.message}", e)
                        }
                    }
                }
            }

            val reservationsSnapshot = db.collection("reservations")
                .whereEqualTo("apartmentId", apartmentId)
                .get()
                .await()
            for (doc in reservationsSnapshot) {
                doc.reference.delete().await()
                Log.d("ApartmentRepository", "Deleted reservation: ${doc.id}")
            }

            db.collection("apartments").document(apartmentId).delete().await()
            Log.d("ApartmentRepository", "Apartment deleted with ID: $apartmentId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ApartmentRepository", "Error deleting apartment: ${e.message}", e)
            Result.failure(e)
        }
    }
}