# 📞 Call Feature Implementation Plan - TV Caller App

**Created:** January 2025
**Status:** Planning Phase
**Target Version:** 3.0.0

---

## 🎯 Executive Summary

This document outlines the implementation strategy for adding actual calling functionality to the TV Caller app. Given the constraints (Supabase free tier, Android TV platform, MVVM architecture), we'll use a **two-phase approach**:

1. **Phase 1 (Quick Win):** Android Telecom API for basic phone calling
2. **Phase 2 (Future):** WebRTC + Supabase Realtime for VoIP calling

---

## 🤔 Technology Analysis

### Option 1: Android Telecom API ✅ RECOMMENDED FOR PHASE 1

**What it is:** Android's built-in framework for integrating with the phone's native calling system.

**How it works:**
```kotlin
// Simple intent-based calling
val intent = Intent(Intent.ACTION_CALL).apply {
    data = Uri.parse("tel:1234567890")
}
startActivity(intent)
```

**Pros:**
- ✅ **FREE** - No backend costs, no API fees
- ✅ **Simple** - 1-2 days implementation
- ✅ **Native** - Uses device's actual phone capabilities
- ✅ **Reliable** - Proven Android framework
- ✅ **Integrated** - Shows in native call logs, works with Bluetooth/car systems
- ✅ **No Supabase changes** - Works with free tier

**Cons:**
- ❌ Only works on devices with phone capability (phones, some tablets)
- ❌ Android TV devices typically don't have cellular modems
- ❌ Requires CALL_PHONE permission (dangerous permission)
- ❌ User needs active phone plan

**Verdict:** Perfect for Phase 1 - gets calling working quickly for phone users.

---

### Option 2: WebRTC + Supabase Realtime ✅ RECOMMENDED FOR PHASE 2

**What it is:** Peer-to-peer video/audio calling over the internet using WebRTC, with Supabase Realtime as the signaling server.

**How it works:**
```
Device A → Supabase Realtime Channel → Device B
          (signaling: SDP offer/answer, ICE candidates)
              ↓
        WebRTC Peer Connection
        (direct audio/video stream)
```

**Pros:**
- ✅ **FREE** - No per-minute costs, uses Supabase Realtime (included in free tier)
- ✅ **Works on TV** - Internet-based, no cellular needed
- ✅ **App-to-app** - TV Caller to TV Caller calls
- ✅ **Quality** - HD audio, low latency
- ✅ **Future-proof** - Can add video, screen sharing later

**Cons:**
- ❌ **Complex** - 2-3 weeks implementation
- ❌ **Only app-to-app** - Can't call regular phone numbers
- ❌ **NAT issues** - May need STUN/TURN servers (STUN is free, TURN costs money)
- ❌ **Both users need app** - Not useful if contact doesn't have TV Caller

**Verdict:** Great for Phase 2 - enables TV-to-TV calling, but more complex.

---

### Option 3: Twilio Voice SDK ❌ NOT RECOMMENDED

**What it is:** Commercial VoIP service that can call actual phone numbers.

**Pros:**
- Can call real phone numbers from any device
- Easy SDK integration
- Great documentation

**Cons:**
- ❌ **COSTS MONEY** - $0.013-0.085 per minute
- ❌ Not feasible with Supabase free tier
- ❌ Need Twilio account with payment method
- ❌ Monthly costs can add up quickly

**Verdict:** Too expensive for free tier backend.

---

### Option 4: Agora.io ⚠️ MAYBE FOR FUTURE

**What it is:** VoIP platform with generous free tier.

**Pros:**
- 10,000 free minutes/month
- Good SDK
- Can do VoIP calling

**Cons:**
- ⚠️ Still limited by free tier
- ❌ Can't call regular phone numbers (only app-to-app)
- ❌ Requires separate account setup
- ❌ Less control than WebRTC

**Verdict:** Consider if WebRTC proves too complex, but WebRTC is better.

---

## 🎯 Recommended Approach: Two-Phase Strategy

### Phase 1: Android Telecom API (Simple, Works Now)
**Timeline:** 2-3 days
**Effort:** Low
**Impact:** High (for phone users)

