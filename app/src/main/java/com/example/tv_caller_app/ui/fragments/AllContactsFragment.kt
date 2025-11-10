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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tv_caller_app.R
import com.example.tv_caller_app.auth.SessionManager
import com.example.tv_caller_app.ui.adapters.ContactListAdapter
import com.example.tv_caller_app.viewmodel.ContactsViewModel

/**
 * AllContactsFragment displays all contacts from Supabase database with pagination.
 * Uses MVVM architecture with ContactsViewModel.
 * Uses activity-scoped ViewModel to share data across fragments.
 */
class AllContactsFragment : Fragment() {

    private val viewModel: ContactsViewModel by activityViewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val sessionManager = SessionManager.getInstance(requireContext())
                @Suppress("UNCHECKED_CAST")
                return ContactsViewModel(sessionManager) as T
            }
        }
    }
    private val TAG = "AllContactsFragment"
    private lateinit var contactsList: RecyclerView
    private lateinit var btnAddContact: TextView
    private lateinit var btnPrevPage: TextView
    private lateinit var btnNextPage: TextView
    private lateinit var pageIndicator: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(TAG, "onCreateView called")
        return inflater.inflate(R.layout.fragment_contacts, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated called")

        contactsList = view.findViewById(R.id.contacts_list)
        btnAddContact = view.findViewById(R.id.btn_add_contact)
        btnPrevPage = view.findViewById(R.id.btn_prev_page)
        btnNextPage = view.findViewById(R.id.btn_next_page)
        pageIndicator = view.findViewById(R.id.page_indicator)

        setupTabs(view)
        setupList()
        setupPagination()
        observeViewModel()

        // Load contacts from ViewModel only if not already loaded
        if (viewModel.currentPageContacts.value.isNullOrEmpty()) {
            viewModel.loadAllContacts()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume called - refreshing contacts from database")
        // Refresh contacts whenever we return to this fragment (e.g., after adding/editing)
        viewModel.refreshContacts()
    }

    private fun setupTabs(view: View) {
        val tabQuickDial = view.findViewById<TextView>(R.id.tab_quick_dial)
        val tabDialPad = view.findViewById<TextView>(R.id.tab_dial_pad)
        val tabContacts = view.findViewById<TextView>(R.id.tab_contacts)

        // Navigate to Quick Dial
        tabQuickDial.setOnClickListener {
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.main_browse_fragment, QuickDialFragment())
                .commit()
        }

        // Navigate to Dial Pad
        tabDialPad.setOnClickListener {
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.main_browse_fragment, DialPadFragment())
                .commit()
        }

        // Contacts tab is already active, do nothing
        tabContacts.setOnClickListener {
            // Already on contacts page
        }
    }

    private fun setupList() {
        // Setup linear layout for list
        contactsList.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun setupPagination() {
        // Add Contact button
        btnAddContact.setOnClickListener {
            Log.d(TAG, "Add contact button clicked")
            // Navigate to add contact screen
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.main_browse_fragment, AddEditContactFragment.newInstanceForAdd())
                .addToBackStack(null)
                .commit()
        }

        // Previous page button
        btnPrevPage.setOnClickListener {
            viewModel.previousPage()
        }

        // Next page button
        btnNextPage.setOnClickListener {
            viewModel.nextPage()
        }
    }

    /**
     * Observe ViewModel LiveData for UI updates.
     */
    private fun observeViewModel() {
        // Observe current page contacts
        viewModel.currentPageContacts.observe(viewLifecycleOwner) { contacts ->
            Log.d(TAG, "Current page contacts updated: ${contacts.size} contacts")

            val adapter = ContactListAdapter(contacts) { contact ->
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
            contactsList.adapter = adapter
        }

        // Observe current page number and total pages for indicator
        viewModel.currentPage.observe(viewLifecycleOwner) { currentPage ->
            updatePageIndicator(currentPage)
        }

        viewModel.totalPages.observe(viewLifecycleOwner) { totalPages ->
            updatePageIndicator(viewModel.currentPage.value)
        }

        // Observe pagination button states
        viewModel.canGoPrevious.observe(viewLifecycleOwner) { canGoPrevious ->
            btnPrevPage.isEnabled = canGoPrevious
            btnPrevPage.alpha = if (canGoPrevious) 1.0f else 0.5f
        }

        viewModel.canGoNext.observe(viewLifecycleOwner) { canGoNext ->
            btnNextPage.isEnabled = canGoNext
            btnNextPage.alpha = if (canGoNext) 1.0f else 0.5f
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

    /**
     * Update page indicator text.
     */
    private fun updatePageIndicator(currentPage: Int?) {
        val page = (currentPage ?: 0) + 1
        val total = viewModel.totalPages.value ?: 0
        pageIndicator.text = getString(R.string.page_indicator, page, total)
    }
}
