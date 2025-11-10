package com.example.tv_caller_app.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tv_caller_app.repository.AuthRepository
import kotlinx.coroutines.launch

/**
 * ViewModel for LoginFragment and RegisterFragment.
 * Manages UI state and business logic for authentication.
 */
class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val TAG = "AuthViewModel"

    // Loading state
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // Error messages
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    // Success state (triggers navigation to main screen)
    private val _authSuccess = MutableLiveData<Boolean>(false)
    val authSuccess: LiveData<Boolean> = _authSuccess

    // Email verification state
    private val _emailVerificationRequired = MutableLiveData<String?>() // Stores email that needs verification
    val emailVerificationRequired: LiveData<String?> = _emailVerificationRequired

    // Form validation errors
    private val _emailError = MutableLiveData<String?>()
    val emailError: LiveData<String?> = _emailError

    private val _passwordError = MutableLiveData<String?>()
    val passwordError: LiveData<String?> = _passwordError

    /**
     * Attempt to log in with email and password.
     * Uses Supabase authentication.
     */
    fun login(email: String, password: String) {
        Log.d(TAG, "login() called with email: $email")

        // Clear previous errors
        _emailError.value = null
        _passwordError.value = null
        _errorMessage.value = null
        _emailVerificationRequired.value = null

        // Validate input
        if (!validateEmail(email)) {
            _emailError.value = "Please enter a valid email"
            return
        }

        if (password.isBlank()) {
            _passwordError.value = "Please enter your password"
            return
        }

        _isLoading.value = true

        viewModelScope.launch {
            try {
                val result = authRepository.signIn(email, password)

                if (result.isSuccess) {
                    Log.d(TAG, "Login successful")
                    _authSuccess.value = true
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Login failed"
                    Log.e(TAG, "Login failed: $error")
                    _errorMessage.value = error
                }
            } catch (e: Exception) {
                Log.e(TAG, "Login error", e)
                _errorMessage.value = e.message ?: "Login failed"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Attempt to register a new account.
     * Uses Supabase authentication with email verification.
     * Creates user profile with provided full name and phone number.
     */
    fun register(email: String, password: String, confirmPassword: String, fullName: String? = null, phoneNumber: String? = null) {
        Log.d(TAG, "register() called with email: $email, fullName: $fullName, phoneNumber: $phoneNumber")

        // Clear previous errors
        _emailError.value = null
        _passwordError.value = null
        _errorMessage.value = null
        _emailVerificationRequired.value = null

        // Validate input
        if (!validateEmail(email)) {
            _emailError.value = "Please enter a valid email"
            return
        }

        if (!validatePassword(password)) {
            _passwordError.value = getPasswordRequirements()
            return
        }

        if (password != confirmPassword) {
            _passwordError.value = "Passwords do not match"
            return
        }

        _isLoading.value = true

        viewModelScope.launch {
            try {
                val result = authRepository.signUp(email, password, fullName, phoneNumber)

                if (result.isSuccess) {
                    Log.d(TAG, "Registration successful - verification email sent")
                    _emailVerificationRequired.value = email
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Registration failed"
                    Log.e(TAG, "Registration failed: $error")
                    _errorMessage.value = error
                }
            } catch (e: Exception) {
                Log.e(TAG, "Registration error", e)
                _errorMessage.value = e.message ?: "Registration failed"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Validate email format.
     */
    private fun validateEmail(email: String): Boolean {
        if (email.isBlank()) return false
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    /**
     * Validate password meets security requirements.
     * - At least 8 characters
     * - One uppercase letter
     * - One lowercase letter
     * - One number
     * - One special character
     */
    private fun validatePassword(password: String): Boolean {
        if (password.length < 8) return false

        val hasUpperCase = password.any { it.isUpperCase() }
        val hasLowerCase = password.any { it.isLowerCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSpecial = password.any { !it.isLetterOrDigit() }

        return hasUpperCase && hasLowerCase && hasDigit && hasSpecial
    }

    /**
     * Get password requirements message.
     */
    private fun getPasswordRequirements(): String {
        return "Password must contain:\n" +
                "• At least 8 characters\n" +
                "• One uppercase letter\n" +
                "• One lowercase letter\n" +
                "• One number\n" +
                "• One special character"
    }

    /**
     * Clear error message after it's been displayed.
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Clear email validation error.
     */
    fun clearEmailError() {
        _emailError.value = null
    }

    /**
     * Clear password validation error.
     */
    fun clearPasswordError() {
        _passwordError.value = null
    }

    /**
     * Reset auth success state after navigation.
     */
    fun resetAuthSuccess() {
        _authSuccess.value = false
    }

    /**
     * Resend verification email to user.
     */
    fun resendVerificationEmail(email: String) {
        Log.d(TAG, "Resending verification email to: $email")
        _isLoading.value = true

        viewModelScope.launch {
            try {
                val result = authRepository.resendVerificationEmail(email)

                if (result.isSuccess) {
                    Log.d(TAG, "Verification email resent successfully")
                    _errorMessage.value = "Verification email sent! Check your inbox."
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Failed to send email"
                    Log.e(TAG, "Failed to resend verification email: $error")
                    _errorMessage.value = error
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error resending verification email", e)
                _errorMessage.value = e.message ?: "Failed to send email"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Reset email verification state.
     */
    fun resetEmailVerification() {
        _emailVerificationRequired.value = null
    }

    /**
     * Log out current user.
     * Clears session and returns to login screen.
     */
    fun logout() {
        Log.d(TAG, "Logout requested")
        _isLoading.value = true

        viewModelScope.launch {
            try {
                val result = authRepository.signOut()

                if (result.isSuccess) {
                    Log.d(TAG, "Logout successful")
                    _authSuccess.value = false
                    // Don't set success state - let activity handle navigation
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Logout failed"
                    Log.e(TAG, "Logout failed: $error")
                    _errorMessage.value = error
                }
            } catch (e: Exception) {
                Log.e(TAG, "Logout error", e)
                _errorMessage.value = e.message ?: "Logout failed"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
