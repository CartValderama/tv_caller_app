package com.example.tv_caller_app.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tv_caller_app.auth.SessionManager
import com.example.tv_caller_app.model.Contact
import com.example.tv_caller_app.repository.ContactRepository
import kotlinx.coroutines.launch

/**
 * ViewModel for AllContactsFragment.
 * Manages UI state and business logic for all contacts with pagination.
 * Uses singleton repository for efficient caching across app.
 */
class ContactsViewModel(
    sessionManager: SessionManager
) : ViewModel() {

    private val repository = ContactRepository.getInstance(sessionManager)
    private val TAG = "ContactsViewModel"

    companion object {
        const val ITEMS_PER_PAGE = 4
    }

    // All contacts loaded from repository
    private var allContacts: List<Contact> = emptyList()

    // LiveData for current page contacts
    private val _currentPageContacts = MutableLiveData<List<Contact>>()
    val currentPageContacts: LiveData<List<Contact>> = _currentPageContacts

    // LiveData for current page number (0-indexed)
    private val _currentPage = MutableLiveData<Int>(0)
    val currentPage: LiveData<Int> = _currentPage

    // LiveData for total pages
    private val _totalPages = MutableLiveData<Int>(0)
    val totalPages: LiveData<Int> = _totalPages

    // LiveData for loading state
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    // LiveData for error messages
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    // LiveData for pagination button states
    private val _canGoNext = MutableLiveData<Boolean>(false)
    val canGoNext: LiveData<Boolean> = _canGoNext

    private val _canGoPrevious = MutableLiveData<Boolean>(false)
    val canGoPrevious: LiveData<Boolean> = _canGoPrevious

    /**
     * Load all contacts from repository and display first page.
     */
    fun loadAllContacts() {
        Log.d(TAG, "loadAllContacts() called")
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                Log.d(TAG, "Fetching all contacts from repository...")
                allContacts = repository.getAllContacts()
                Log.d(TAG, "Successfully loaded ${allContacts.size} contacts")

                calculateTotalPages()
                displayPage(0)

                if (allContacts.isEmpty()) {
                    _errorMessage.value = "No contacts found"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading contacts", e)
                _errorMessage.value = "Failed to load contacts: ${e.message}"
                allContacts = emptyList()
                _currentPageContacts.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Refresh contacts from database (force reload, bypass cache).
     * Maintains current page if possible.
     */
    fun refreshContacts() {
        Log.d(TAG, "refreshContacts() called - forcing reload from database")
        val currentPageNumber = _currentPage.value ?: 0
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                Log.d(TAG, "Fetching fresh contacts from database...")
                allContacts = repository.getAllContacts(forceRefresh = true)
                Log.d(TAG, "Successfully refreshed ${allContacts.size} contacts")

                calculateTotalPages()

                // Try to maintain current page, but reset to 0 if current page is now out of bounds
                val totalPagesValue = _totalPages.value ?: 0
                val pageToDisplay = if (currentPageNumber < totalPagesValue) currentPageNumber else 0
                displayPage(pageToDisplay)

                if (allContacts.isEmpty()) {
                    _errorMessage.value = "No contacts found"
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
     * Navigate to next page.
     */
    fun nextPage() {
        val current = _currentPage.value ?: 0
        val total = _totalPages.value ?: 0
        if (current < total - 1) {
            displayPage(current + 1)
        }
    }

    /**
     * Navigate to previous page.
     */
    fun previousPage() {
        val current = _currentPage.value ?: 0
        if (current > 0) {
            displayPage(current - 1)
        }
    }

    /**
     * Display contacts for a specific page.
     * @param pageNumber 0-indexed page number
     */
    private fun displayPage(pageNumber: Int) {
        if (allContacts.isEmpty()) {
            _currentPageContacts.value = emptyList()
            _currentPage.value = 0
            updatePaginationButtons()
            return
        }

        val startIndex = pageNumber * ITEMS_PER_PAGE
        val endIndex = minOf(startIndex + ITEMS_PER_PAGE, allContacts.size)

        _currentPageContacts.value = allContacts.subList(startIndex, endIndex)
        _currentPage.value = pageNumber
        updatePaginationButtons()

        Log.d(TAG, "Displaying page ${pageNumber + 1}/${ _totalPages.value} with ${endIndex - startIndex} contacts")
    }

    /**
     * Calculate total number of pages based on all contacts.
     */
    private fun calculateTotalPages() {
        val total = if (allContacts.isEmpty()) {
            0
        } else {
            (allContacts.size + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE
        }
        _totalPages.value = total
        Log.d(TAG, "Total pages: $total for ${allContacts.size} contacts")
    }

    /**
     * Update pagination button enabled/disabled states.
     */
    private fun updatePaginationButtons() {
        val current = _currentPage.value ?: 0
        val total = _totalPages.value ?: 0

        _canGoPrevious.value = current > 0
        _canGoNext.value = current < total - 1
    }

    /**
     * Clear error message after it's been displayed.
     */
    fun clearError() {
        _errorMessage.value = null
    }
}
