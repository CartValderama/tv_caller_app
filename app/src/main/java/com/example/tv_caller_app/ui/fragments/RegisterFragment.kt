package com.example.tv_caller_app.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.tv_caller_app.R
import com.example.tv_caller_app.auth.SessionManager
import com.example.tv_caller_app.repository.AuthRepository
import com.example.tv_caller_app.ui.activities.MainActivity
import com.example.tv_caller_app.viewmodel.AuthViewModel
import com.example.tv_caller_app.viewmodel.AuthViewModelFactory

/**
 * RegisterFragment handles user registration.
 * Uses MVVM architecture with AuthViewModel.
 */
class RegisterFragment : Fragment() {

    private val viewModel: AuthViewModel by viewModels {
        val sessionManager = SessionManager.getInstance(requireContext())
        val authRepository = AuthRepository.getInstance(sessionManager)
        AuthViewModelFactory(authRepository)
    }
    private val TAG = "RegisterFragment"

    private lateinit var firstNameInput: EditText
    private lateinit var lastNameInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var phoneNumberInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var confirmPasswordInput: EditText
    private lateinit var registerButton: Button
    private lateinit var backToLoginButton: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_register, container, false)

        firstNameInput = view.findViewById(R.id.first_name_input)
        lastNameInput = view.findViewById(R.id.last_name_input)
        emailInput = view.findViewById(R.id.email_input)
        phoneNumberInput = view.findViewById(R.id.phone_number_input)
        passwordInput = view.findViewById(R.id.password_input)
        confirmPasswordInput = view.findViewById(R.id.confirm_password_input)
        registerButton = view.findViewById(R.id.register_button)
        backToLoginButton = view.findViewById(R.id.back_to_login_button)

        setupTextWatchers()
        setupClickListeners()
        observeViewModel()

        // Initial validation check
        validateForm()

        return view
    }

    /**
     * Setup text watchers for real-time validation.
     */
    private fun setupTextWatchers() {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validateForm()
            }
        }

        firstNameInput.addTextChangedListener(textWatcher)
        lastNameInput.addTextChangedListener(textWatcher)
        emailInput.addTextChangedListener(textWatcher)
        phoneNumberInput.addTextChangedListener(textWatcher)
        passwordInput.addTextChangedListener(textWatcher)
        confirmPasswordInput.addTextChangedListener(textWatcher)
    }

    /**
     * Validate form and enable/disable register button.
     * Shows real-time error feedback for invalid inputs.
     */
    private fun validateForm() {
        val firstName = firstNameInput.text.toString()
        val lastName = lastNameInput.text.toString()
        val email = emailInput.text.toString()
        val phoneNumber = phoneNumberInput.text.toString()
        val password = passwordInput.text.toString()
        val confirmPassword = confirmPasswordInput.text.toString()

        val isFirstNameValid = firstName.isNotBlank()
        val isLastNameValid = lastName.isNotBlank()
        val isEmailValid = email.isNotBlank() && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
        val isPhoneValid = phoneNumber.isNotBlank() && phoneNumber.length >= 10
        val isPasswordValid = validatePassword(password)
        val doPasswordsMatch = password == confirmPassword && password.isNotBlank()

        // Show phone number error if user has typed
        if (phoneNumber.isNotEmpty() && !isPhoneValid) {
            phoneNumberInput.error = "Phone number must be at least 10 digits"
        } else {
            phoneNumberInput.error = null
        }

        // Show password mismatch error if user has typed in confirm field
        if (confirmPassword.isNotEmpty() && password.isNotEmpty() && !doPasswordsMatch) {
            confirmPasswordInput.error = "Passwords do not match"
        } else {
            confirmPasswordInput.error = null
        }

        // Show password strength error if user has typed
        if (password.isNotEmpty() && !isPasswordValid) {
            passwordInput.error = "Must be 8+ chars with uppercase, lowercase, number, and special character"
        } else {
            passwordInput.error = null
        }

        registerButton.isEnabled = isFirstNameValid && isLastNameValid && isEmailValid && isPhoneValid && isPasswordValid && doPasswordsMatch
    }

    /**
     * Validate password meets security requirements.
     * Must match AuthViewModel's requirements exactly.
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
     * Setup click listeners for buttons.
     */
    private fun setupClickListeners() {
        registerButton.setOnClickListener {
            val firstName = firstNameInput.text.toString()
            val lastName = lastNameInput.text.toString()
            val fullName = "$firstName $lastName".trim()
            val email = emailInput.text.toString()
            val phoneNumber = phoneNumberInput.text.toString()
            val password = passwordInput.text.toString()
            val confirmPassword = confirmPasswordInput.text.toString()

            // Trigger registration via ViewModel with fullName and phoneNumber
            // Validation is already done by real-time form validation
            viewModel.register(email, password, confirmPassword, fullName, phoneNumber)
        }

        backToLoginButton.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }
    }

    /**
     * Observe ViewModel LiveData for UI updates.
     */
    private fun observeViewModel() {
        // Observe email validation errors
        viewModel.emailError.observe(viewLifecycleOwner) { error ->
            error?.let {
                emailInput.error = it
                viewModel.clearEmailError()
            }
        }

        // Observe password validation errors
        viewModel.passwordError.observe(viewLifecycleOwner) { error ->
            error?.let {
                passwordInput.error = it
                confirmPasswordInput.error = it
                viewModel.clearPasswordError()
            }
        }

        // Observe general error messages
        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                Log.w(TAG, "Error: $it")
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }

        // Observe loading state
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            // Disable all interactive elements during loading
            backToLoginButton.isEnabled = !isLoading
            firstNameInput.isEnabled = !isLoading
            lastNameInput.isEnabled = !isLoading
            emailInput.isEnabled = !isLoading
            phoneNumberInput.isEnabled = !isLoading
            passwordInput.isEnabled = !isLoading
            confirmPasswordInput.isEnabled = !isLoading

            // Register button respects both loading state AND form validation
            if (isLoading) {
                registerButton.isEnabled = false
                registerButton.text = "Creating account..."
            } else {
                validateForm() // Re-validate form when loading completes
                registerButton.text = "Register"
            }
        }

        // Observe email verification requirement (for registration)
        viewModel.emailVerificationRequired.observe(viewLifecycleOwner) { email ->
            email?.let {
                Log.d(TAG, "Registration successful - email verification required for: $email")

                // Show verification message with Toast
                Toast.makeText(
                    requireContext(),
                    "Account created! Verification email sent to:\n$email\n\nPlease verify your email before logging in.",
                    Toast.LENGTH_LONG
                ).show()

                // Navigate back to login after short delay
                view?.postDelayed({
                    requireActivity().supportFragmentManager.popBackStack()
                    viewModel.resetEmailVerification()
                }, 3000) // 3 second delay to read the message
            }
        }

        // Observe auth success - navigate to MainActivity (not used after registration)
        viewModel.authSuccess.observe(viewLifecycleOwner) { success ->
            if (success) {
                Log.d(TAG, "Auth successful, navigating to MainActivity")
                Toast.makeText(requireContext(), "Login successful!", Toast.LENGTH_SHORT).show()

                // Navigate to MainActivity
                val intent = Intent(requireContext(), MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)

                viewModel.resetAuthSuccess()
            }
        }
    }
}
