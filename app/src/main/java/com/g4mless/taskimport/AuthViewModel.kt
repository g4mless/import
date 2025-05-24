package com.g4mless.taskimport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth // Import extension ktx
import com.google.firebase.ktx.Firebase // Import Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class AuthResult(
    val success: Boolean,
    val errorMessage: String? = null
)

class AuthViewModel : ViewModel() {

    private val auth: FirebaseAuth = Firebase.auth

    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _authResult = MutableStateFlow<AuthResult?>(null)
    val authResult: StateFlow<AuthResult?> = _authResult.asStateFlow()

    private val _isEmailVerified = MutableStateFlow(auth.currentUser?.isEmailVerified ?: false)
    val isEmailVerified: StateFlow<Boolean> = _isEmailVerified.asStateFlow()

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            _currentUser.value = user
            _isEmailVerified.value = user?.isEmailVerified ?: false
        }
        checkEmailVerificationStatus()
    }

    fun signUp(email: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _authResult.value = null
            try {
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                result.user?.sendEmailVerification()?.await()
                _authResult.value = AuthResult(success = true)
            } catch (e: Exception) {
                e.printStackTrace()
                _authResult.value = AuthResult(success = false, errorMessage = e.message ?: "Sign up failed")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _authResult.value = null
            try {
                auth.signInWithEmailAndPassword(email, password).await()
                checkEmailVerificationStatus()
                _authResult.value = AuthResult(success = true)
            } catch (e: Exception) {
                e.printStackTrace()
                _authResult.value = AuthResult(success = false, errorMessage = e.message ?: "Sign in failed")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun signOut() {
        auth.signOut()
    }

    fun checkEmailVerificationStatus() {
        viewModelScope.launch {
            try {
                // Reload data pengguna dari Firebase
                auth.currentUser?.reload()?.await()
                // Update state verifikasi
                _isEmailVerified.value = auth.currentUser?.isEmailVerified ?: false
            } catch (e: Exception) {
                // Tangani error saat reload (misal: tidak ada koneksi)
                e.printStackTrace()
                // Mungkin tampilkan pesan error?
                // _authResult.value = AuthResult(success = false, errorMessage = "Failed to check verification status: ${e.message}")
            }
        }
    }

    fun resendVerificationEmail() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                auth.currentUser?.sendEmailVerification()?.await()
                _authResult.value = AuthResult(success = true, errorMessage = "Verification email sent!")
            } catch (e: Exception) {
                e.printStackTrace()
                _authResult.value = AuthResult(success = false, errorMessage = "Failed to send verification email: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun sendPasswordResetEmail(email: String) {
        if (email.isBlank()) {
            _authResult.value = AuthResult(success = false, errorMessage = "Please enter your email address.")
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _authResult.value = null // Reset hasil sebelumnya
            try {
                auth.sendPasswordResetEmail(email).await() // Tunggu hasil
                _authResult.value = AuthResult(success = true, errorMessage = "Password reset email sent to $email. Please check your inbox.")
            } catch (e: Exception) {
                e.printStackTrace()
                _authResult.value = AuthResult(success = false, errorMessage = e.message ?: "Failed to send password reset email.")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearAuthResult() {
        _authResult.value = null
    }
}
