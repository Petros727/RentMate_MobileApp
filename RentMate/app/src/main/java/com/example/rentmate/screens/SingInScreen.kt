package com.example.rentmate.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rentmate.viewModels.AuthViewModel

@Composable
fun SignInScreen(
    viewModel: AuthViewModel = viewModel(),
    onSignInSuccess: (String) -> Unit = {},
    onNavigateToSignUp: () -> Unit = {}
) {
    val signInState by viewModel.signInState.collectAsState()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val isLoading = signInState is AuthViewModel.SignInState.Loading

    LaunchedEffect(signInState) {
        when (signInState) {
            is AuthViewModel.SignInState.Success -> {
                val startTime = System.currentTimeMillis()
                Log.d("SignInScreen", "Sign-in success observed at: $startTime")
                onSignInSuccess((signInState as AuthViewModel.SignInState.Success).userId)
                Log.d("SignInScreen", "Navigation triggered, time taken: ${System.currentTimeMillis() - startTime}ms")
            }
            is AuthViewModel.SignInState.Error -> {
                Log.e("SignInScreen", "Sign-in error: ${(signInState as AuthViewModel.SignInState.Error).message}")
            }
            else -> Unit
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "RentMate",
            fontSize = 24.sp,
            color = Color.Black,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFD3D3D3))
                .padding(16.dp),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Sign In",
            fontSize = 20.sp,
            color = Color.Black
        )

        Spacer(modifier = Modifier.height(16.dp))

        BasicTextField(
            value = email,
            onValueChange = { email = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(Color(0xFFE0E0E0))
                .padding(8.dp),
            textStyle = TextStyle(fontSize = 16.sp),
            decorationBox = { innerTextField ->
                if (email.isEmpty()) {
                    Text(text = "Email", color = Color.Gray)
                }
                innerTextField()
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        BasicTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(Color(0xFFE0E0E0))
                .padding(8.dp),
            textStyle = TextStyle(fontSize = 16.sp),
            decorationBox = { innerTextField ->
                if (password.isEmpty()) {
                    Text(text = "Password", color = Color.Gray)
                }
                innerTextField()
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                viewModel.signIn(email, password)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4682B4)),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Text(text = "Sign In", color = Color.White, fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Don't have an account? Sign Up",
            fontSize = 14.sp,
            color = Color(0xFF4682B4),
            modifier = Modifier.clickable { onNavigateToSignUp() }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SignInScreenPreview() {
    SignInScreen()
}