### Phase 2: WebRTC + Supabase Realtime (Advanced, Works Everywhere)
**Timeline:** 2-3 weeks
**Effort:** High
**Impact:** Very High (enables TV calling)

---

## 📋 Phase 1: Android Telecom API Implementation

### Step 1: Permissions & Manifest (30 minutes)

**Add to `AndroidManifest.xml`:**
```xml
<!-- Phone calling permissions -->
<uses-permission android:name="android.permission.CALL_PHONE" />

<!-- Check if device has phone capability -->
<uses-feature
    android:name="android.hardware.telephony"
    android:required="false" />
```

**Note:** `android:required="false"` allows app to run on non-phone devices.

---

### Step 2: Create CallManager Class (2 hours)

**Purpose:** Centralized calling logic with permission handling and device capability checks.

**Location:** `app/src/main/java/com/example/tv_caller_app/calling/CallManager.kt`

```kotlin
package com.example.tv_caller_app.calling

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Manages phone calling functionality using Android Telecom API.
 * Handles permission checks, device capability detection, and call initiation.
 *
 * Singleton pattern ensures consistent state across app.
 */
class CallManager private constructor(private val context: Context) {

    private val TAG = "CallManager"

    companion object {
        @Volatile
        private var instance: CallManager? = null

        fun getInstance(context: Context): CallManager {
            return instance ?: synchronized(this) {
                instance ?: CallManager(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Check if device supports phone calling.
     * Returns false on Android TV, tablets without cellular, etc.
     */
    fun isCallingSupported(): Boolean {
        val telephonySupported = context.packageManager.hasSystemFeature(
            PackageManager.FEATURE_TELEPHONY
        )
        Log.d(TAG, "Device telephony support: $telephonySupported")
        return telephonySupported
    }

    /**
     * Check if CALL_PHONE permission is granted.
     */
    fun hasCallPermission(): Boolean {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        Log.d(TAG, "CALL_PHONE permission: $hasPermission")
        return hasPermission
    }

    /**
     * Initiate a phone call.
     * Requires CALL_PHONE permission and telephony support.
     *
     * @param phoneNumber Phone number to call (can include +, -, spaces)
     * @return CallResult indicating success or reason for failure
     */
    fun makeCall(phoneNumber: String): CallResult {
        // Validate input
        val cleanNumber = cleanPhoneNumber(phoneNumber)
        if (cleanNumber.isEmpty()) {
            return CallResult.Error("Invalid phone number")
        }

        // Check device capability
        if (!isCallingSupported()) {
            return CallResult.Error("This device doesn't support phone calling")
        }

        // Check permission
        if (!hasCallPermission()) {
            return CallResult.PermissionRequired
        }

        // Make the call
        return try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$cleanNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Log.d(TAG, "Call initiated to: $cleanNumber")
            CallResult.Success(cleanNumber)
        } catch (e: Exception) {
            Log.e(TAG, "Call failed: ${e.message}", e)
            CallResult.Error(e.message ?: "Failed to make call")
        }
    }

    /**
     * Remove non-digit characters from phone number.
     * Keeps +, as it's valid for international numbers.
     */
    private fun cleanPhoneNumber(phoneNumber: String): String {
        return phoneNumber.filter { it.isDigit() || it == '+' }
    }
}

/**
 * Result of a call attempt.
 */
sealed class CallResult {
    data class Success(val phoneNumber: String) : CallResult()
    data class Error(val message: String) : CallResult()
    object PermissionRequired : CallResult()
}
```

---

### Step 3: Update CallHistoryRepository (1 hour)

**Add method to log outgoing calls:**

```kotlin
/**
 * Log an outgoing call attempt.
 * Called after initiating a call via CallManager.
 */
suspend fun logOutgoingCall(
    contactId: String?,
    phoneNumber: String,
    contactName: String?
): Result<Unit> {
    return try {
        val userId = sessionManager.getUserId()
            ?: return Result.failure(Exception("Not logged in"))

        val callLog = CallHistoryInsert(
            userId = userId,
            contactId = contactId,
            phoneNumber = phoneNumber,
            contactName = contactName,
            callType = "outgoing",
            callDuration = 0, // Duration unknown for Telecom API calls
            callTimestamp = Clock.System.now().toString(),
            notes = null
        )

        supabase.from("call_history").insert(callLog)

        // Invalidate cache to refresh quick dial
        invalidateCache()

        Log.d(TAG, "Outgoing call logged: $phoneNumber")
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to log call: ${e.message}", e)
        Result.failure(e)
    }
}
```

