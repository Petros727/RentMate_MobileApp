package com.example.rentmate.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.rentmate.viewModels.ApartmentViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat

@Composable
fun ReviewScreen(
    reservationId: String,
    apartmentId: String,
    navController: NavController,
    viewModel: ApartmentViewModel
) {
    val context = LocalContext.current
    val pastReservations by viewModel.pastReservations.collectAsState()
    val reservation = pastReservations.find { it.id == reservationId }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var canReview by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
        viewModel.loadPastReservations(userId)
    }

    LaunchedEffect(reservation) {
        if (reservation == null) {
            errorMessage = "Reservation not found"
            canReview = false
        } else if (reservation.apartmentId != apartmentId) {
            errorMessage = "This reservation is not for the specified apartment"
            canReview = false
        } else {
            val endDateMillis = reservation.endDate.toDateMillis()
            if (endDateMillis > System.currentTimeMillis()) {
                errorMessage = "You can only review after your stay has ended"
                canReview = false
            } else {
                val existingReviewSnapshot = Firebase.firestore.collection("reviews")
                    .whereEqualTo("apartmentId", apartmentId)
                    .whereEqualTo("userId", FirebaseAuth.getInstance().currentUser?.uid)
                    .get()
                    .await()
                if (!existingReviewSnapshot.isEmpty) {
                    errorMessage = "You have already reviewed this apartment"
                    canReview = false
                } else {
                    canReview = true
                    errorMessage = null
                }
            }
        }
    }

    if (reservation == null && errorMessage == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    var rating by remember { mutableIntStateOf(0) }
    var comment by remember { mutableStateOf("") }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = { navController.navigate("profile") },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
            ) {
                Text("Back", color = Color.White)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Leave a Review",
                fontSize = 24.sp,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(16.dp))

            errorMessage?.let {
                Text(
                    text = it,
                    color = Color.Red,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            if (canReview) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    (1..5).forEach { star ->
                        TextButton(
                            onClick = { rating = star },
                            modifier = Modifier.padding(4.dp)
                        ) {
                            Text(
                                text = if (star <= rating) "★" else "☆",
                                fontSize = 24.sp,
                                color = if (star <= rating) Color(0xFFFFD700) else Color.Gray
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Comment") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            Button(
                onClick = {
                    if (!canReview) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(errorMessage ?: "Cannot submit review")
                        }
                        return@Button
                    }
                    if (rating == 0) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Please select a rating")
                        }
                        return@Button
                    }
                    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@Button
                    viewModel.addReview(
                        apartmentId = apartmentId,
                        userId = userId,
                        rating = rating,
                        comment = comment,
                        context = context
                    )
                    navController.navigate("profile") {
                        popUpTo("review/$reservationId/$apartmentId") { inclusive = true }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4682B4)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = canReview
            ) {
                Text("Submit Review", color = Color.White, fontSize = 16.sp)
            }
        }
    }
}

private fun String.toDateMillis(): Long {
    val sdf = SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
    return sdf.parse(this)?.time ?: 0L
}