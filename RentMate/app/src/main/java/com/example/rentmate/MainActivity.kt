package com.example.rentmate

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.rentmate.screens.AddApartmentScreen
import com.example.rentmate.screens.ApartmentDetailsScreen
import com.example.rentmate.screens.EditApartmentScreen
import com.example.rentmate.screens.EditReservationScreen
import com.example.rentmate.screens.PaymentScreen
import com.example.rentmate.screens.ProfileScreen
import com.example.rentmate.screens.ReviewScreen
import com.example.rentmate.screens.SearchResultsScreen
import com.example.rentmate.screens.SignInScreen
import com.example.rentmate.ui.theme.RentMateTheme
import com.example.rentmate.utils.ReservationNotificationManager
import com.example.rentmate.viewModels.ApartmentViewModel
import com.google.firebase.FirebaseApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "onCreate called at ${System.currentTimeMillis()}")

        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
            Log.d("MainActivity", "Firebase initialized")
        } else {
            Log.d("MainActivity", "Firebase already initialized")
        }

        ReservationNotificationManager.createNotificationChannel(this)
        requestPermissions()

        setContent {
            RentMateTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RentMateApp(context = this@MainActivity)
                }
            }
        }
    }

    private fun requestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.SCHEDULE_EXACT_ALARM) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(android.Manifest.permission.SCHEDULE_EXACT_ALARM)
        }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(android.Manifest.permission.CAMERA)
        }
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), 1)
        }
    }
}

@Composable
fun RentMateApp(context: Context) {
    val navController = rememberNavController()
    Log.d("RentMateApp", "NavHost initialized at ${System.currentTimeMillis()}")

    val viewModelFactory = object : ViewModelProvider.Factory {
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return ApartmentViewModel() as T
        }
    }
    val apartmentViewModel: ApartmentViewModel = viewModel(factory = viewModelFactory)

    NavHost(navController = navController, startDestination = "signIn") {
        composable("signIn") {
            Log.d("RentMateApp", "Rendering SignInScreen")
            SignInScreen(
                onSignInSuccess = { userId ->
                    val startTime = System.currentTimeMillis()
                    Log.d("RentMateApp", "Navigating to searchResults for userId: $userId at $startTime")
                    navController.navigate("searchResults") {
                        popUpTo("signIn") { inclusive = true }
                    }
                    Log.d("RentMateApp", "Navigation to searchResults completed, time taken: ${System.currentTimeMillis() - startTime}ms")
                },
                onNavigateToSignUp = {
                    Log.d("RentMateApp", "Navigating to signUp")
                    navController.navigate("signUp")
                }
            )
        }

        composable("signUp") {
            Log.d("RentMateApp", "Rendering SignUpScreen")
            SignUpScreen(
                onSignUpSuccess = { userId ->
                    Log.d("RentMateApp", "SignUp successful for userId: $userId")
                    navController.navigate("searchResults") {
                        popUpTo("signUp") { inclusive = true }
                    }
                },
                onBackToSignIn = {
                    Log.d("RentMateApp", "Navigating back to signIn")
                    navController.navigate("signIn") {
                        popUpTo("signUp") { inclusive = true }
                    }
                }
            )
        }

        composable("searchResults") {
            Log.d("RentMateApp", "Rendering SearchResultsScreen")
            SearchResultsScreen(
                onNavigateToAddApartment = {
                    Log.d("RentMateApp", "Navigating to addApartment")
                    navController.navigate("addApartment")
                },
                onNavigateToApartmentDetails = { apartmentId ->
                    Log.d("RentMateApp", "Navigating to apartmentDetails for ID: $apartmentId")
                    navController.navigate("apartmentDetails/$apartmentId")
                },
                onNavigateToProfile = {
                    Log.d("RentMateApp", "Navigating to profile")
                    navController.navigate("profile")
                },
                isSignInSuccessful = navController.previousBackStackEntry?.destination?.route == "signIn",
                viewModel = apartmentViewModel
            )
        }

        composable("addApartment") {
            Log.d("RentMateApp", "Rendering AddApartmentScreen")
            AddApartmentScreen(
                navController = navController,
                viewModel = apartmentViewModel
            )
        }

        composable("apartmentDetails/{apartmentId}") { backStackEntry ->
            Log.d("RentMateApp", "Rendering ApartmentDetailsScreen for ID: ${backStackEntry.arguments?.getString("apartmentId")}")
            ApartmentDetailsScreen(
                apartmentId = backStackEntry.arguments?.getString("apartmentId") ?: "",
                navController = navController,
                viewModel = apartmentViewModel
            )
        }

        composable("profile") {
            Log.d("RentMateApp", "Rendering ProfileScreen")
            ProfileScreen(
                navController = navController,
                onEditReservation = { reservationId ->
                    Log.d("RentMateApp", "Navigating to editReservation for ID: $reservationId")
                    navController.navigate("editReservation/$reservationId")
                },
                onReviewReservation = { reservationId, apartmentId ->
                    Log.d("RentMateApp", "Navigating to review for reservation ID: $reservationId")
                    navController.navigate("review/$reservationId/$apartmentId")
                },
                apartmentViewModel = apartmentViewModel
            )
        }

        composable("editReservation/{reservationId}") { backStackEntry ->
            Log.d("RentMateApp", "Rendering EditReservationScreen for ID: ${backStackEntry.arguments?.getString("reservationId")}")
            EditReservationScreen(
                reservationId = backStackEntry.arguments?.getString("reservationId") ?: "",
                navController = navController,
                viewModel = apartmentViewModel
            )
        }

        composable("editApartment/{apartmentId}") { backStackEntry ->
            Log.d("RentMateApp", "Rendering EditApartmentScreen for ID: ${backStackEntry.arguments?.getString("apartmentId")}")
            EditApartmentScreen(
                apartmentId = backStackEntry.arguments?.getString("apartmentId") ?: "",
                navController = navController,
                viewModel = apartmentViewModel
            )
        }

        composable("review/{reservationId}/{apartmentId}") { backStackEntry ->
            Log.d("RentMateApp", "Rendering ReviewScreen for reservation ID: ${backStackEntry.arguments?.getString("reservationId")}")
            ReviewScreen(
                reservationId = backStackEntry.arguments?.getString("reservationId") ?: "",
                apartmentId = backStackEntry.arguments?.getString("apartmentId") ?: "",
                navController = navController,
                viewModel = apartmentViewModel
            )
        }

        composable("payment/{apartmentId}/{startDate}/{endDate}/{totalPrice}") { backStackEntry ->
            Log.d("RentMateApp", "Rendering PaymentScreen for apartment ID: ${backStackEntry.arguments?.getString("apartmentId")}")
            PaymentScreen(
                apartmentId = backStackEntry.arguments?.getString("apartmentId") ?: "",
                startDate = backStackEntry.arguments?.getString("startDate") ?: "",
                endDate = backStackEntry.arguments?.getString("endDate") ?: "",
                totalPrice = backStackEntry.arguments?.getString("totalPrice")?.toDoubleOrNull() ?: 0.0,
                navController = navController,
                viewModel = apartmentViewModel
            )
        }
    }
}