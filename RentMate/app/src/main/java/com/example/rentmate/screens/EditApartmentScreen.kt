package com.example.rentmate.screens

import android.Manifest
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.rentmate.models.ApartmentFeatures
import com.example.rentmate.viewModels.ApartmentViewModel
import kotlinx.coroutines.launch

@Composable
fun EditApartmentScreen(
    apartmentId: String,
    navController: NavController,
    viewModel: ApartmentViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val selectedApartment by viewModel.selectedApartment.collectAsState()
    val updateApartmentState by viewModel.updateApartmentState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(apartmentId) {
        viewModel.loadApartmentById(apartmentId)
    }

    LaunchedEffect(updateApartmentState) {
        when (val state = updateApartmentState) {
            is ApartmentViewModel.UpdateApartmentState.Success -> {
                snackbarHostState.showSnackbar(state.message)
                navController.navigate("profile") {
                    popUpTo("editApartment/$apartmentId") { inclusive = true }
                }
            }
            is ApartmentViewModel.UpdateApartmentState.Error -> {
                snackbarHostState.showSnackbar("Error: ${state.message}")
            }
            else -> Unit
        }
    }

    var nameInput by remember { mutableStateOf("") }
    var addressInput by remember { mutableStateOf("") }
    var priceInput by remember { mutableStateOf("") }
    var descriptionInput by remember { mutableStateOf("") }
    var hasWifi by remember { mutableStateOf(false) }
    var hasParking by remember { mutableStateOf(false) }
    var numberOfRooms by remember { mutableStateOf("") }
    var numberOfBathrooms by remember { mutableStateOf("") }
    var hasAirConditioning by remember { mutableStateOf(false) }
    var hasKitchen by remember { mutableStateOf(false) }
    var existingPhotoUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var newImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var photosToDelete by remember { mutableStateOf<List<String>>(emptyList()) }
    var tempUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(selectedApartment) {
        selectedApartment?.let { apartment ->
            nameInput = apartment.name
            addressInput = apartment.address
            priceInput = apartment.price.toString()
            descriptionInput = apartment.description
            hasWifi = apartment.features.hasWifi
            hasParking = apartment.features.hasParking
            numberOfRooms = apartment.features.numberOfRooms.toString()
            numberOfBathrooms = apartment.features.numberOfBathrooms.toString()
            hasAirConditioning = apartment.features.hasAirConditioning
            hasKitchen = apartment.features.hasKitchen
            existingPhotoUrls = apartment.photoUrls
        }
    }

    val permissionsToRequest = mutableListOf<String>().apply {
        add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    val multiplePermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissions.entries.forEach { entry ->
            Log.d("EditApartmentScreen", "${entry.key} = ${entry.value}")
            if (!entry.value) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Permission ${entry.key} denied")
                }
            }
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            Log.d("EditApartmentScreen", "Photo taken: $tempUri")
            tempUri?.let { uri ->
                newImageUris = newImageUris + uri
                Log.d("EditApartmentScreen", "newImageUris updated: $newImageUris")
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Image captured successfully")
                }
            }
        } else {
            Log.e("EditApartmentScreen", "Failed to take photo")
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Failed to capture image")
            }
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            newImageUris = newImageUris + uris
            Log.d("EditApartmentScreen", "Images picked: $newImageUris")
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Images selected: ${uris.size}")
            }
        } else {
            Log.d("EditApartmentScreen", "No images picked")
            coroutineScope.launch {
                snackbarHostState.showSnackbar("No images selected")
            }
        }
    }

    LaunchedEffect(Unit) {
        multiplePermissionsLauncher.launch(permissionsToRequest.toTypedArray())
    }

    if (selectedApartment == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF1E88E5))
        }
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFFF1F8FF)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF1F8FF))
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = { navController.navigate("profile") },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Back", color = Color.White)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Edit Apartment",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E88E5)
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = addressInput,
                onValueChange = { addressInput = it },
                label = { Text("Address") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = priceInput,
                onValueChange = { priceInput = it },
                label = { Text("Price (Daily)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = descriptionInput,
                onValueChange = { descriptionInput = it },
                label = { Text("Description") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Features",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E88E5)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("WiFi", fontSize = 16.sp, color = Color.Black)
                Switch(
                    checked = hasWifi,
                    onCheckedChange = { hasWifi = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF1E88E5))
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Parking", fontSize = 16.sp, color = Color.Black)
                Switch(
                    checked = hasParking,
                    onCheckedChange = { hasParking = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF1E88E5))
                )
            }
            OutlinedTextField(
                value = numberOfRooms,
                onValueChange = { numberOfRooms = it },
                label = { Text("Number of Rooms") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = numberOfBathrooms,
                onValueChange = { numberOfBathrooms = it },
                label = { Text("Number of Bathrooms") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Air Conditioning", fontSize = 16.sp, color = Color.Black)
                Switch(
                    checked = hasAirConditioning,
                    onCheckedChange = { hasAirConditioning = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF1E88E5))
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Kitchen", fontSize = 16.sp, color = Color.Black)
                Switch(
                    checked = hasKitchen,
                    onCheckedChange = { hasKitchen = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF1E88E5))
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (existingPhotoUrls.isNotEmpty()) {
                Text(
                    text = "Existing Photos:",
                    fontSize = 16.sp,
                    color = Color.Black,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(existingPhotoUrls - photosToDelete.toSet()) { photoUrl ->
                        Box {
                            AsyncImage(
                                model = photoUrl,
                                contentDescription = "Existing Apartment Image",
                                modifier = Modifier
                                    .width(100.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            IconButton(
                                onClick = {
                                    photosToDelete = photosToDelete + photoUrl
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(24.dp)
                                    .background(Color.Red.copy(alpha = 0.7f), shape = RoundedCornerShape(12.dp))
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove Photo",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (newImageUris.isNotEmpty()) {
                Text(
                    text = "New Photos:",
                    fontSize = 16.sp,
                    color = Color.Black,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(newImageUris) { uri ->
                        Box {
                            AsyncImage(
                                model = uri,
                                contentDescription = "New Apartment Image",
                                modifier = Modifier
                                    .width(100.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            IconButton(
                                onClick = {
                                    newImageUris = newImageUris - uri
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(24.dp)
                                    .background(Color.Red.copy(alpha = 0.7f), shape = RoundedCornerShape(12.dp))
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove Photo",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = {
                        val contentValues = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, "Rentmate_${System.currentTimeMillis()}.jpg")
                            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Rentmate")
                            }
                        }
                        tempUri = context.contentResolver.insert(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            contentValues
                        )
                        tempUri?.let { uri ->
                            Log.d("EditApartmentScreen", "Launching camera with URI: $uri")
                            takePictureLauncher.launch(uri)
                        } ?: run {
                            Log.e("EditApartmentScreen", "Failed to create image URI")
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Failed to create image URI")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Take Photo", color = Color.White)
                }

                Button(
                    onClick = {
                        Log.d("EditApartmentScreen", "Launching image picker")
                        pickImageLauncher.launch("image/*")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Pick from Gallery", color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val price = priceInput.toDoubleOrNull() ?: 0.0
                    val rooms = numberOfRooms.toIntOrNull() ?: 0
                    val bathrooms = numberOfBathrooms.toIntOrNull() ?: 0
                    if (nameInput.isBlank() || addressInput.isBlank() || price <= 0 || rooms <= 0 || bathrooms <= 0) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Please fill in all fields correctly")
                        }
                        return@Button
                    }
                    Log.d("EditApartmentScreen", "Updating apartment with newImageUris: $newImageUris, photosToDelete: $photosToDelete")
                    viewModel.updateApartment(
                        apartmentId = apartmentId,
                        name = nameInput,
                        address = addressInput,
                        price = price,
                        newImageUris = newImageUris,
                        existingPhotoUrls = existingPhotoUrls,
                        photosToDelete = photosToDelete,
                        description = descriptionInput,
                        features = ApartmentFeatures(
                            hasWifi = hasWifi,
                            hasParking = hasParking,
                            numberOfRooms = rooms,
                            numberOfBathrooms = bathrooms,
                            hasAirConditioning = hasAirConditioning,
                            hasKitchen = hasKitchen
                        )
                    )
                },
                enabled = updateApartmentState !is ApartmentViewModel.UpdateApartmentState.Loading,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save Changes", color = Color.White, fontSize = 16.sp)
            }
        }
    }
}