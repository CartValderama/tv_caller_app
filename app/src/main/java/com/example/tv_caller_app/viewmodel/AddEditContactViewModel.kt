package com.example.tv_caller_app.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tv_caller_app.auth.SessionManager
import com.example.tv_caller_app.repository.ContactRepository
import kotlinx.coroutines.launch

/**
 * ViewModel for AddEditContactFragment.
 * Manages UI state and business logic for adding/editing contacts.
 * Uses singleton repository for efficient caching across app.
 */
class AddEditContactViewModel(
    sessionManager: SessionManager
) : ViewModel() {

    private val repository = ContactRepository.getInstance(sessionManager)
    private val TAG = "AddEditContactVM"

    // Contact ID (null for new contact, set for editing)
    private var contactId: String? = null
    private var isEditMode = false

    // Input fields (required)
    private val _name = MutableLiveData<String>("")
    val name: LiveData<String> = _name

    private val _phoneNumber = MutableLiveData<String>("")
    val phoneNumber: LiveData<String> = _phoneNumber

    // Input fields (optional)
    private val _email = MutableLiveData<String>("")
    val email: LiveData<String> = _email

    private val _address = MutableLiveData<String>("")
    val address: LiveData<String> = _address

    private val _notes = MutableLiveData<String>("")
    val notes: LiveData<String> = _notes

    // Loading state
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // Success state
    private val _isSaved = MutableLiveData<Boolean>(false)
    val isSaved: LiveData<Boolean> = _isSaved

    // Error messages
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    /**
     * Set mode to add new contact.
     */
    fun setAddMode() {
        isEditMode = false
        contactId = null
        _name.value = ""
        _phoneNumber.value = ""
        _email.value = ""
        _address.value = ""
        _notes.value = ""
        Log.d(TAG, "Set to ADD mode")
    }

    /**
     * Set mode to edit existing contact.
     * @param id Contact ID
     * @param currentName Current contact name
     * @param currentPhone Current phone number
     * @param currentEmail Current email (optional)
     * @param currentAddress Current address (optional)
     * @param currentNotes Current notes (optional)
     */
    fun setEditMode(
        id: String,
        currentName: String,
        currentPhone: String,
        currentEmail: String? = null,
        currentAddress: String? = null,
        currentNotes: String? = null
    ) {
        isEditMode = true
        contactId = id
        _name.value = currentName
        _phoneNumber.value = currentPhone
        _email.value = currentEmail ?: ""
        _address.value = currentAddress ?: ""
        _notes.value = currentNotes ?: ""
        Log.d(TAG, "Set to EDIT mode for contact: $id")

        // Fetch full contact details from repository
        loadFullContactDetails(id)
    }

    /**
     * Load full contact details from repository.
     * This fetches email, address, and notes fields.
     * @param id Contact ID
     */
    private fun loadFullContactDetails(id: String) {
        Log.d(TAG, "Loading full contact details for: $id")
        _isLoading.value = true

        viewModelScope.launch {
            try {
                val contact = repository.getContactById(id)

                if (contact != null) {
                    // Update all fields with fetched data
                    _name.value = contact.name
                    _phoneNumber.value = contact.phoneNumber
                    _email.value = contact.email ?: ""
                    _address.value = contact.address ?: ""
                    _notes.value = contact.notes ?: ""

                    Log.d(TAG, "Successfully loaded full contact details: ${contact.name}")
                } else {
                    Log.w(TAG, "Contact not found with ID: $id")
                    _errorMessage.value = "Contact not found"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading contact details", e)
                _errorMessage.value = "Error loading contact: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Update name field.
     */
    fun updateName(newName: String) {
        _name.value = newName
    }

    /**
     * Update phone number field.
     */
    fun updatePhoneNumber(newPhone: String) {
        _phoneNumber.value = newPhone
    }

    /**
     * Update email field.
     */
    fun updateEmail(newEmail: String) {
        _email.value = newEmail
    }

    /**
     * Update address field.
     */
    fun updateAddress(newAddress: String) {
        _address.value = newAddress
    }

    /**
     * Update notes field.
     */
    fun updateNotes(newNotes: String) {
        _notes.value = newNotes
    }

    /**
     * Validate input fields.
     * @return True if valid, false otherwise
     */
    private fun validateInput(): Boolean {
        val nameValue = _name.value?.trim() ?: ""
        val phoneValue = _phoneNumber.value?.trim() ?: ""

        when {
            nameValue.isEmpty() -> {
                _errorMessage.value = "Please enter a name"
                return false
            }
            phoneValue.isEmpty() -> {
                _errorMessage.value = "Please enter a phone number"
                return false
            }
            phoneValue.length < 10 -> {
                _errorMessage.value = "Phone number must be at least 10 digits"
                return false
            }
            else -> return true
        }
    }

    /**
     * Save contact (create new or update existing).
     */
    fun saveContact() {
        Log.d(TAG, "saveContact() called, isEditMode: $isEditMode")

        if (!validateInput()) {
            Log.w(TAG, "Validation failed")
            return
        }

        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val nameValue = _name.value?.trim() ?: ""
                val phoneValue = _phoneNumber.value?.trim() ?: ""

                // Convert empty strings to null for optional fields
                val emailValue = _email.value?.trim()?.takeIf { it.isNotEmpty() }
                val addressValue = _address.value?.trim()?.takeIf { it.isNotEmpty() }
                val notesValue = _notes.value?.trim()?.takeIf { it.isNotEmpty() }

                Log.d(TAG, "Saving contact - Name: $nameValue, Phone: $phoneValue, Email: $emailValue, Address: $addressValue, Notes: $notesValue")

                val success = if (isEditMode && contactId != null) {
                    // Update existing contact
                    Log.d(TAG, "Updating contact: $contactId")
                    repository.updateContact(
                        contactId = contactId!!,
                        name = nameValue,
                        phoneNumber = phoneValue,
                        email = emailValue,
                        address = addressValue,
                        notes = notesValue
                    )
                } else {
                    // Create new contact
                    Log.d(TAG, "Creating new contact: $nameValue")
                    repository.createContact(
                        name = nameValue,
                        phoneNumber = phoneValue,
                        email = emailValue,
                        address = addressValue,
                        notes = notesValue
                    )
                }

                if (success) {
                    Log.d(TAG, "Contact saved successfully")
                    _isSaved.value = true
                } else {
                    Log.e(TAG, "Failed to save contact")
                    _errorMessage.value = "Failed to save contact. Please try again."
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving contact", e)
                _errorMessage.value = "Error: ${e.message}"
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

    /**
     * Reset saved state.
     */
    fun resetSavedState() {
        _isSaved.value = false
    }
}
