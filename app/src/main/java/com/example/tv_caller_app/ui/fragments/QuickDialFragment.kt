package com.example.tv_caller_app.ui.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tv_caller_app.R
import com.example.tv_caller_app.auth.SessionManager
import com.example.tv_caller_app.ui.adapters.ContactGridAdapter
import com.example.tv_caller_app.viewmodel.QuickDialViewModel

/**
 * QuickDialFragment displays top 4 quick dial contacts based on call history analysis.
 * Uses MVVM architecture with QuickDialViewModel.
 * Uses activity-scoped ViewModel to share data across fragments.
 */
class QuickDialFragment : Fragment() {

    private val viewModel: QuickDialViewModel by activityViewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val sessionManager = SessionManager.getInstance(requireContext())
                @Suppress("UNCHECKED_CAST")
                return QuickDialViewModel(sessionManager) as T
            }
        }
    }
    private val TAG = "QuickDialFragment"
    private lateinit var contactsGrid: RecyclerView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(TAG, "onCreateView called")
        return inflater.inflate(R.layout.fragment_quick_dial, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated called")

        contactsGrid = view.findViewById(R.id.contacts_grid)
        setupTabs(view)
        setupGrid()
        observeViewModel()

        // Load contacts from ViewModel only if not already loaded
        if (viewModel.contacts.value.isNullOrEmpty()) {
            viewModel.loadQuickDialContacts()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume called - refreshing quick dial contacts from database")
        // Refresh contacts whenever we return to this fragment (e.g., after adding/editing)
        viewModel.refreshQuickDialContacts()
    }

    private fun setupTabs(view: View) {
        val tabQuickDial = view.findViewById<TextView>(R.id.tab_quick_dial)
        val tabDialPad = view.findViewById<TextView>(R.id.tab_dial_pad)
        val tabContacts = view.findViewById<TextView>(R.id.tab_contacts)

        // Quick Dial tab is already active, do nothing
        tabQuickDial.setOnClickListener {
            // Already on quick dial page
        }

        // Navigate to Dial Pad
        tabDialPad.setOnClickListener {
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.main_browse_fragment, DialPadFragment())
                .commit()
        }

        // Navigate to Contacts
        tabContacts.setOnClickListener {
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.main_browse_fragment, AllContactsFragment())
                .commit()
        }
    }

    private fun setupGrid() {
        // Setup 3-column grid layout
        contactsGrid.layoutManager = GridLayoutManager(requireContext(), 3)
    }

    /**
     * Observe ViewModel LiveData for UI updates.
     */
    private fun observeViewModel() {
        // Observe contacts list
        viewModel.contacts.observe(viewLifecycleOwner) { contacts ->
            Log.d(TAG, "Contacts LiveData updated with ${contacts.size} contacts")

            if (contacts.isNotEmpty()) {
                // Create and set adapter
                val adapter = ContactGridAdapter(contacts) { contact ->
                    // Navigate to contact detail page
                    val detailFragment = ContactDetailFragment.newInstance(
                        contact.id,
                        contact.name,
                        contact.phoneNumber
                    )
                    requireActivity().supportFragmentManager.beginTransaction()
                        .replace(R.id.main_browse_fragment, detailFragment)
                        .addToBackStack(null)
                        .commit()
                }
                contactsGrid.adapter = adapter
            }
        }

        // Observe error messages
        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                Log.w(TAG, "Error message: $it")
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }

        // Observe loading state (optional - can add progress bar later)
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            Log.d(TAG, "Loading state: $isLoading")
            // TODO: Show/hide loading indicator
        }
    }
}
