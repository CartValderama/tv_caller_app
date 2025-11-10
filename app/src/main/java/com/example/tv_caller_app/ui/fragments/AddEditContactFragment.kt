package com.example.tv_caller_app.ui.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.tv_caller_app.R
import com.example.tv_caller_app.auth.SessionManager
import com.example.tv_caller_app.viewmodel.AddEditContactViewModel

/**
 * AddEditContactFragment for adding or editing contacts.
 * Uses MVVM architecture with AddEditContactViewModel.
 * Uses activity-scoped ViewModel to share data across fragments.
 */
class AddEditContactFragment : Fragment() {

    private val viewModel: AddEditContactViewModel by activityViewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val sessionManager = SessionManager.getInstance(requireContext())
                @Suppress("UNCHECKED_CAST")
                return AddEditContactViewModel(sessionManager) as T
            }
        }
    }
    private val TAG = "AddEditContactFragment"

    private lateinit var formTitle: TextView
    private lateinit var inputName: EditText
    private lateinit var inputPhone: EditText
    private lateinit var inputEmail: EditText
    private lateinit var inputAddress: EditText
    private lateinit var inputNotes: EditText
    private lateinit var btnSave: TextView
    private lateinit var btnCancel: TextView

    companion object {
        private const val ARG_MODE = "mode"
        private const val ARG_CONTACT_ID = "contact_id"
        private const val ARG_CONTACT_NAME = "contact_name"
        private const val ARG_CONTACT_PHONE = "contact_phone"

        const val MODE_ADD = "add"
        const val MODE_EDIT = "edit"

        /**
         * Create instance for adding new contact.
         */
        fun newInstanceForAdd(): AddEditContactFragment {
            val fragment = AddEditContactFragment()
            val args = Bundle()
            args.putString(ARG_MODE, MODE_ADD)
            fragment.arguments = args
            return fragment
        }

        /**
         * Create instance for editing existing contact.
         */
        fun newInstanceForEdit(
            contactId: String,
            contactName: String,
            contactPhone: String
        ): AddEditContactFragment {
            val fragment = AddEditContactFragment()
            val args = Bundle()
            args.putString(ARG_MODE, MODE_EDIT)
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
        return inflater.inflate(R.layout.fragment_add_edit_contact, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated called")

        // Initialize views
        formTitle = view.findViewById(R.id.form_title)
        inputName = view.findViewById(R.id.input_name)
        inputPhone = view.findViewById(R.id.input_phone)
        inputEmail = view.findViewById(R.id.input_email)
        inputAddress = view.findViewById(R.id.input_address)
        inputNotes = view.findViewById(R.id.input_notes)
        btnSave = view.findViewById(R.id.btn_save)
        btnCancel = view.findViewById(R.id.btn_cancel)

        // Determine mode (add or edit)
        val mode = arguments?.getString(ARG_MODE) ?: MODE_ADD
        if (mode == MODE_EDIT) {
            val contactId = arguments?.getString(ARG_CONTACT_ID) ?: ""
            val contactName = arguments?.getString(ARG_CONTACT_NAME) ?: ""
            val contactPhone = arguments?.getString(ARG_CONTACT_PHONE) ?: ""

            formTitle.text = getString(R.string.edit_contact)
            viewModel.setEditMode(contactId, contactName, contactPhone)
        } else {
            formTitle.text = getString(R.string.add_new_contact)
            viewModel.setAddMode()
        }

        // Setup input listeners
        setupInputListeners()

        // Setup button listeners
        setupButtonListeners()

        // Observe ViewModel
        observeViewModel()
    }

    private fun setupInputListeners() {
        // Name input
        inputName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.updateName(s?.toString() ?: "")
            }
        })

        // Phone input
        inputPhone.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.updatePhoneNumber(s?.toString() ?: "")
            }
        })

        // Email input
        inputEmail.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.updateEmail(s?.toString() ?: "")
            }
        })

        // Address input
        inputAddress.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.updateAddress(s?.toString() ?: "")
            }
        })

        // Notes input
        inputNotes.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.updateNotes(s?.toString() ?: "")
            }
        })
    }

    private fun setupButtonListeners() {
        // Save button
        btnSave.setOnClickListener {
            Log.d(TAG, "Save button clicked")
            viewModel.saveContact()
        }

        // Cancel button
        btnCancel.setOnClickListener {
            Log.d(TAG, "Cancel button clicked")
            // Navigate back to contacts list
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.main_browse_fragment, AllContactsFragment())
                .commit()
        }
    }

    private fun observeViewModel() {
        // Observe name
        viewModel.name.observe(viewLifecycleOwner) { name ->
            if (inputName.text.toString() != name) {
                inputName.setText(name)
            }
        }

        // Observe phone number
        viewModel.phoneNumber.observe(viewLifecycleOwner) { phone ->
            if (inputPhone.text.toString() != phone) {
                inputPhone.setText(phone)
            }
        }

        // Observe email
        viewModel.email.observe(viewLifecycleOwner) { email ->
            if (inputEmail.text.toString() != email) {
                inputEmail.setText(email)
            }
        }

        // Observe address
        viewModel.address.observe(viewLifecycleOwner) { address ->
            if (inputAddress.text.toString() != address) {
                inputAddress.setText(address)
            }
        }

        // Observe notes
        viewModel.notes.observe(viewLifecycleOwner) { notes ->
            if (inputNotes.text.toString() != notes) {
                inputNotes.setText(notes)
            }
        }

        // Observe loading state
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            btnSave.isEnabled = !isLoading
            btnCancel.isEnabled = !isLoading
            Log.d(TAG, "Loading state: $isLoading")
        }

        // Observe success state
        viewModel.isSaved.observe(viewLifecycleOwner) { isSaved ->
            if (isSaved) {
                Log.d(TAG, "Contact saved successfully")
                Toast.makeText(
                    requireContext(),
                    "Contact saved successfully!",
                    Toast.LENGTH_SHORT
                ).show()

                viewModel.resetSavedState()

                // Navigate back to contacts list
                requireActivity().supportFragmentManager.beginTransaction()
                    .replace(R.id.main_browse_fragment, AllContactsFragment())
                    .commit()
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
    }
}
