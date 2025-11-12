package com.example.tv_caller_app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.tv_caller_app.auth.SessionManager

/**
 * Factory for creating CallViewModel with dependencies.
 * Required for injecting Context and SessionManager into the ViewModel.
 */
class CallViewModelFactory(
    private val context: Context,
    private val sessionManager: SessionManager
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CallViewModel::class.java)) {
            return CallViewModel(context, sessionManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
