package com.example.tv_caller_app.ui.adapters

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.RecyclerView
import com.example.tv_caller_app.R
import com.example.tv_caller_app.model.Contact

class ContactListAdapter(
    private val contacts: List<Contact>,
    private val onContactClick: (Contact) -> Unit
) : RecyclerView.Adapter<ContactListAdapter.ContactViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact_list, parent, false)
        return ContactViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val contact = contacts[position]
        holder.bind(contact, onContactClick)
    }

    override fun getItemCount(): Int = contacts.size

    class ContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val contactName: TextView = itemView.findViewById(R.id.contact_name)
        private val contactPhone: TextView = itemView.findViewById(R.id.contact_phone)

        fun bind(contact: Contact, onClick: (Contact) -> Unit) {
            // Set name and phone separately
            contactName.text = contact.name
            contactPhone.text = contact.phoneNumber

            itemView.setOnClickListener { onClick(contact) }

            // Focus change listener for highlighting
            itemView.setOnFocusChangeListener { view, hasFocus ->
                val bg = GradientDrawable()
                if (hasFocus) {
                    bg.setColor("#2196F3".toColorInt()) // Lighter blue when focused
                } else {
                    bg.setColor("#1976D2".toColorInt()) // Original blue
                }
                view.background = bg
            }
        }
    }
}
