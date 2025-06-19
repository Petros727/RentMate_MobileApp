package com.example.rentmate.viewModels

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rentmate.models.Apartment
import com.example.rentmate.models.ApartmentFeatures
import com.example.rentmate.models.Reservation
import com.example.rentmate.models.Review
import com.example.rentmate.repository.ApartmentRepository
import com.example.rentmate.repository.ReservationRepository
import com.example.rentmate.utils.ReservationNotificationManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ApartmentViewModel(
    private val apartmentRepository: ApartmentRepository = ApartmentRepository(),
    private val reservationRepository: ReservationRepository = ReservationRepository()
) : ViewModel() {
    private val db = Firebase.firestore
    private val auth = Firebase.auth

    private val _apartments = MutableStateFlow<List<Apartment>>(emptyList())
    val apartments: StateFlow<List<Apartment>> = _apartments

    private val _landlordApartments = MutableStateFlow<List<Apartment>>(emptyList())
    val landlordApartments: StateFlow<List<Apartment>> = _landlordApartments

    private val _selectedApartment = MutableStateFlow<Apartment?>(null)
    val selectedApartment: StateFlow<Apartment?> = _selectedApartment

    private val _addApartmentState = MutableStateFlow<AddApartmentState>(AddApartmentState.Idle)
    val addApartmentState: StateFlow<AddApartmentState> = _addApartmentState

    private val _updateApartmentState = MutableStateFlow<UpdateApartmentState>(UpdateApartmentState.Idle)
    val updateApartmentState: StateFlow<UpdateApartmentState> = _updateApartmentState

    private val _deleteApartmentState = MutableStateFlow<DeleteApartmentState>(DeleteApartmentState.Idle)
    val deleteApartmentState: StateFlow<DeleteApartmentState> = _deleteApartmentState

    private val _reservations = MutableStateFlow<List<Reservation>>(emptyList())
    val reservations: StateFlow<List<Reservation>> = _reservations

    private val _reservationsForApartment = MutableStateFlow<List<Reservation>>(emptyList())
    val reservationsForApartment: StateFlow<List<Reservation>> = _reservationsForApartment

    private val _pastReservations = MutableStateFlow<List<Reservation>>(emptyList())
    val pastReservations: StateFlow<List<Reservation>> = _pastReservations

    private val _createReservationState = MutableStateFlow<CreateReservationState>(CreateReservationState.Idle)
    val createReservationState: StateFlow<CreateReservationState> = _createReservationState

    private val _cancelReservationState = MutableStateFlow<CancelReservationState>(CancelReservationState.Idle)
    val cancelReservationState: StateFlow<CancelReservationState> = _cancelReservationState

    private val _updateReservationState = MutableStateFlow<UpdateReservationState>(UpdateReservationState.Idle)
    val updateReservationState: StateFlow<UpdateReservationState> = _updateReservationState

    private val _userRole = MutableStateFlow<String>("guest")
    val userRole: StateFlow<String> = _userRole.asStateFlow()

    private val _reviews = MutableStateFlow<List<Review>>(emptyList())
    val reviews: StateFlow<List<Review>> = _reviews

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val userId = firebaseAuth.currentUser?.uid
        Log.d("ApartmentViewModel", "Auth state changed, userId: $userId")
        if (userId != null) {
            fetchUserRole(userId)
        } else {
            _userRole.value = "guest"
            Log.d("ApartmentViewModel", "No user logged in, setting role to 'guest'")
        }
    }

    init {
        Log.d("ApartmentViewModel", "Initializing, loading apartments")
        auth.addAuthStateListener(authStateListener)
        loadApartments()
    }

    private fun fetchUserRole(userId: String) {
        Log.d("ApartmentViewModel", "Fetching role for userId: $userId")
        viewModelScope.launch {
            try {
                val document = db.collection("users").document(userId).get().await()
                if (document.exists()) {
                    _userRole.value = document.getString("role") ?: "guest"
                    Log.d("ApartmentViewModel", "User role fetched: ${_userRole.value}")
                } else {
                    _userRole.value = "guest"
                    Log.w("ApartmentViewModel", "User document does not exist for userId: $userId")
                }
            } catch (e: Exception) {
                _userRole.value = "guest"
                Log.e("ApartmentViewModel", "Error fetching user role: ${e.message}", e)
            }
        }
    }

    override fun onCleared() {
        Log.d("ApartmentViewModel", "ViewModel cleared, removing auth state listener")
        auth.removeAuthStateListener(authStateListener)
        super.onCleared()
    }

    fun loadApartments() {
        Log.d("ApartmentViewModel", "Loading apartments via repository")
        viewModelScope.launch {
            val result = apartmentRepository.getApartments()
            if (result.isSuccess) {
                _apartments.value = result.getOrDefault(emptyList())
                Log.d("ApartmentViewModel", "Loaded apartments: ${_apartments.value}")
            } else {
                Log.e("ApartmentViewModel", "Error loading apartments: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun loadLandlordApartments(landlordId: String) {
        viewModelScope.launch {
            val result = apartmentRepository.getApartmentsByLandlordId(landlordId)
            if (result.isSuccess) {
                _landlordApartments.value = result.getOrDefault(emptyList())
                Log.d("ApartmentViewModel", "Loaded landlord apartments for $landlordId: ${_landlordApartments.value}")
            } else {
                Log.e("ApartmentViewModel", "Error loading landlord apartments: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun loadApartmentById(apartmentId: String) {
        viewModelScope.launch {
            val result = apartmentRepository.getApartmentById(apartmentId)
            if (result.isSuccess) {
                _selectedApartment.value = result.getOrNull()
                Log.d("ApartmentViewModel", "Loaded apartment: ${_selectedApartment.value}")
            } else {
                Log.e("ApartmentViewModel", "Error loading apartment: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun loadReservations(userId: String) {
        viewModelScope.launch {
            val result = reservationRepository.getReservationsByUserId(userId, getCurrentDate())
            if (result.isSuccess) {
                _reservations.value = result.getOrDefault(emptyList())
                Log.d("ApartmentViewModel", "Loaded ${_reservations.value.size} reservations for userId: $userId")
            } else {
                Log.e("ApartmentViewModel", "Error loading reservations: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun loadReservationsForApartment(apartmentId: String) {
        viewModelScope.launch {
            val result = reservationRepository.getReservationsForApartment(apartmentId)
            if (result.isSuccess) {
                _reservationsForApartment.value = result.getOrDefault(emptyList())
                Log.d("ApartmentViewModel", "Loaded reservations for apartment $apartmentId: ${_reservationsForApartment.value}")
            } else {
                Log.e("ApartmentViewModel", "Error loading reservations for apartment: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun loadPastReservations(userId: String) {
        viewModelScope.launch {
            val result = reservationRepository.getPastReservationsByUserId(userId, getCurrentDate())
            if (result.isSuccess) {
                _pastReservations.value = result.getOrDefault(emptyList())
                Log.d("ApartmentViewModel", "Loaded ${_pastReservations.value.size} past reservations for userId: $userId")
            } else {
                Log.e("ApartmentViewModel", "Error loading past reservations: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun addApartment(
        name: String,
        address: String,
        price: Double,
        imageUris: List<Uri>,
        description: String,
        features: ApartmentFeatures
    ) {
        viewModelScope.launch {
            try {
                Log.d("ApartmentViewModel", "Starting to add apartment: $name")
                _addApartmentState.value = AddApartmentState.Loading

                val landlordId = Firebase.auth.currentUser?.uid ?: run {
                    Log.e("ApartmentViewModel", "User not logged in during apartment addition")
                    _addApartmentState.value = AddApartmentState.Error("User not logged in")
                    return@launch
                }

                val apartment = Apartment(
                    name = name,
                    address = address,
                    price = price,
                    description = description,
                    landlordId = landlordId,
                    features = features
                )

                val result = apartmentRepository.addApartment(apartment, imageUris)
                if (result.isSuccess) {
                    Log.d("ApartmentViewModel", "Apartment added with ID: ${result.getOrNull()}")
                    loadApartments()
                    loadLandlordApartments(landlordId)
                    _addApartmentState.value = AddApartmentState.Success("Apartment added successfully!")
                } else {
                    Log.e("ApartmentViewModel", "Failed to add apartment: ${result.exceptionOrNull()?.message}")
                    _addApartmentState.value = AddApartmentState.Error(
                        result.exceptionOrNull()?.message ?: "Failed to save apartment"
                    )
                }
            } catch (e: Exception) {
                Log.e("ApartmentViewModel", "Exception during apartment addition: ${e.message}", e)
                _addApartmentState.value = AddApartmentState.Error(e.message ?: "Unknown error during apartment addition")
            }
        }
    }

    fun updateApartment(
        apartmentId: String,
        name: String,
        address: String,
        price: Double,
        newImageUris: List<Uri>,
        existingPhotoUrls: List<String>,
        photosToDelete: List<String>,
        description: String,
        features: ApartmentFeatures
    ) {
        viewModelScope.launch {
            try {
                Log.d("ApartmentViewModel", "Starting to update apartment: $apartmentId")
                _updateApartmentState.value = UpdateApartmentState.Loading

                val currentUserId = Firebase.auth.currentUser?.uid ?: throw SecurityException("User not logged in")
                val apartmentResult = apartmentRepository.getApartmentById(apartmentId)
                if (apartmentResult.isFailure) {
                    throw Exception("Apartment not found")
                }
                val apartment = apartmentResult.getOrThrow()
                if (apartment.landlordId != currentUserId) {
                    throw SecurityException("Only the landlord can update this apartment")
                }

                val updatedApartment = Apartment(
                    name = name,
                    address = address,
                    price = price,
                    description = description,
                    landlordId = apartment.landlordId,
                    features = features
                )

                val result = apartmentRepository.updateApartment(
                    apartmentId,
                    updatedApartment,
                    newImageUris,
                    existingPhotoUrls,
                    photosToDelete
                )
                if (result.isSuccess) {
                    Log.d("ApartmentViewModel", "Apartment updated with ID: $apartmentId")
                    loadApartments()
                    loadLandlordApartments(currentUserId)
                    _updateApartmentState.value = UpdateApartmentState.Success("Apartment updated successfully!")
                } else {
                    Log.e("ApartmentViewModel", "Failed to update apartment: ${result.exceptionOrNull()?.message}")
                    _updateApartmentState.value = UpdateApartmentState.Error(
                        result.exceptionOrNull()?.message ?: "Failed to update apartment"
                    )
                }
            } catch (e: Exception) {
                Log.e("ApartmentViewModel", "Exception during apartment update: ${e.message}", e)
                _updateApartmentState.value = UpdateApartmentState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun deleteApartment(apartmentId: String, photoUrls: List<String>) {
        viewModelScope.launch {
            try {
                Log.d("ApartmentViewModel", "Starting to delete apartment: $apartmentId")
                _deleteApartmentState.value = DeleteApartmentState.Loading

                val currentUserId = Firebase.auth.currentUser?.uid ?: throw SecurityException("User not logged in")
                val apartmentResult = apartmentRepository.getApartmentById(apartmentId)
                if (apartmentResult.isFailure) {
                    throw Exception("Apartment not found")
                }
                val apartment = apartmentResult.getOrThrow()
                if (apartment.landlordId != currentUserId) {
                    throw SecurityException("Only the landlord can delete this apartment")
                }

                val result = apartmentRepository.deleteApartment(apartmentId, photoUrls)
                if (result.isSuccess) {
                    Log.d("ApartmentViewModel", "Apartment deleted with ID: $apartmentId")
                    loadApartments()
                    loadLandlordApartments(currentUserId)
                    _deleteApartmentState.value = DeleteApartmentState.Success("Apartment deleted successfully!")
                } else {
                    Log.e("ApartmentViewModel", "Failed to delete apartment: ${result.exceptionOrNull()?.message}")
                    _deleteApartmentState.value = DeleteApartmentState.Error(
                        result.exceptionOrNull()?.message ?: "Failed to delete apartment"
                    )
                }
            } catch (e: Exception) {
                Log.e("ApartmentViewModel", "Exception during apartment deletion: ${e.message}", e)
                _deleteApartmentState.value = DeleteApartmentState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun createReservation(
        apartmentId: String,
        userId: String,
        startDate: String,
        endDate: String,
        context: Context,
        isPaid: Boolean = false
    ) {
        viewModelScope.launch {
            try {
                _createReservationState.value = CreateReservationState.Loading
                val reservation = Reservation(
                    apartmentId = apartmentId,
                    userId = userId,
                    startDate = startDate,
                    endDate = endDate,
                    status = "confirmed",
                    isPaid = isPaid
                )

                val existingReservationsResult = reservationRepository.getReservationsByApartmentId(apartmentId)
                if (existingReservationsResult.isFailure) {
                    throw Exception("Failed to check for overlapping reservations: ${existingReservationsResult.exceptionOrNull()?.message}")
                }
                val existingReservations = existingReservationsResult.getOrThrow()

                val newStart = startDate.toDateMillis()
                val newEnd = endDate.toDateMillis()
                val isOverlapping = existingReservations.any { res ->
                    val resStart = res.startDate.toDateMillis()
                    val resEnd = res.endDate.toDateMillis()
                    !(newEnd < resStart || newStart > resEnd)
                }

                if (isOverlapping) {
                    throw IllegalStateException("Selected dates overlap with an existing reservation")
                }

                val result = reservationRepository.createReservation(reservation)
                if (result.isSuccess) {
                    Log.d("ApartmentViewModel", "Reservation created successfully: ${result.getOrNull()}")
                    loadReservations(userId)
                    loadReservationsForApartment(apartmentId)

                    val apartmentResult = apartmentRepository.getApartmentById(apartmentId)
                    val apartment = apartmentResult.getOrNull()
                    if (apartment != null && apartment.landlordId != userId) {
                        ReservationNotificationManager.sendNotification(
                            context,
                            "New Reservation",
                            "Your apartment (${apartment.name}) has been reserved and paid!"
                        )
                    }

                    ReservationNotificationManager.sendNotification(
                        context,
                        "Reservation Confirmed",
                        "You have successfully reserved and paid for ${apartment?.name ?: "an apartment"} from $startDate to $endDate!"
                    )

                    ReservationNotificationManager.scheduleReminder(context, startDate)

                    _createReservationState.value = CreateReservationState.Success("Reservation created successfully!")
                } else {
                    Log.e("ApartmentViewModel", "Failed to create reservation: ${result.exceptionOrNull()?.message}")
                    _createReservationState.value = CreateReservationState.Error(
                        result.exceptionOrNull()?.message ?: "Failed to create reservation"
                    )
                }
            } catch (e: Exception) {
                Log.e("ApartmentViewModel", "Exception during reservation creation", e)
                _createReservationState.value = CreateReservationState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun cancelReservation(reservationId: String, userId: String) {
        viewModelScope.launch {
            try {
                _cancelReservationState.value = CancelReservationState.Loading

                val reservationResult = reservationRepository.getReservationById(reservationId)
                if (reservationResult.isFailure) {
                    throw Exception("Reservation not found: ${reservationResult.exceptionOrNull()?.message}")
                }
                val reservation = reservationResult.getOrThrow()
                if (reservation.userId != userId) {
                    throw SecurityException("Only the user who made the reservation can cancel it")
                }

                val result = reservationRepository.cancelReservation(reservationId)
                if (result.isSuccess) {
                    Log.d("ApartmentViewModel", "Reservation deleted successfully: $reservationId")
                    loadReservations(userId)
                    loadReservationsForApartment(reservation.apartmentId)
                    loadPastReservations(userId)
                    _cancelReservationState.value = CancelReservationState.Success("Reservation cancelled successfully!")
                } else {
                    Log.e("ApartmentViewModel", "Failed to delete reservation: ${result.exceptionOrNull()?.message}")
                    _cancelReservationState.value = CancelReservationState.Error(
                        result.exceptionOrNull()?.message ?: "Failed to cancel reservation"
                    )
                }
            } catch (e: Exception) {
                Log.e("ApartmentViewModel", "Exception during reservation cancellation", e)
                _cancelReservationState.value = CancelReservationState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun updateReservation(reservationId: String?, updatedReservation: Reservation, context: Context) {
        if (reservationId == null) {
            Log.e("ApartmentViewModel", "Reservation ID is null, cannot update reservation")
            _updateReservationState.value = UpdateReservationState.Error("Reservation ID is null")
            return
        }
        viewModelScope.launch {
            try {
                _updateReservationState.value = UpdateReservationState.Loading

                val existingReservationResult = reservationRepository.getReservationById(reservationId)
                if (existingReservationResult.isFailure) {
                    throw Exception("Reservation not found: ${existingReservationResult.exceptionOrNull()?.message}")
                }
                val existingReservation = existingReservationResult.getOrThrow()
                if (existingReservation.userId != updatedReservation.userId) {
                    throw SecurityException("Only the user who made the reservation can update it")
                }

                val otherReservationsResult = reservationRepository.getReservationsByApartmentId(updatedReservation.apartmentId)
                if (otherReservationsResult.isFailure) {
                    throw Exception("Failed to check for overlapping reservations: ${otherReservationsResult.exceptionOrNull()?.message}")
                }
                val otherReservations = otherReservationsResult.getOrThrow().filter { it.id != reservationId }

                val newStartDate = updatedReservation.startDate.toDateMillis()
                val newEnd = updatedReservation.endDate.toDateMillis()
                val isOverlapping = otherReservations.any { res ->
                    val resStart = res.startDate.toDateMillis()
                    val resEnd = res.endDate.toDateMillis()
                    !(newEnd < resStart || newStartDate > resEnd)
                }

                if (isOverlapping) {
                    throw IllegalStateException("Updated dates overlap with an existing reservation")
                }

                val oldStartDate = existingReservation.startDate.toDateMillis()
                val isDelayed = newStartDate > oldStartDate

                val result = reservationRepository.updateReservation(reservationId, updatedReservation)
                if (result.isSuccess) {
                    Log.d("ApartmentViewModel", "Reservation updated successfully: $reservationId")
                    loadReservations(updatedReservation.userId)
                    loadReservationsForApartment(updatedReservation.apartmentId)

                    val apartmentResult = apartmentRepository.getApartmentById(updatedReservation.apartmentId)
                    val apartment = apartmentResult.getOrNull()

                    if (isDelayed) {
                        ReservationNotificationManager.sendNotification(
                            context,
                            "Reservation Delayed",
                            "Your reservation for ${apartment?.name ?: "an apartment"} has been delayed to ${updatedReservation.startDate}."
                        )
                    } else {
                        ReservationNotificationManager.sendNotification(
                            context,
                            "Reservation Updated",
                            "Your reservation for ${apartment?.name ?: "an apartment"} has been updated to ${updatedReservation.startDate} - ${updatedReservation.endDate}."
                        )
                    }

                    ReservationNotificationManager.scheduleReminder(context, updatedReservation.startDate)

                    _updateReservationState.value = UpdateReservationState.Success("Reservation updated successfully!")
                } else {
                    Log.e("ApartmentViewModel", "Failed to update reservation: ${result.exceptionOrNull()?.message}")
                    _updateReservationState.value = UpdateReservationState.Error(
                        result.exceptionOrNull()?.message ?: "Failed to update reservation"
                    )
                }
            } catch (e: Exception) {
                Log.e("ApartmentViewModel", "Exception during reservation update", e)
                _updateReservationState.value = UpdateReservationState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun getCurrentDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    private fun String.toDateMillis(): Long {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.parse(this)?.time ?: 0L
    }

    fun loadReviewsForApartment(apartmentId: String) {
        viewModelScope.launch {
            try {
                val result = db.collection("reviews")
                    .whereEqualTo("apartmentId", apartmentId)
                    .get()
                    .await()
                val reviewList = result.map { document ->
                    document.toObject(Review::class.java).copy(id = document.id)
                }
                Log.d("ApartmentViewModel", "Loaded reviews for apartment $apartmentId: $reviewList")
                _reviews.value = reviewList
            } catch (e: Exception) {
                Log.e("ApartmentViewModel", "Error loading reviews", e)
            }
        }
    }

    fun addReview(apartmentId: String, userId: String, rating: Int, comment: String, context: Context) {
        viewModelScope.launch {
            try {
                val pastReservationsResult = reservationRepository.getPastReservationsByUserId(userId, getCurrentDate())
                if (pastReservationsResult.isFailure) {
                    throw Exception("Failed to fetch past reservations: ${pastReservationsResult.exceptionOrNull()?.message}")
                }
                val pastReservations = pastReservationsResult.getOrThrow()
                val validReservation = pastReservations.find { reservation ->
                    reservation.apartmentId == apartmentId && reservation.endDate.toDateMillis() < System.currentTimeMillis()
                }

                if (validReservation == null) {
                    throw SecurityException("You can only review apartments you have reserved and stayed at")
                }

                val existingReview = db.collection("reviews")
                    .whereEqualTo("apartmentId", apartmentId)
                    .whereEqualTo("userId", userId)
                    .get()
                    .await()
                if (!existingReview.isEmpty) {
                    throw IllegalStateException("You have already reviewed this apartment")
                }

                val review = hashMapOf(
                    "apartmentId" to apartmentId,
                    "userId" to userId,
                    "rating" to rating,
                    "comment" to comment,
                    "timestamp" to System.currentTimeMillis()
                )
                val documentReference = db.collection("reviews").add(review).await()
                Log.d("ApartmentViewModel", "Review added with ID: ${documentReference.id}")
                loadReviewsForApartment(apartmentId)
                ReservationNotificationManager.sendNotification(
                    context,
                    "Review Submitted",
                    "Thank you for your review of the apartment!"
                )
            } catch (e: Exception) {
                Log.e("ApartmentViewModel", "Error adding review: ${e.message}", e)
                ReservationNotificationManager.sendNotification(
                    context,
                    "Review Failed",
                    "Failed to submit your review: ${e.message}"
                )
            }
        }
    }

    sealed class AddApartmentState {
        object Idle : AddApartmentState()
        object Loading : AddApartmentState()
        data class Success(val message: String) : AddApartmentState()
        data class Error(val message: String) : AddApartmentState()
    }

    sealed class UpdateApartmentState {
        object Idle : UpdateApartmentState()
        object Loading : UpdateApartmentState()
        data class Success(val message: String) : UpdateApartmentState()
        data class Error(val message: String) : UpdateApartmentState()
    }

    sealed class DeleteApartmentState {
        object Idle : DeleteApartmentState()
        object Loading : DeleteApartmentState()
        data class Success(val message: String) : DeleteApartmentState()
        data class Error(val message: String) : DeleteApartmentState()
    }

    sealed class CreateReservationState {
        object Idle : CreateReservationState()
        object Loading : CreateReservationState()
        data class Success(val message: String) : CreateReservationState()
        data class Error(val message: String) : CreateReservationState()
    }

    sealed class CancelReservationState {
        object Idle : CancelReservationState()
        object Loading : CancelReservationState()
        data class Success(val message: String) : CancelReservationState()
        data class Error(val message: String) : CancelReservationState()
    }

    sealed class UpdateReservationState {
        object Idle : UpdateReservationState()
        object Loading : UpdateReservationState()
        data class Success(val message: String) : UpdateReservationState()
        data class Error(val message: String) : UpdateReservationState()
    }
}