---

### Step 4: Update ContactDetailFragment (1 hour)

**Replace toast with actual calling:**

```kotlin
// Add at class level
private lateinit var callManager: CallManager

// In onViewCreated
callManager = CallManager.getInstance(requireContext())

// In setupClickListeners
btnCall.setOnClickListener {
    val phoneNumber = viewModel.contact.value?.phoneNumber
    if (phoneNumber.isNullOrBlank()) {
        Toast.makeText(requireContext(), "No phone number", Toast.LENGTH_SHORT).show()
        return@setOnClickListener
    }

    when (val result = callManager.makeCall(phoneNumber)) {
        is CallResult.Success -> {
            // Log the call
            viewModel.logOutgoingCall(phoneNumber)
            Toast.makeText(requireContext(), "Calling...", Toast.LENGTH_SHORT).show()
        }
        is CallResult.PermissionRequired -> {
            // Request permission
            requestCallPermission()
        }
        is CallResult.Error -> {
            Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
        }
    }
}

// Permission request
private val callPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
) { isGranted ->
    if (isGranted) {
        // Retry call
        btnCall.performClick()
    } else {
        Toast.makeText(
            requireContext(),
            "Call permission denied. Enable in Settings.",
            Toast.LENGTH_LONG
        ).show()
    }
}

private fun requestCallPermission() {
    callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
}
```

---

### Step 5: Update ContactDetailViewModel (30 minutes)

**Add method to log calls:**

```kotlin
/**
 * Log an outgoing call in the call history.
 */
fun logOutgoingCall(phoneNumber: String) {
    viewModelScope.launch {
        try {
            val contact = _contact.value
            callHistoryRepository.logOutgoingCall(
                contactId = contact?.id,
                phoneNumber = phoneNumber,
                contactName = contact?.name
            )
            Log.d(TAG, "Call logged successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log call: ${e.message}", e)
            // Don't show error to user - logging is non-critical
        }
    }
}
```

---

### Step 6: Update QuickDialFragment (1 hour)

**Same pattern as ContactDetailFragment:**

```kotlin
private lateinit var callManager: CallManager

// In adapter's onBindViewHolder
holder.itemView.setOnClickListener {
    val phoneNumber = contact.phoneNumber

    when (val result = callManager.makeCall(phoneNumber)) {
        is CallResult.Success -> {
            viewModel.logOutgoingCall(contact.id, phoneNumber, contact.name)
        }
        is CallResult.PermissionRequired -> {
            // Request permission (same as ContactDetailFragment)
        }
        is CallResult.Error -> {
            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
        }
    }
}
```

---

### Step 7: Update DialPadFragment (1 hour)

**Call manually entered numbers:**

```kotlin
private lateinit var callManager: CallManager

// In setupClickListeners
btnCall.setOnClickListener {
    val phoneNumber = viewModel.phoneNumber.value
    if (phoneNumber.isNullOrBlank()) {
        Toast.makeText(requireContext(), "Enter a phone number", Toast.LENGTH_SHORT).show()
        return@setOnClickListener
    }

    when (val result = callManager.makeCall(phoneNumber)) {
        is CallResult.Success -> {
            // Log the call (no contact associated)
            viewModel.logOutgoingCall(phoneNumber)
            // Clear dial pad after calling
            viewModel.clearNumber()
        }
        is CallResult.PermissionRequired -> {
            requestCallPermission()
        }
        is CallResult.Error -> {
            Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
        }
    }
}
```

---

### Step 8: Add UI Feedback for Non-Phone Devices (30 minutes)

**Show helpful message on Android TV:**

