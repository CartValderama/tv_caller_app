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
 * LoginFragment handles user authentication.
 * Uses MVVM architecture with AuthViewModel.
 */
class LoginFragment : Fragment() {

    private val viewModel: AuthViewModel by viewModels {
        val sessionManager = SessionManager.getInstance(requireContext())
        val authRepository = AuthRepository.getInstance(sessionManager)
        AuthViewModelFactory(authRepository)
    }
    private val TAG = "LoginFragment"

    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: Button
    private lateinit var registerButton: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_login, container, false)

        emailInput = view.findViewById(R.id.email_input)
        passwordInput = view.findViewById(R.id.password_input)
        loginButton = view.findViewById(R.id.login_button)
        registerButton = view.findViewById(R.id.register_button)

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

        emailInput.addTextChangedListener(textWatcher)
        passwordInput.addTextChangedListener(textWatcher)
    }

    /**
     * Validate form and enable/disable login button.
     */
    private fun validateForm() {
        val email = emailInput.text.toString()
        val password = passwordInput.text.toString()

        val isEmailValid = email.isNotBlank() && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
        val isPasswordValid = password.isNotBlank()

        loginButton.isEnabled = isEmailValid && isPasswordValid
    }

    /**
     * Setup click listeners for buttons.
     */
    private fun setupClickListeners() {
        loginButton.setOnClickListener {
            val email = emailInput.text.toString()
            val password = passwordInput.text.toString()

            // Trigger login via ViewModel
            viewModel.login(email, password)
        }

        registerButton.setOnClickListener {
            // Switch to RegisterFragment
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.auth_fragment_container, RegisterFragment())
                .addToBackStack(null)
                .commit()
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
            registerButton.isEnabled = !isLoading
            emailInput.isEnabled = !isLoading
            passwordInput.isEnabled = !isLoading

            // Login button respects both loading state AND form validation
            if (isLoading) {
                loginButton.isEnabled = false
                loginButton.text = "Logging in..."
            } else {
                validateForm() // Re-validate form when loading completes
                loginButton.text = "Login"
            }
        }

        // Observe auth success - navigate to MainActivity
        viewModel.authSuccess.observe(viewLifecycleOwner) { success ->
            if (success) {
                Log.d(TAG, "Login successful, navigating to MainActivity")
                Toast.makeText(requireContext(), "Login successful!", Toast.LENGTH_SHORT).show()

                // Start session refresh manager
                val app = requireActivity().application as com.example.tv_caller_app.TVCallerApplication
                app.startSessionRefresh()

                // Navigate to MainActivity
                val intent = Intent(requireContext(), MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)

                viewModel.resetAuthSuccess()
            }
        }
    }
}
