package com.example.tv_caller_app.ui.adapters

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.tv_caller_app.model.Contact

class ContactGridAdapter(
    private val contacts: List<Contact>,
    private val onContactClick: (Contact) -> Unit
) : RecyclerView.Adapter<ContactGridAdapter.ContactViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val contactButton = TextView(parent.context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                240  // Fixed height for buttons
            ).apply {
                setMargins(4, 4, 4, 4)
            }

            // Blue background
            val drawable = GradientDrawable()
            drawable.setColor(Color.parseColor("#1976D2"))
            drawable.cornerRadius = 4f
            background = drawable

            // Text styling
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            maxLines = 2

            // Make focusable for TV
            isFocusable = true
            isFocusableInTouchMode = true

            // Focus change listener for highlighting
            setOnFocusChangeListener { view, hasFocus ->
                val bg = (view as TextView).background as GradientDrawable
                if (hasFocus) {
                    bg.setColor(Color.parseColor("#2196F3")) // Lighter blue when focused
                } else {
                    bg.setColor(Color.parseColor("#1976D2")) // Original blue
                }
            }
        }

        return ContactViewHolder(contactButton)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val contact = contacts[position]
        holder.bind(contact, onContactClick)
    }

    override fun getItemCount(): Int = contacts.size

    class ContactViewHolder(private val textView: TextView) : RecyclerView.ViewHolder(textView) {
        fun bind(contact: Contact, onClick: (Contact) -> Unit) {
            textView.text = contact.name
            textView.setOnClickListener { onClick(contact) }
        }
    }
}
