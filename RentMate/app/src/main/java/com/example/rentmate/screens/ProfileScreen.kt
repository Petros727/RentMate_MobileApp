package com.example.rentmate.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.rentmate.viewModels.ApartmentViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    navController: NavController,
    onEditReservation: (String) -> Unit,
    onReviewReservation: (String, String) -> Unit,
    apartmentViewModel: ApartmentViewModel
) {
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    val reservations by apartmentViewModel.reservations.collectAsState()
    val pastReservations by apartmentViewModel.pastReservations.collectAsState()
    val landlordApartments by apartmentViewModel.landlordApartments.collectAsState()
    val cancelReservationState by apartmentViewModel.cancelReservationState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(userId) {
        apartmentViewModel.loadReservations(userId)
        apartmentViewModel.loadPastReservations(userId)
        if (apartmentViewModel.userRole.value == "landlord") {
            apartmentViewModel.loadLandlordApartments(userId)
        }
    }

    LaunchedEffect(cancelReservationState) {
        when (cancelReservationState) {
            is ApartmentViewModel.CancelReservationState.Success -> {
                snackbarHostState.showSnackbar("Reservation cancelled successfully!")
            }
            is ApartmentViewModel.CancelReservationState.Error -> {
                snackbarHostState.showSnackbar(
                    (cancelReservationState as ApartmentViewModel.CancelReservationState.Error).message
                )
            }
            else -> {}
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Button(
                    onClick = { navController.navigate("searchResults") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                ) {
                    Text("Back", color = Color.White)
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Profile",
                    fontSize = 24.sp,
                    color = Color.Black
                )
            }

            if (apartmentViewModel.userRole.value == "landlord" && landlordApartments.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Your Apartments",
                        fontSize = 20.sp,
                        color = Color.Black,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(landlordApartments) { apartment ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = apartment.name,
                                fontSize = 16.sp,
                                color = Color.Black
                            )
                            Text(
                                text = "Address: ${apartment.address}",
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                            Text(
                                text = "Price: ${apartment.price} €/night",
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Button(
                                    onClick = { navController.navigate("editApartment/${apartment.id}") },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4682B4))
                                ) {
                                    Text("Edit", color = Color.White)
                                }
                                Button(
                                    onClick = {
                                        apartmentViewModel.deleteApartment(apartment.id, apartment.photoUrls)
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Apartment deleted successfully!")
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                                ) {
                                    Text("Delete", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Your Reservations",
                    fontSize = 20.sp,
                    color = Color.Black,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            items(reservations) { reservation ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Apartment ID: ${reservation.apartmentId}",
                            fontSize = 16.sp,
                            color = Color.Black
                        )
                        Text(
                            text = "Dates: ${reservation.startDate} - ${reservation.endDate}",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                        Text(
                            text = "Payment Status: ${if (reservation.isPaid) "Paid" else "Not Paid"}",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Button(
                                onClick = { onEditReservation(reservation.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4682B4))
                            ) {
                                Text("Edit", color = Color.White)
                            }
                            Button(
                                onClick = {
                                    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@Button
                                    apartmentViewModel.cancelReservation(reservation.id, userId)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                enabled = cancelReservationState != ApartmentViewModel.CancelReservationState.Loading
                            ) {
                                Text("Cancel", color = Color.White)
                            }
                        }
                    }
                }
            }

            if (pastReservations.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Past Reservations",
                        fontSize = 20.sp,
                        color = Color.Black,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(pastReservations) { reservation ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Apartment ID: ${reservation.apartmentId}",
                                fontSize = 16.sp,
                                color = Color.Black
                            )
                            Text(
                                text = "Dates: ${reservation.startDate} - ${reservation.endDate}",
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                            Text(
                                text = "Payment Status: Paid",
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { onReviewReservation(reservation.id, reservation.apartmentId) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4682B4)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Add Review", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}