```kotlin
// In MainActivity.onCreate()
val callManager = CallManager.getInstance(this)
if (!callManager.isCallingSupported()) {
    // Show banner or toast
    Toast.makeText(
        this,
        "Phone calling not available on this device. VoIP calling coming soon!",
        Toast.LENGTH_LONG
    ).show()

    // Optionally: Hide call buttons or show alternative UI
}
```

---

### Step 9: Testing Checklist

**Device Compatibility:**
- [ ] Test on Android phone (should work)
- [ ] Test on Android TV (should show "not supported" message)
- [ ] Test on tablet with cellular (should work)
- [ ] Test on tablet without cellular (should show "not supported")

**Permission Flow:**
- [ ] First call: permission dialog appears
- [ ] Grant permission: call goes through
- [ ] Deny permission: helpful error message
- [ ] Deny with "don't ask again": message to enable in Settings

**Calling Functionality:**
- [ ] Call from contact detail page
- [ ] Call from quick dial
- [ ] Call from dial pad
- [ ] Call with various number formats (+1, dashes, spaces, etc.)
- [ ] Call invalid number (empty, letters, etc.)

**Call Logging:**
- [ ] Outgoing call appears in call_history table
- [ ] Quick dial updates after making call
- [ ] Call log shows correct timestamp
- [ ] Contact is linked if calling from contact

**Error Handling:**
- [ ] No phone capability: friendly error
- [ ] No permission: request permission
- [ ] Invalid number: validation error
- [ ] Network issues: graceful degradation

---

## 📊 Phase 1 Summary

### Time Estimate
- **Total:** 2-3 days
- Permissions & Manifest: 30 min
- CallManager: 2 hours
- CallHistoryRepository: 1 hour
- ContactDetailFragment: 1 hour
- ContactDetailViewModel: 30 min
- QuickDialFragment: 1 hour
- DialPadFragment: 1 hour
- UI feedback: 30 min
- Testing: 4-6 hours

### Pros of This Approach
✅ Quick to implement
✅ Zero backend costs
✅ Works immediately on phones
✅ Uses native Android framework
✅ Integrates with device features
✅ No external dependencies

### Cons of This Approach
❌ Doesn't work on Android TV
❌ Requires phone capability
❌ Can't call from TV to TV

### Next Steps After Phase 1
1. Deploy and test with users on phones
2. Gather feedback
3. Evaluate need for Phase 2 (VoIP)
4. Plan WebRTC implementation

---

## 🚀 Phase 2: WebRTC + Supabase Realtime (Future)

### Overview

**Goal:** Enable TV-to-TV calling using WebRTC for peer-to-peer audio/video.

**Architecture:**
```
┌─────────────┐                    ┌─────────────┐
│   Device A  │                    │   Device B  │
│             │                    │             │
│  WebRTC     │◄──────────────────►│  WebRTC     │
│  PeerConn   │   Direct P2P Audio │  PeerConn   │
│             │   (Low Latency)    │             │
└──────┬──────┘                    └──────┬──────┘
       │                                  │
       │    Signaling (SDP, ICE)         │
       │                                  │
       └────────►┌──────────────┐◄───────┘
                 │  Supabase    │
                 │  Realtime    │
                 │  Channel     │
                 └──────────────┘
```

### Key Components

**1. Supabase Realtime as Signaling Server**
- Use Realtime channels for signaling
- Exchange SDP offers/answers
- Share ICE candidates
- Presence tracking (online/offline)

**2. WebRTC for Audio/Video**
- Peer-to-peer connection
- HD audio codec
- Low latency
- NAT traversal (STUN/TURN)

**3. Call State Management**
- Incoming call notifications
- Busy status
- Call history
- Missed calls

### Implementation Steps (High-Level)

#### Step 1: Setup WebRTC Dependencies
```gradle
implementation "org.webrtc:google-webrtc:1.0.+"
```

#### Step 2: Create WebRTCManager
- Initialize PeerConnection
- Handle audio streams
- Manage ICE candidates
- Create SDP offers/answers

#### Step 3: Create SignalingManager
- Use Supabase Realtime channels
- Send/receive call signals
- Handle presence
- Manage call state

