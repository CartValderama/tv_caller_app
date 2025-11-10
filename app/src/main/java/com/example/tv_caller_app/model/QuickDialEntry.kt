package com.example.tv_caller_app.model

/**
 * QuickDialEntry represents a contact in the quick dial list.
 * This combines Contact data with quick dial metadata (position, score).
 */
data class QuickDialEntry(
    val contact: Contact,
    val position: Int,
    val score: Double = 0.0,
    val callCount: Int = 0
) {
    companion object {
        const val MAX_QUICK_DIAL_SLOTS = 4
    }
}
