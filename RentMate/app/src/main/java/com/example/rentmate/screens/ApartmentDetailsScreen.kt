package com.example.rentmate.screens

import android.location.Geocoder
import android.util.Log
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DateRangePickerState
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.rentmate.models.Review
import com.example.rentmate.viewModels.ApartmentViewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApartmentDetailsScreen(
    apartmentId: String,
    navController: NavController,
    viewModel: ApartmentViewModel
) {
    val context = LocalContext.current
    val selectedApartment by viewModel.selectedApartment.collectAsState()
    val reviews by viewModel.reviews.collectAsState()
    val pastReservations by viewModel.pastReservations.collectAsState()
    val createReservationState by viewModel.createReservationState.collectAsState()
    val deleteApartmentState by viewModel.deleteApartmentState.collectAsState()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val reservations by viewModel.reservationsForApartment.collectAsState()
    val reservedDates = remember(reservations) {
        reservations.flatMap {
            val start = it.startDate.toDateMillis()
            val end = it.endDate.toDateMillis()
            generateDateRange(start, end)
        }.toSet()
    }
    val todayMillis = remember { System.currentTimeMillis().startOfDay() }

    val selectableDates = remember(reservedDates, todayMillis) {
        object : androidx.compose.material3.SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val dateStartOfDay = utcTimeMillis.startOfDay()
                return dateStartOfDay >= todayMillis && dateStartOfDay !in reservedDates
            }
        }
    }

    val dateRangePickerState = rememberDateRangePickerState(
        selectableDates = selectableDates
    )
    var totalPrice by remember { mutableStateOf(0.0) }

    LaunchedEffect(apartmentId, currentUserId) {
        viewModel.loadApartmentById(apartmentId)
        viewModel.loadReservationsForApartment(apartmentId)
        viewModel.loadReviewsForApartment(apartmentId)
        if (currentUserId.isNotEmpty()) {
            viewModel.loadPastReservations(currentUserId)
        }
    }

    LaunchedEffect(createReservationState, deleteApartmentState) {
        when {
            createReservationState is ApartmentViewModel.CreateReservationState.Success -> {
                snackbarHostState.showSnackbar("Reservation created successfully!")
            }
            createReservationState is ApartmentViewModel.CreateReservationState.Error -> {
                snackbarHostState.showSnackbar("Error: ${(createReservationState as ApartmentViewModel.CreateReservationState.Error).message}")
            }
            deleteApartmentState is ApartmentViewModel.DeleteApartmentState.Success -> {
                snackbarHostState.showSnackbar("Apartment deleted successfully!")
                navController.navigate("searchResults") { popUpTo("apartmentDetails/$apartmentId") { inclusive = true } }
            }
            deleteApartmentState is ApartmentViewModel.DeleteApartmentState.Error -> {
                snackbarHostState.showSnackbar("Error: ${(deleteApartmentState as ApartmentViewModel.DeleteApartmentState.Error).message}")
            }
        }
    }

    val apartment = selectedApartment ?: return
    val geocoder = Geocoder(context, Locale.getDefault())
    val location by remember(apartment.address, apartmentId) {
        mutableStateOf(
            try {
                if (apartment.address.isNotBlank()) {
                    val addresses = geocoder.getFromLocationName(apartment.address, 1)
                    if (!addresses.isNullOrEmpty()) {
                        LatLng(addresses.first().latitude, addresses.first().longitude)
                    } else {
                        LatLng(45.5511, 18.6934)
                    }
                } else {
                    LatLng(45.5511, 18.6934)
                }
            } catch (e: Exception) {
                Log.e("ApartmentDetails", "Geocoding failed: ${e.message}", e)
                LatLng(45.5511, 18.6934)
            }
        )
    }
    val cameraPositionState = rememberCameraPositionState()

    LaunchedEffect(apartmentId, location) {
        cameraPositionState.position = CameraPosition.fromLatLngZoom(location, 12f)
    }

    LaunchedEffect(dateRangePickerState.selectedStartDateMillis, dateRangePickerState.selectedEndDateMillis) {
        val startMillis = dateRangePickerState.selectedStartDateMillis
        val endMillis = dateRangePickerState.selectedEndDateMillis
        totalPrice = if (startMillis != null && endMillis != null) {
            val days = ((endMillis - startMillis) / (1000 * 60 * 60 * 24)).toInt()
            apartment.price * days
        } else 0.0
    }

    var showReviewsDialog by remember { mutableStateOf(false) }
    val averageRating by remember(reviews) {
        derivedStateOf {
            if (reviews.isNotEmpty()) reviews.map { it.rating.toFloat() }.average().toFloat() else 0f
        }
    }

    val hasPastReservation by remember(pastReservations, apartmentId, currentUserId) {
        derivedStateOf {
            currentUserId.isNotEmpty() && pastReservations.any { reservation ->
                reservation.apartmentId == apartmentId && reservation.userId == currentUserId && reservation.endDate.toDateMillis() < System.currentTimeMillis()
            }
        }
    }

    var showAddReviewDialog by remember { mutableStateOf(false) }
    var ratingInput by remember { mutableStateOf(1) }
    var commentInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        apartment.name,
                        fontSize = 22.sp,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigate("searchResults") }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {navController.navigate("profile")}) {
                        Icon(
                            Icons.Rounded.AccountCircle,
                            contentDescription = "Profile",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E88E5),
                    titleContentColor = Color.White
                ),
                modifier = Modifier.shadow(4.dp)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFFF5F7FA)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .shadow(8.dp, RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    val images = apartment.photoUrls.ifEmpty { listOf("https://via.placeholder.com/280") }
                    val pagerState = rememberPagerState(pageCount = { images.size })
                    Column {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) { page ->
                            AsyncImage(
                                model = images[page],
                                contentDescription = "Apartment Image ${page + 1}",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(20.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            images.forEachIndexed { index, _ ->
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (index == pagerState.currentPage) Color(0xFF1E88E5)
                                            else Color(0xFFD0D5DD)
                                        )
                                        .padding(2.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(animationSpec = tween(300))
                        .shadow(8.dp, RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = apartment.name,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1A252F)
                        )
                        Text(
                            text = apartment.address,
                            fontSize = 16.sp,
                            color = Color(0xFF6B7280),
                            fontWeight = FontWeight.Medium
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${apartment.price} € / night",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF388E3C)
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Rating",
                                    tint = Color(0xFFFFD700),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", averageRating.absoluteValue)}/5",
                                    fontSize = 16.sp,
                                    color = Color(0xFF1E88E5),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        Divider(color = Color(0xFFE5E7EB), thickness = 1.dp)
                        Text(
                            text = apartment.description.ifEmpty { "No description available" },
                            fontSize = 16.sp,
                            color = Color(0xFF374151),
                            lineHeight = 24.sp
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(8.dp, RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Features",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1A252F)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            FeatureItem(label = "WiFi", value = apartment.features.hasWifi)
                            FeatureItem(label = "Parking", value = apartment.features.hasParking)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            FeatureItem(label = "Rooms", value = apartment.features.numberOfRooms)
                            FeatureItem(label = "Bathrooms", value = apartment.features.numberOfBathrooms)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            FeatureItem(label = "AC", value = apartment.features.hasAirConditioning)
                            FeatureItem(label = "Kitchen", value = apartment.features.hasKitchen)
                        }
                    }
                }
            }

            if (hasPastReservation) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showReviewsDialog = true }
                            .shadow(8.dp, RoundedCornerShape(20.dp)),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Reviews (${reviews.size})",
                                    fontSize = 20.sp,
                                    color = Color(0xFF1E88E5),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Rating",
                                    tint = Color(0xFFFFD700),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Button(
                                onClick = { showAddReviewDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1E88E5),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                            ) {
                                Text(
                                    "Add Review",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                item {
                    if (showReviewsDialog) {
                        Dialog(onDismissRequest = { showReviewsDialog = false }) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(450.dp)
                                    .clip(RoundedCornerShape(20.dp)),
                                color = Color.White,
                                shadowElevation = 8.dp
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(20.dp)
                                ) {
                                    Text(
                                        text = "Reviews",
                                        fontSize = 26.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1A252F),
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )
                                    if (reviews.isEmpty()) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "No reviews yet.",
                                                fontSize = 16.sp,
                                                color = Color(0xFF6B7280),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    } else {
                                        LazyColumn {
                                            items(reviews.size) { index ->
                                                ReviewItem(review = reviews[index])
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    if (showAddReviewDialog) {
                        Dialog(onDismissRequest = { showAddReviewDialog = false }) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp)),
                                color = Color.White,
                                shadowElevation = 8.dp
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Text(
                                        text = "Add Review",
                                        fontSize = 26.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1A252F)
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        (1..5).forEach { star ->
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = "Star $star",
                                                tint = if (star <= ratingInput) Color(0xFFFFD700) else Color(0xFFD0D5DD),
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clickable { ratingInput = star }
                                            )
                                        }
                                    }
                                    OutlinedTextField(
                                        value = commentInput,
                                        onValueChange = { commentInput = it },
                                        label = { Text("Your Comment") },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(140.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF1E88E5),
                                            unfocusedBorderColor = Color(0xFFD0D5DD)
                                        )
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(onClick = { showAddReviewDialog = false }) {
                                            Text(
                                                "Cancel",
                                                color = Color(0xFFEF4444),
                                                fontSize = 16.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Button(
                                            onClick = {
                                                if (ratingInput > 0) {
                                                    viewModel.addReview(
                                                        apartmentId = apartmentId,
                                                        userId = currentUserId,
                                                        rating = ratingInput,
                                                        comment = commentInput,
                                                        context = context
                                                    )
                                                    showAddReviewDialog = false
                                                    ratingInput = 1
                                                    commentInput = ""
                                                } else {
                                                    coroutineScope.launch {
                                                        snackbarHostState.showSnackbar("Please select a rating")
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF1E88E5),
                                                contentColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                                        ) {
                                            Text(
                                                "Submit",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .shadow(8.dp, RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    GoogleMap(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(20.dp)),
                        cameraPositionState = cameraPositionState,
                        properties = MapProperties(isMyLocationEnabled = false),
                        uiSettings = MapUiSettings(zoomControlsEnabled = true)
                    ) {
                        Marker(
                            state = MarkerState(position = location),
                            title = apartment.name,
                            snippet = apartment.address
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(6.dp, RoundedCornerShape(24.dp)),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFF42A5F5),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Select Stay Dates",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0D47A1)
                            )
                        }

                        Divider(color = Color(0xFFDEE4EA), thickness = 1.dp)

                        CustomDateRangePicker(
                            state = dateRangePickerState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(400.dp)
                        )

                        Text(
                            text = "Greyed out dates (past or reserved) cannot be selected.",
                            fontSize = 12.sp,
                            color = Color(0xFF9CA3AF),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )

                        TextButton(
                            onClick = { dateRangePickerState.setSelection(null, null) },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Clear Selection", color = Color(0xFFEF5350))
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(8.dp, RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Book Your Stay",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1A252F)
                        )
                        Text(
                            text = "Total Price: ${String.format(Locale.US, "%.2f", totalPrice)} €",
                            fontSize = 18.sp,
                            color = Color(0xFF388E3C),
                            fontWeight = FontWeight.SemiBold
                        )
                        Button(
                            onClick = {
                                val startDate = dateRangePickerState.selectedStartDateMillis?.toDateString()
                                val endDate = dateRangePickerState.selectedEndDateMillis?.toDateString()
                                val startMillis = dateRangePickerState.selectedStartDateMillis
                                val endMillis = dateRangePickerState.selectedEndDateMillis
                                val today = System.currentTimeMillis().startOfDay()

                                if (startDate == null || endDate == null) {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Please select a date range.")
                                    }
                                    return@Button
                                }

                                startMillis?.let {
                                    if (endMillis!! <= it) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Check-out must be after check-in.")
                                        }
                                        return@Button
                                    }
                                }

                                if (startMillis != null) {
                                    if (startMillis < today || endMillis!! < today) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Dates cannot be in the past.")
                                        }
                                        return@Button
                                    }
                                }

                                navController.navigate(
                                    "payment/$apartmentId/$startDate/$endDate/$totalPrice"
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1E88E5),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                            enabled = createReservationState !is ApartmentViewModel.CreateReservationState.Loading
                        ) {
                            Text(
                                "Proceed to Payment",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FeatureItem(label: String, value: Any) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 10.dp)
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color(0xFF6B7280),
            fontWeight = FontWeight.Medium
        )
        Text(
            text = when (value) {
                is Boolean -> if (value) "Yes" else "No"
                else -> value.toString()
            },
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF374151)
        )
    }
}

@Composable
fun ReviewItem(review: Review) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(5) { index ->
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = if (index < review.rating) Color(0xFFFFD700) else Color(0xFFD0D5DD),
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${review.rating}/5",
                fontSize = 14.sp,
                color = Color(0xFF6B7280)
            )
        }
        Text(
            text = review.comment.ifEmpty { "No comment provided" },
            fontSize = 16.sp,
            color = Color(0xFF374151),
            modifier = Modifier.padding(top = 8.dp),
            lineHeight = 22.sp
        )
        Text(
            text = "Posted on: ${SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(review.timestamp))}",
            fontSize = 12.sp,
            color = Color(0xFF9CA3AF),
            modifier = Modifier.padding(top = 6.dp)
        )
        Divider(
            color = Color(0xFFE5E7EB),
            thickness = 1.dp,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

fun Long.startOfDay(): Long {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = this@startOfDay
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return calendar.timeInMillis
}

private fun String.toDateMillis(): Long {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    return try {
        sdf.parse(this)?.time ?: 0L
    } catch (e: Exception) {
        0L
    }
}

private fun Long.toDateString(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    return sdf.format(Date(this))
}

private fun generateDateRange(startMillis: Long, endMillis: Long): List<Long> {
    val dates = mutableListOf<Long>()
    var currentMillis = startMillis.startOfDay()
    val endMillisDay = endMillis.startOfDay()
    while (currentMillis <= endMillisDay) {
        dates.add(currentMillis)
        currentMillis += 24 * 60 * 60 * 1000
    }
    return dates
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomDateRangePicker(
    state: DateRangePickerState,
    modifier: Modifier = Modifier
) {
    val locale = Locale.US
    val sdf = SimpleDateFormat("dd MMM yyyy", locale)
    val calendar = Calendar.getInstance(locale).apply {
        firstDayOfWeek = Calendar.SUNDAY
    }

    DateRangePicker(
        state = state,
        modifier = modifier,
        title = null,
        headline = {
            val start = state.selectedStartDateMillis?.let { sdf.format(Date(it)) } ?: "-"
            val end = state.selectedEndDateMillis?.let { sdf.format(Date(it)) } ?: "-"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Check-in: $start",
                    fontSize = 16.sp,
                    color = Color(0xFF1976D2),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Check-out: $end",
                    fontSize = 16.sp,
                    color = Color(0xFF1976D2),
                    fontWeight = FontWeight.Medium
                )
            }
        },
        colors = DatePickerDefaults.colors(
            containerColor = Color.White,
            titleContentColor = Color(0xFF0D47A1),
            headlineContentColor = Color.Black,
            weekdayContentColor = Color(0xFF546E7A),
            subheadContentColor = Color.Black,
            selectedDayContainerColor = Color(0xFF42A5F5),
            selectedDayContentColor = Color.White,
            dayContentColor = Color.Black,
            disabledDayContentColor = Color(0xFFCFD8DC),
            todayContentColor = Color.Black,
            todayDateBorderColor = Color(0xFF90CAF9)
        )
    )
}