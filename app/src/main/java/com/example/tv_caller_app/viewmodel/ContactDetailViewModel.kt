package com.example.tv_caller_app.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tv_caller_app.auth.SessionManager
import com.example.tv_caller_app.model.CallHistory
import com.example.tv_caller_app.repository.CallHistoryRepository
import com.example.tv_caller_app.repository.ContactRepository
import kotlinx.coroutines.launch

/**
 * ViewModel for ContactDetailFragment.
 * Manages UI state and business logic for contact details and call history.
 * Uses singleton repository for efficient caching across app.
 */
class ContactDetailViewModel(
    sessionManager: SessionManager
) : ViewModel() {

    private val callHistoryRepository = CallHistoryRepository.getInstance(sessionManager)
    private val contactRepository = ContactRepository.getInstance(sessionManager)
    private val TAG = "ContactDetailViewModel"

    // Contact info (passed from fragment args)
    private val _contactId = MutableLiveData<String>()
    val contactId: LiveData<String> = _contactId

    private val _contactName = MutableLiveData<String>()
    val contactName: LiveData<String> = _contactName

    private val _phoneNumber = MutableLiveData<String>()
    val phoneNumber: LiveData<String> = _phoneNumber

    // Call history for this contact
    private val _callHistory = MutableLiveData<List<CallHistory>>()
    val callHistory: LiveData<List<CallHistory>> = _callHistory

    // Loading state
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    // Error messages
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    // Call action state (for showing call in progress)
    private val _isCallInProgress = MutableLiveData<Boolean>(false)
    val isCallInProgress: LiveData<Boolean> = _isCallInProgress

    // Delete success state
    private val _isDeleted = MutableLiveData<Boolean>(false)
    val isDeleted: LiveData<Boolean> = _isDeleted

    /**
     * Initialize contact details.
     * @param id Contact ID
     * @param name Contact name
     * @param phone Phone number
     */
    fun setContactDetails(id: String, name: String, phone: String) {
        _contactId.value = id
        _contactName.value = name
        _phoneNumber.value = phone
        loadCallHistory(id)
    }

    /**
     * Load call history for this contact.
     */
    private fun loadCallHistory(contactId: String) {
        Log.d(TAG, "loadCallHistory() for contact $contactId")
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                Log.d(TAG, "Fetching call history from repository...")
                val history = callHistoryRepository.getCallHistoryForContact(contactId)
                Log.d(TAG, "Successfully loaded ${history.size} call records")

                _callHistory.value = history
            } catch (e: Exception) {
                Log.e(TAG, "Error loading call history", e)
                _errorMessage.value = "Failed to load call history: ${e.message}"
                _callHistory.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Initiate a call to this contact.
     * In a real implementation, this would trigger the phone dialer.
     */
    fun callContact() {
        val phone = _phoneNumber.value ?: return
        val name = _contactName.value ?: "Unknown"

        Log.d(TAG, "Calling $name at $phone")
        _isCallInProgress.value = true

        // In real implementation, you would use Intent to start phone call
        // For now, just simulate call action
        viewModelScope.launch {
            // Simulate call action
            kotlinx.coroutines.delay(1000)
            _isCallInProgress.value = false
        }
    }

    /**
     * Clear error message after it's been displayed.
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Delete this contact from the database.
     */
    fun deleteContact() {
        val id = _contactId.value
        val name = _contactName.value ?: "Unknown"

        if (id.isNullOrBlank()) {
            _errorMessage.value = "Cannot delete contact: Invalid contact ID"
            return
        }

        Log.d(TAG, "Deleting contact: $name (ID: $id)")
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val success = contactRepository.deleteContact(id)

                if (success) {
                    Log.d(TAG, "Contact deleted successfully: $name")
                    _isDeleted.value = true
                } else {
                    Log.e(TAG, "Failed to delete contact: $name")
                    _errorMessage.value = "Failed to delete contact. Please try again."
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting contact", e)
                _errorMessage.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Reset deleted state.
     */
    fun resetDeletedState() {
        _isDeleted.value = false
    }
}
