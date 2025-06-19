package com.example.rentmate.screens

import android.Manifest
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.rentmate.models.ApartmentFeatures
import com.example.rentmate.viewModels.ApartmentViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AddApartmentScreen(
    navController: NavController,
    viewModel: ApartmentViewModel
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val currentUser = FirebaseAuth.getInstance().currentUser
    val userRole by viewModel.userRole.collectAsState()

    LaunchedEffect(currentUser, userRole) {
        if (currentUser == null) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("You must be logged in to add an apartment")
                navController.navigate("signIn") {
                    popUpTo("addApartment") { inclusive = true }
                }
            }
        } else if (userRole != "landlord") {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Only landlords can add apartments")
                navController.navigate("searchResults") {
                    popUpTo("addApartment") { inclusive = true }
                }
            }
        }
    }

    if (currentUser == null || userRole != "landlord") {
        return
    }

    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var hasWifi by remember { mutableStateOf(false) }
    var hasParking by remember { mutableStateOf(false) }
    var numberOfRooms by remember { mutableStateOf("") }
    var numberOfBathrooms by remember { mutableStateOf("") }
    var hasAirConditioning by remember { mutableStateOf(false) }
    var hasKitchen by remember { mutableStateOf(false) }
    val imageUris = remember { mutableStateListOf<Uri>() }

    val pickMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5)
    ) { uris ->
        if (uris.isNotEmpty()) {
            imageUris.addAll(uris)
            Log.d("AddApartmentScreen", "Selected images: $uris")
        }
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var showCamera by remember { mutableStateOf(false) }
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }

    suspend fun getCameraProvider(): ProcessCameraProvider = suspendCoroutine { continuation ->
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                continuation.resume(cameraProviderFuture.get())
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun startCamera(previewView: PreviewView) {
        coroutineScope.launch {
            try {
                val cameraProvider = getCameraProvider()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                imageCapture = ImageCapture.Builder().build()

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Log.e("AddApartmentScreen", "Camera binding failed", e)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Failed to start camera: ${e.message}")
                }
            }
        }
    }

    fun takePhoto() {
        val imageCaptureInstance = imageCapture ?: return
        val photoFile = createTempImageFile(context)
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCaptureInstance.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val photoUri = photoFile.toUri()
                    imageUris.add(photoUri)
                    showCamera = false
                    Log.d("AddApartmentScreen", "Photo captured, URI: $photoUri, total images: ${imageUris.size}")
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("AddApartmentScreen", "Failed to take photo", exception)
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Failed to take photo: ${exception.message}")
                    }
                }
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    val addApartmentState by viewModel.addApartmentState.collectAsState()

    LaunchedEffect(addApartmentState) {
        when (val state = addApartmentState) {
            is ApartmentViewModel.AddApartmentState.Success -> {
                snackbarHostState.showSnackbar(state.message)
                navController.navigate("searchResults") {
                    popUpTo("addApartment") { inclusive = true }
                }
            }
            is ApartmentViewModel.AddApartmentState.Error -> {
                snackbarHostState.showSnackbar(state.message)
            }
            else -> Unit
        }
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
                onClick = { navController.popBackStack() },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Back", color = Color.White)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Add New Apartment",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E88E5)
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color(0xFF1A252F),
                    unfocusedTextColor = Color(0xFF1A252F),
                    focusedBorderColor = Color(0xFF1E88E5),
                    unfocusedBorderColor = Color(0xFFD0D5DD)
                )
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("Address") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color(0xFF1A252F),
                    unfocusedTextColor = Color(0xFF1A252F),
                    focusedBorderColor = Color(0xFF1E88E5),
                    unfocusedBorderColor = Color(0xFFD0D5DD)
                )
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = price,
                onValueChange = { price = it },
                label = { Text("Price per Night") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color(0xFF1A252F),
                    unfocusedTextColor = Color(0xFF1A252F),
                    focusedBorderColor = Color(0xFF1E88E5),
                    unfocusedBorderColor = Color(0xFFD0D5DD)
                )
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color(0xFF1A252F),
                    unfocusedTextColor = Color(0xFF1A252F),
                    focusedBorderColor = Color(0xFF1E88E5),
                    unfocusedBorderColor = Color(0xFFD0D5DD)
                )
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
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color(0xFF1A252F),
                    unfocusedTextColor = Color(0xFF1A252F),
                    focusedBorderColor = Color(0xFF1E88E5),
                    unfocusedBorderColor = Color(0xFFD0D5DD)
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = numberOfBathrooms,
                onValueChange = { numberOfBathrooms = it },
                label = { Text("Number of Bathrooms") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color(0xFF1A252F),
                    unfocusedTextColor = Color(0xFF1A252F),
                    focusedBorderColor = Color(0xFF1E88E5),
                    unfocusedBorderColor = Color(0xFFD0D5DD)
                )
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

            Button(
                onClick = {
                    pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Select Images (up to 5)", color = Color.White)
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (cameraPermissionState.status.isGranted) {
                Button(
                    onClick = { showCamera = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Take Photo", tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Take Photo", color = Color.White)
                }
            } else {
                Button(
                    onClick = { cameraPermissionState.launchPermissionRequest() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Request Camera Permission", color = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (showCamera && cameraPermissionState.status.isGranted) {
                Dialog(onDismissRequest = { showCamera = false }) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp),
                        color = Color.Black,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                AndroidView(
                                    factory = { ctx ->
                                        PreviewView(ctx).apply {
                                            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                    update = { previewView ->
                                        if (cameraPermissionState.status.isGranted) {
                                            startCamera(previewView)
                                        }
                                    }
                                )
                            }
                            Button(
                                onClick = { takePhoto() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Capture", color = Color.White)
                            }
                        }
                    }
                }
            }

            Text(
                text = "Selected images: ${imageUris.size}",
                fontSize = 16.sp,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(imageUris) { uri ->
                    AsyncImage(
                        model = uri,
                        contentDescription = "Selected Image",
                        modifier = Modifier
                            .width(120.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (name.isBlank() || address.isBlank() || price.isBlank() || description.isBlank()) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Please fill in all fields")
                        }
                        return@Button
                    }
                    val priceValue = price.toDoubleOrNull()
                    if (priceValue == null || priceValue <= 0) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Please enter a valid price")
                        }
                        return@Button
                    }
                    val rooms = numberOfRooms.toIntOrNull() ?: 0
                    val bathrooms = numberOfBathrooms.toIntOrNull() ?: 0
                    if (rooms <= 0 || bathrooms <= 0) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Please enter valid numbers for rooms and bathrooms")
                        }
                        return@Button
                    }
                    Log.d("AddApartmentScreen", "Dodajem apartman s imageUris: $imageUris")
                    viewModel.addApartment(
                        name = name,
                        address = address,
                        price = priceValue,
                        imageUris = imageUris,
                        description = description,
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
                enabled = addApartmentState !is ApartmentViewModel.AddApartmentState.Loading,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (addApartmentState is ApartmentViewModel.AddApartmentState.Loading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Text("Add Apartment", color = Color.White, fontSize = 16.sp)
                }
            }
        }
    }
}

private fun createTempImageFile(context: Context): File {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val storageDir = context.cacheDir
    return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
}