package com.example.rentmate.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AuthRepository {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    suspend fun signIn(email: String, password: String): Result<String> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val userId = result.user?.uid ?: return Result.failure(Exception("User ID not found"))
            Log.d("AuthRepository", "Sign-in successful for user: $userId")
            Result.success(userId)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Sign-in failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun signUp(email: String, password: String, username: String, role: String): Result<String> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val userId = result.user?.uid ?: return Result.failure(Exception("User ID not found"))
            Log.d("AuthRepository", "Sign-up successful for user: $userId")
            val userData = hashMapOf(
                "username" to username,
                "role" to role
            )
            db.collection("users").document(userId).set(userData).await()
            Log.d("AuthRepository", "User data saved for user: $userId with role: $role")
            Result.success(userId)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Sign-up failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getUsername(userId: String): Result<String> {
        return try {
            val document = db.collection("users").document(userId).get().await()
            val username = document.getString("username") ?: ""
            Log.d("AuthRepository", "Username fetched for user $userId: $username")
            Result.success(username)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Failed to fetch username for user $userId: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getUserRole(userId: String): Result<String> {
        return try {
            val document = db.collection("users").document(userId).get().await()
            val role = document.getString("role") ?: "tenant"
            Log.d("AuthRepository", "Role fetched for user $userId: $role")
            Result.success(role)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Failed to fetch role for user $userId: ${e.message}", e)
            Result.failure(e)
        }
    }
}