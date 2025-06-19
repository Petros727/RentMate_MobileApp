package com.example.rentmate.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.example.rentmate.models.Reservation
import com.example.rentmate.viewModels.ApartmentViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EditReservationScreen(
    reservationId: String,
    navController: NavController,
    viewModel: ApartmentViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val reservations by viewModel.reservations.collectAsState()
    val updateReservationState by viewModel.updateReservationState.collectAsState()

    val reservation = reservations.find { it.id == reservationId }

    var startDateInput by remember { mutableStateOf("") }
    var endDateInput by remember { mutableStateOf("") }
    var statusInput by remember { mutableStateOf("") }

    LaunchedEffect(reservation) {
        reservation?.let {
            startDateInput = it.startDate
            endDateInput = it.endDate
            statusInput = it.status
        }
    }

    LaunchedEffect(Unit) {
        val userId = Firebase.auth.currentUser?.uid ?: return@LaunchedEffect
        viewModel.loadReservations(userId)
    }

    LaunchedEffect(updateReservationState) {
        when (val state = updateReservationState) {
            is ApartmentViewModel.UpdateReservationState.Success -> {
                snackbarHostState.showSnackbar(state.message)
                navController.navigate("profile") {
                    popUpTo("editReservation/$reservationId") { inclusive = true }
                }
            }
            is ApartmentViewModel.UpdateReservationState.Error -> {
                snackbarHostState.showSnackbar("Error: ${state.message}")
            }
            else -> Unit
        }
    }

    if (reservation == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

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
                text = "Edit Reservation",
                fontSize = 24.sp,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = startDateInput,
                onValueChange = { startDateInput = it },
                label = { Text("Start Date (yyyy-MM-dd)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = endDateInput,
                onValueChange = { endDateInput = it },
                label = { Text("End Date (yyyy-MM-dd)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            val statusOptions = listOf("confirmed", "cancelled")
            var expanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = statusInput,
                    onValueChange = { /* Read-only, koristi se dropdown */ },
                    label = { Text("Status") },
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { expanded = true }) {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select Status"
                            )
                        }
                    }
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    statusOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                statusInput = option
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    sdf.isLenient = false
                    val currentDate = sdf.parse(sdf.format(Date()))?.time ?: 0L
                    val startDate = try {
                        sdf.parse(startDateInput)?.time ?: 0L
                    } catch (e: Exception) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Invalid start date format")
                        }
                        return@Button
                    }
                    val endDate = try {
                        sdf.parse(endDateInput)?.time ?: 0L
                    } catch (e: Exception) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Invalid end date format")
                        }
                        return@Button
                    }

                    if (startDate < currentDate) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Start date cannot be in the past")
                        }
                        return@Button
                    }
                    if (endDate <= startDate) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("End date must be after start date")
                        }
                        return@Button
                    }
                    if (statusInput !in statusOptions) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Invalid status")
                        }
                        return@Button
                    }

                    val updatedReservation = Reservation(
                        id = reservation.id,
                        apartmentId = reservation.apartmentId,
                        userId = reservation.userId,
                        startDate = startDateInput,
                        endDate = endDateInput,
                        status = statusInput
                    )

                    Log.d("EditReservationScreen", "Updating reservation: $updatedReservation")
                    viewModel.updateReservation(reservationId, updatedReservation, context)
                },
                enabled = updateReservationState !is ApartmentViewModel.UpdateReservationState.Loading,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4682B4)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Save Changes", color = Color.White, fontSize = 16.sp)
            }
        }
    }
}