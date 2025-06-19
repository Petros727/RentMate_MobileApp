package com.example.rentmate.viewModels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rentmate.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {
    private val repository = AuthRepository()

    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username.asStateFlow()

    private val _role = MutableStateFlow("tenant")
    val role: StateFlow<String> = _role.asStateFlow()

    private val _signInState = MutableStateFlow<SignInState>(SignInState.Idle)
    val signInState: StateFlow<SignInState> = _signInState.asStateFlow()

    private val _signUpState = MutableStateFlow<SignUpState>(SignUpState.Idle)
    val signUpState: StateFlow<SignUpState> = _signUpState.asStateFlow()

    sealed class SignInState {
        object Idle : SignInState()
        object Loading : SignInState()
        data class Success(val userId: String) : SignInState()
        data class Error(val message: String) : SignInState()
    }

    sealed class SignUpState {
        object Idle : SignUpState()
        object Loading : SignUpState()
        data class Success(val userId: String) : SignUpState()
        data class Error(val message: String) : SignUpState()
    }

    init {
        val currentUser = FirebaseAuth.getInstance().currentUser
        Log.d("AuthViewModel", "Current user: ${currentUser?.uid ?: "Not authenticated"}")
        if (currentUser != null) {
            fetchUsername(currentUser.uid)
            fetchRole(currentUser.uid)
        }
    }

    fun signIn(email: String, password: String) {
        val startTime = System.currentTimeMillis()
        Log.d("AuthViewModel", "Sign-in started at: $startTime")

        if (email.isBlank() || password.isBlank()) {
            _signInState.value = SignInState.Error("Email and password cannot be empty")
            Log.e("AuthViewModel", "Invalid input: email=$email, password=$password")
            return
        }

        viewModelScope.launch {
            _signInState.value = SignInState.Loading
            Log.d("AuthViewModel", "Calling repository.signIn")
            val result = repository.signIn(email, password)
            if (result.isSuccess) {
                val userId = result.getOrNull() ?: ""
                if (userId.isNotEmpty()) {
                    _signInState.value = SignInState.Success(userId)
                    Log.d("AuthViewModel", "Sign-in successful, userId: $userId, time taken: ${System.currentTimeMillis() - startTime}ms")
                    fetchUsername(userId)
                    fetchRole(userId)
                } else {
                    _signInState.value = SignInState.Error("User ID not found")
                    Log.e("AuthViewModel", "User ID not found, time taken: ${System.currentTimeMillis() - startTime}ms")
                }
            } else {
                _signInState.value = SignInState.Error(result.exceptionOrNull()?.message ?: "Sign-in failed")
                Log.e("AuthViewModel", "Sign-in failed: ${result.exceptionOrNull()?.message}, time taken: ${System.currentTimeMillis() - startTime}ms")
            }
        }
    }

    private fun fetchUsername(userId: String) {
        val startTime = System.currentTimeMillis()
        Log.d("AuthViewModel", "Fetching username for userId: $userId at: $startTime")
        viewModelScope.launch {
            val usernameResult = repository.getUsername(userId)
            if (usernameResult.isSuccess) {
                _username.value = usernameResult.getOrNull() ?: ""
                Log.d("AuthViewModel", "Username fetched: ${_username.value}, time taken: ${System.currentTimeMillis() - startTime}ms")
            } else {
                Log.e("AuthViewModel", "Failed to fetch username: ${usernameResult.exceptionOrNull()?.message}, time taken: ${System.currentTimeMillis() - startTime}ms")
            }
        }
    }

    private fun fetchRole(userId: String) {
        Log.d("AuthViewModel", "Fetching role for userId: $userId")
        viewModelScope.launch {
            val roleResult = repository.getUserRole(userId)
            if (roleResult.isSuccess) {
                _role.value = roleResult.getOrNull() ?: "tenant"
                Log.d("AuthViewModel", "Role fetched: ${_role.value}")
            } else {
                Log.e("AuthViewModel", "Failed to fetch role: ${roleResult.exceptionOrNull()?.message}")
            }
        }
    }

    fun signUp(email: String, password: String, username: String, role: String) {
        if (email.isBlank() || password.isBlank() || username.isBlank()) {
            _signUpState.value = SignUpState.Error("All fields must be filled")
            return
        }

        viewModelScope.launch {
            _signUpState.value = SignUpState.Loading
            val result = repository.signUp(email, password, username, role)
            if (result.isSuccess) {
                val userId = result.getOrNull() ?: ""
                if (userId.isNotEmpty()) {
                    _username.value = username
                    _role.value = role
                    _signUpState.value = SignUpState.Success(userId)
                } else {
                    _signUpState.value = SignUpState.Error("User ID not found")
                }
            } else {
                _signUpState.value = SignUpState.Error(result.exceptionOrNull()?.message ?: "Sign-up failed")
            }
        }
    }
}