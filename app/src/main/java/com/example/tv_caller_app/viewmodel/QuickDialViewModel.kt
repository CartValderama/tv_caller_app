package com.example.tv_caller_app.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tv_caller_app.auth.SessionManager
import com.example.tv_caller_app.model.Contact
import com.example.tv_caller_app.repository.QuickDialRepository
import kotlinx.coroutines.launch

/**
 * ViewModel for QuickDialFragment.
 * Manages UI state and business logic for quick dial contacts display.
 * Uses singleton repository for efficient caching across app.
 */
class QuickDialViewModel(
    sessionManager: SessionManager
) : ViewModel() {

    private val repository = QuickDialRepository.getInstance(sessionManager)
    private val TAG = "QuickDialViewModel"

    // LiveData for contacts list
    private val _contacts = MutableLiveData<List<Contact>>()
    val contacts: LiveData<List<Contact>> = _contacts

    // LiveData for loading state
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    // LiveData for error messages
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    /**
     * Load quick dial contacts from repository.
     * Contacts are ordered by call history score.
     */
    fun loadQuickDialContacts() {
        Log.d(TAG, "loadQuickDialContacts() called")
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                Log.d(TAG, "Fetching contacts from repository...")
                val contactsList = repository.getQuickDialContacts()
                Log.d(TAG, "Successfully loaded ${contactsList.size} contacts")

                _contacts.value = contactsList

                if (contactsList.isEmpty()) {
                    _errorMessage.value = "No quick dial contacts found"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading contacts", e)
                _errorMessage.value = "Failed to load contacts: ${e.message}"
                _contacts.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Refresh quick dial contacts from database (force reload, bypass cache).
     */
    fun refreshQuickDialContacts() {
        Log.d(TAG, "refreshQuickDialContacts() called - forcing reload from database")
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                Log.d(TAG, "Fetching fresh contacts from database...")
                val contactsList = repository.getQuickDialContacts(forceRefresh = true)
                Log.d(TAG, "Successfully refreshed ${contactsList.size} contacts")

                _contacts.value = contactsList

                if (contactsList.isEmpty()) {
                    _errorMessage.value = "No quick dial contacts found"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing contacts", e)
                _errorMessage.value = "Failed to refresh contacts: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Clear error message after it's been displayed.
     */
    fun clearError() {
        _errorMessage.value = null
    }
}