#### Step 4: Update Profile Table
```sql
ALTER TABLE profiles
ADD COLUMN is_online BOOLEAN DEFAULT false,
ADD COLUMN last_seen TIMESTAMPTZ,
ADD COLUMN call_status TEXT CHECK (call_status IN ('available', 'busy', 'in_call'));
```

#### Step 5: Create Calling UI
- Incoming call screen
- Outgoing call screen
- In-call controls (mute, speaker, end)
- Call timer

#### Step 6: Integrate with Existing Contacts
- Add "VoIP Call" button
- Show online status indicators
- Filter contacts by online status

### Challenges & Solutions

**Challenge 1: NAT Traversal**
- **Problem:** Devices behind NAT/firewall can't connect directly
- **Solution:** Use Google's free STUN servers (`stun:stun.l.google.com:19302`)
- **Backup:** If STUN fails, need TURN server (costs money, delay to Phase 3)

**Challenge 2: Signaling Latency**
- **Problem:** Supabase Realtime may have delays
- **Solution:** Optimize channel design, use direct messages
- **Acceptable:** Signaling delays are one-time, audio is P2P after connection

**Challenge 3: Both Users Online**
- **Problem:** Can only call if both users online
- **Solution:** Add presence tracking, show online indicators

**Challenge 4: Complexity**
- **Problem:** WebRTC is complex (SDP, ICE, etc.)
- **Solution:** Use wrapper libraries, extensive testing

### Time Estimate for Phase 2
- **Total:** 2-3 weeks
- WebRTC setup: 3 days
- Signaling with Supabase: 4 days
- UI implementation: 3 days
- Testing & debugging: 5-7 days

### Cost Analysis
- **STUN servers:** FREE (Google)
- **Supabase Realtime:** FREE (included in free tier)
- **Total:** $0/month

---

## 🎯 Recommendation

### Start with Phase 1 (Android Telecom API)

**Reasons:**
1. ✅ **Quick Win:** 2-3 days vs 2-3 weeks
2. ✅ **Zero Cost:** No backend changes, no API fees
3. ✅ **Immediate Value:** Works for phone users right away
4. ✅ **Low Risk:** Uses proven Android framework
5. ✅ **Easy Testing:** Simple to verify functionality

### Move to Phase 2 Only If:
1. User feedback shows demand for TV calling
2. Phase 1 is fully working and tested
3. Team has time for 2-3 week project
4. WebRTC expertise is available

---

## 📝 Next Steps

1. **Review this plan** - Make sure approach fits your needs
2. **Create feature branch** - `git checkout -b feature/calling-phase1`
3. **Implement Phase 1** - Follow steps above
4. **Test thoroughly** - Complete testing checklist
5. **Deploy to testing** - Get user feedback
6. **Evaluate Phase 2** - Based on user demand

---

## 🔐 Security Considerations

### Phase 1 (Telecom API)
- ✅ CALL_PHONE is a dangerous permission (user explicitly grants)
- ✅ Call logs stored securely in Supabase (RLS enforced)
- ✅ No sensitive data transmitted

### Phase 2 (WebRTC)
- ⚠️ Need encryption for audio streams (WebRTC has built-in DTLS-SRTP)
- ⚠️ Signaling must be authenticated (use Supabase auth tokens)
- ⚠️ Rate limit call attempts to prevent abuse
- ⚠️ Validate phone numbers before calling

---

## 📚 Resources

### Phase 1 Resources
- [Android Telecom API Guide](https://developer.android.com/guide/topics/connectivity/telecom)
- [Runtime Permissions](https://developer.android.com/training/permissions/requesting)
- [Intent Actions](https://developer.android.com/reference/android/content/Intent#ACTION_CALL)

### Phase 2 Resources
- [WebRTC Official Site](https://webrtc.org/)
- [Supabase Realtime Docs](https://supabase.com/docs/guides/realtime)
- [WebRTC for Android](https://webrtc.github.io/webrtc-org/native-code/android/)
- [STUN/TURN Servers](https://gist.github.com/sagivo/3a4b2f2c7ac6e1b5267c2f1f59ac6c6b)

---

**Ready to implement?** Let's start with Phase 1! 🚀
