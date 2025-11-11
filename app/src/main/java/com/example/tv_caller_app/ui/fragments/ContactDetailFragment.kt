package com.example.tv_caller_app.ui.fragments

import android.app.AlertDialog
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
import com.example.tv_caller_app.R
import com.example.tv_caller_app.auth.SessionManager
import com.example.tv_caller_app.ui.activities.MainActivity
import com.example.tv_caller_app.viewmodel.ContactDetailViewModel

/**
 * ContactDetailFragment displays detailed information about a contact.
 * Uses MVVM architecture with ContactDetailViewModel.
 * Uses activity-scoped ViewModel to share data across fragments.
 */
class ContactDetailFragment : Fragment() {

    private val viewModel: ContactDetailViewModel by activityViewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val sessionManager = SessionManager.getInstance(requireContext())
                @Suppress("UNCHECKED_CAST")
                return ContactDetailViewModel(sessionManager) as T
            }
        }
    }
    private val TAG = "ContactDetailFragment"

    private lateinit var contactName: TextView
    private lateinit var contactPhone: TextView
    private lateinit var btnGoBack: TextView
    private lateinit var btnEdit: TextView
    private lateinit var btnCall: TextView
    private lateinit var btnRemoveContact: TextView

    companion object {
        private const val ARG_CONTACT_ID = "contact_id"
        private const val ARG_CONTACT_NAME = "contact_name"
        private const val ARG_CONTACT_PHONE = "contact_phone"

        fun newInstance(contactId: String, contactName: String, contactPhone: String): ContactDetailFragment {
            val fragment = ContactDetailFragment()
            val args = Bundle()
            args.putString(ARG_CONTACT_ID, contactId)
            args.putString(ARG_CONTACT_NAME, contactName)
            args.putString(ARG_CONTACT_PHONE, contactPhone)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_contact_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Hide header (title and logout button)
        (activity as? MainActivity)?.hideHeader()

        // Initialize views
        contactName = view.findViewById(R.id.contact_name)
        contactPhone = view.findViewById(R.id.contact_phone)
        btnGoBack = view.findViewById(R.id.btn_go_back)
        btnEdit = view.findViewById(R.id.btn_edit)
        btnCall = view.findViewById(R.id.btn_call)
        btnRemoveContact = view.findViewById(R.id.btn_remove_contact)

        // Get contact data from arguments and initialize ViewModel
        val id = arguments?.getString(ARG_CONTACT_ID) ?: ""
        val name = arguments?.getString(ARG_CONTACT_NAME) ?: ""
        val phone = arguments?.getString(ARG_CONTACT_PHONE) ?: ""

        viewModel.setContactDetails(id, name, phone)

        // Setup observers and click listeners
        observeViewModel()
        setupClickListeners()
    }

    /**
     * Observe ViewModel LiveData for UI updates.
     */
    private fun observeViewModel() {
        // Observe contact name
        viewModel.contactName.observe(viewLifecycleOwner) { name ->
            contactName.text = name
        }

        // Observe phone number
        viewModel.phoneNumber.observe(viewLifecycleOwner) { phone ->
            contactPhone.text = phone
        }

        // Observe call history
        viewModel.callHistory.observe(viewLifecycleOwner) { history ->
            Log.d(TAG, "Call history updated: ${history.size} records")
            // TODO: Display call history in a RecyclerView
        }

        // Observe call in progress state
        viewModel.isCallInProgress.observe(viewLifecycleOwner) { inProgress ->
            if (inProgress) {
                val name = viewModel.contactName.value ?: "Unknown"
                val phone = viewModel.phoneNumber.value ?: ""
                Toast.makeText(
                    requireContext(),
                    "Calling $name at $phone...",
                    Toast.LENGTH_SHORT
                ).show()
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

        // Observe loading state
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            Log.d(TAG, "Loading state: $isLoading")
            // Disable buttons while loading
            btnRemoveContact.isEnabled = !isLoading
            btnCall.isEnabled = !isLoading
            btnRemoveContact.alpha = if (isLoading) 0.5f else 1.0f
            btnCall.alpha = if (isLoading) 0.5f else 1.0f
        }

        // Observe delete success state
        viewModel.isDeleted.observe(viewLifecycleOwner) { isDeleted ->
            if (isDeleted) {
                Log.d(TAG, "Contact deleted successfully")
                Toast.makeText(
                    requireContext(),
                    "Contact deleted successfully!",
                    Toast.LENGTH_SHORT
                ).show()

                viewModel.resetDeletedState()

                // Show header when going back
                (activity as? MainActivity)?.showHeader()

                // Navigate back to contacts list
                requireActivity().supportFragmentManager.popBackStack()
            }
        }
    }

    /**
     * Setup click listeners for buttons.
     */
    private fun setupClickListeners() {
        btnGoBack.setOnClickListener {
            // Show header when going back
            (activity as? MainActivity)?.showHeader()
            requireActivity().supportFragmentManager.popBackStack()
        }

        btnEdit.setOnClickListener {
            navigateToEditContact()
        }

        btnCall.setOnClickListener {
            viewModel.callContact()
        }

        btnRemoveContact.setOnClickListener {
            showDeleteConfirmationDialog()
        }
    }

    /**
     * Navigate to AddEditContactFragment in edit mode.
     */
    private fun navigateToEditContact() {
        val id = viewModel.contactId.value ?: ""
        val name = viewModel.contactName.value ?: ""
        val phone = viewModel.phoneNumber.value ?: ""

        Log.d(TAG, "Navigating to edit contact: $name (ID: $id)")

        val editFragment = AddEditContactFragment.newInstanceForEdit(
            contactId = id,
            contactName = name,
            contactPhone = phone
        )

        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.main_browse_fragment, editFragment)
            .addToBackStack(null)
            .commit()
    }

    /**
     * Show confirmation dialog before deleting contact.
     */
    private fun showDeleteConfirmationDialog() {
        val contactName = viewModel.contactName.value ?: "this contact"

        AlertDialog.Builder(requireContext())
            .setTitle("Delete Contact")
            .setMessage("Are you sure you want to delete $contactName? This action cannot be undone.")
            .setPositiveButton("Delete") { dialog, _ ->
                Log.d(TAG, "User confirmed deletion")
                viewModel.deleteContact()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                Log.d(TAG, "User cancelled deletion")
                dialog.dismiss()
            }
            .show()
    }
}
