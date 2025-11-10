package com.example.tv_caller_app.ui.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.tv_caller_app.R
import com.example.tv_caller_app.viewmodel.DialPadViewModel

/**
 * DialPadFragment provides manual number entry via dial pad.
 * Uses MVVM architecture with DialPadViewModel.
 */
class DialPadFragment : Fragment() {

    private val viewModel: DialPadViewModel by viewModels()
    private val TAG = "DialPadFragment"

    private lateinit var numberInput: EditText
    private lateinit var btnClear: TextView
    private lateinit var btnCall: ImageView

    // Number buttons
    private lateinit var btn0: TextView
    private lateinit var btn1: TextView
    private lateinit var btn2: TextView
    private lateinit var btn3: TextView
    private lateinit var btn4: TextView
    private lateinit var btn5: TextView
    private lateinit var btn6: TextView
    private lateinit var btn7: TextView
    private lateinit var btn8: TextView
    private lateinit var btn9: TextView
    private lateinit var btnStar: TextView
    private lateinit var btnHash: TextView

    private var isUpdatingFromViewModel = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(TAG, "onCreateView called")
        return inflater.inflate(R.layout.fragment_dial_pad, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated called")

        initializeViews(view)
        setupTabs(view)
        setupNumberPad()
        setupCallButton()
        setupInputField()
        observeViewModel()
    }

    /**
     * Initialize all view references.
     */
    private fun initializeViews(view: View) {
        numberInput = view.findViewById(R.id.number_input)
        btnClear = view.findViewById(R.id.btn_clear)
        btnCall = view.findViewById(R.id.btn_call)

        btn0 = view.findViewById(R.id.btn_0)
        btn1 = view.findViewById(R.id.btn_1)
        btn2 = view.findViewById(R.id.btn_2)
        btn3 = view.findViewById(R.id.btn_3)
        btn4 = view.findViewById(R.id.btn_4)
        btn5 = view.findViewById(R.id.btn_5)
        btn6 = view.findViewById(R.id.btn_6)
        btn7 = view.findViewById(R.id.btn_7)
        btn8 = view.findViewById(R.id.btn_8)
        btn9 = view.findViewById(R.id.btn_9)
        btnStar = view.findViewById(R.id.btn_star)
        btnHash = view.findViewById(R.id.btn_hash)
    }

    /**
     * Setup tab navigation.
     */
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

        // Dial Pad tab is already active, do nothing
        tabDialPad.setOnClickListener {
            // Already on dial pad page
        }

        // Navigate to Contacts
        tabContacts.setOnClickListener {
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.main_browse_fragment, AllContactsFragment())
                .commit()
        }
    }

    /**
     * Setup number pad button click listeners.
     */
    private fun setupNumberPad() {
        // Number buttons 0-9
        btn0.setOnClickListener { viewModel.addDigit("0") }
        btn1.setOnClickListener { viewModel.addDigit("1") }
        btn2.setOnClickListener { viewModel.addDigit("2") }
        btn3.setOnClickListener { viewModel.addDigit("3") }
        btn4.setOnClickListener { viewModel.addDigit("4") }
        btn5.setOnClickListener { viewModel.addDigit("5") }
        btn6.setOnClickListener { viewModel.addDigit("6") }
        btn7.setOnClickListener { viewModel.addDigit("7") }
        btn8.setOnClickListener { viewModel.addDigit("8") }
        btn9.setOnClickListener { viewModel.addDigit("9") }

        // Special buttons
        btnStar.setOnClickListener { viewModel.addDigit("*") }
        btnHash.setOnClickListener { viewModel.addDigit("#") }

        // Clear button
        btnClear.setOnClickListener {
            viewModel.clearNumber()
        }
    }

    /**
     * Setup call button click listener.
     */
    private fun setupCallButton() {
        btnCall.setOnClickListener {
            viewModel.makeCall()
        }
    }

    /**
     * Setup input field text change listener.
     * Updates ViewModel when user types directly.
     */
    private fun setupInputField() {
        numberInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Only update ViewModel if change came from user input, not from ViewModel update
                if (!isUpdatingFromViewModel) {
                    viewModel.setPhoneNumber(s.toString())
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    /**
     * Observe ViewModel LiveData for UI updates.
     */
    private fun observeViewModel() {
        // Observe phone number changes
        viewModel.phoneNumber.observe(viewLifecycleOwner) { number ->
            // Update EditText only if different to avoid infinite loop
            if (numberInput.text.toString() != number) {
                isUpdatingFromViewModel = true
                numberInput.setText(number)
                numberInput.setSelection(number.length) // Move cursor to end
                isUpdatingFromViewModel = false
            }
            Log.d(TAG, "Phone number updated: $number")
        }

        // Observe call in progress state
        viewModel.isCallInProgress.observe(viewLifecycleOwner) { inProgress ->
            if (inProgress) {
                val number = viewModel.phoneNumber.value ?: ""
                Toast.makeText(
                    requireContext(),
                    "Calling $number...",
                    Toast.LENGTH_SHORT
                ).show()
                Log.d(TAG, "Call initiated to $number")

                // Simulate call duration
                view?.postDelayed({
                    viewModel.endCall()
                }, 2000)
            }
        }

        // Observe error messages
        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                Log.w(TAG, "Error: $it")
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
    }
}
