package com.example.tv_caller_app.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

/**
 * ViewModel for DialPadFragment.
 * Manages UI state and business logic for manual number entry via dial pad.
 */
class DialPadViewModel : ViewModel() {

    private val TAG = "DialPadViewModel"

    // LiveData for the phone number being entered
    private val _phoneNumber = MutableLiveData<String>("")
    val phoneNumber: LiveData<String> = _phoneNumber

    // LiveData for call action state
    private val _isCallInProgress = MutableLiveData<Boolean>(false)
    val isCallInProgress: LiveData<Boolean> = _isCallInProgress

    // LiveData for error messages
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    /**
     * Add a digit to the phone number.
     * @param digit The digit to add (0-9, *, #)
     */
    fun addDigit(digit: String) {
        val currentNumber = _phoneNumber.value ?: ""
        _phoneNumber.value = currentNumber + digit
        Log.d(TAG, "Digit added: $digit, Current number: ${_phoneNumber.value}")
    }

    /**
     * Remove the last digit from the phone number (backspace).
     */
    fun removeLastDigit() {
        val currentNumber = _phoneNumber.value ?: ""
        if (currentNumber.isNotEmpty()) {
            _phoneNumber.value = currentNumber.dropLast(1)
            Log.d(TAG, "Last digit removed, Current number: ${_phoneNumber.value}")
        }
    }

    /**
     * Clear the entire phone number.
     */
    fun clearNumber() {
        _phoneNumber.value = ""
        Log.d(TAG, "Number cleared")
    }

    /**
     * Set the phone number directly (when user types in the EditText).
     * @param number The phone number to set
     */
    fun setPhoneNumber(number: String) {
        _phoneNumber.value = number
        Log.d(TAG, "Phone number set: $number")
    }

    /**
     * Initiate a call with the entered phone number.
     */
    fun makeCall() {
        val number = _phoneNumber.value ?: ""

        // Validate phone number
        if (number.isEmpty()) {
            _errorMessage.value = "Please enter a phone number"
            Log.w(TAG, "Attempted to call with empty number")
            return
        }

        if (number.length < 3) {
            _errorMessage.value = "Phone number too short"
            Log.w(TAG, "Attempted to call with too short number: $number")
            return
        }

        Log.d(TAG, "Initiating call to: $number")
        _isCallInProgress.value = true

        // TODO: Implement actual phone calling functionality
        // For now, just simulate the call action
    }

    /**
     * End the current call simulation.
     */
    fun endCall() {
        _isCallInProgress.value = false
        Log.d(TAG, "Call ended")
    }

    /**
     * Clear error message after it's been displayed.
     */
    fun clearError() {
        _errorMessage.value = null
    }
}
