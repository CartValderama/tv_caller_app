# 📞 Cross-Platform Call Feature Plan - TV Caller App

**Created:** January 2025
**Target:** TV ↔ Phone, TV ↔ TV, Phone ↔ Phone calling
**Challenge:** Enable calling to BOTH app users AND regular phone numbers
**Constraint:** Supabase free tier

---

## 🎯 The Challenge

You want users to be able to:
- 📺 → 📱 **TV calls Phone app**
- 📺 → 📺 **TV calls TV app**
- 📱 → 📺 **Phone app calls TV app**
- 📱 → 📱 **Phone app calls Phone app**
- 📱/📺 → ☎️ **App calls regular phone number** (PSTN)

This requires **TWO types of calling:**
1. **App-to-App (VoIP)** - TV Caller to TV Caller
2. **App-to-PSTN** - TV Caller to regular phone numbers

---

## 💰 The Reality Check

### Option A: Completely Free (App-to-App Only) ❌ NOT WHAT YOU WANT

**What you get:**
- ✅ TV Caller → TV Caller (free via WebRTC)
- ❌ Can't call regular phone numbers
- ❌ Both users MUST have TV Caller app installed

**Cost:** $0/month

**Verdict:** This doesn't meet your requirements (can't call regular phones).

---

### Option B: Hybrid - WebRTC + Twilio ✅ RECOMMENDED

**What you get:**
- ✅ TV Caller → TV Caller (free via WebRTC)
- ✅ TV Caller → Regular phone number (paid via Twilio)
- ✅ Works on TV, Phone, Tablet
- ✅ Best of both worlds

**Cost:**
- App-to-app calls: **$0** (free WebRTC)
- Calls to phone numbers: **$0.0085 - $0.085 per minute**
  - US: $0.0085/min (about 50¢ per hour)
  - International varies by country

**Example monthly cost:**
- 100 minutes of app-to-app: $0
- 100 minutes to phone numbers: $0.85 - $8.50
- **Total: ~$1-10/month** depending on usage

**Verdict:** Best solution - gives you everything you want at reasonable cost.

---

### Option C: WebRTC + Free SIP Provider ⚠️ COMPLEX

**What you get:**
- ✅ TV Caller → TV Caller (free via WebRTC)
- ⚠️ TV Caller → Phone numbers (limited free minutes via SIP provider)
- ⚠️ Very complex setup
- ⚠️ Quality issues common

**Cost:** $0 - $5/month (free tiers exist but limited)

**Verdict:** Too complex, unreliable, not worth the savings.

---

## 🏆 Recommended Solution: Hybrid WebRTC + Twilio

### Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│                    TV Caller App                        │
│                                                         │
│  ┌──────────────┐              ┌─────────────────┐    │
│  │   WebRTC     │              │  Twilio Voice   │    │
│  │   Manager    │              │     SDK         │    │
│  │              │              │                 │    │
│  │ For App-to-  │              │ For App-to-     │    │
│  │ App Calls    │              │ Phone Calls     │    │
│  └──────┬───────┘              └────────┬────────┘    │
│         │                               │             │
└─────────┼───────────────────────────────┼─────────────┘
          │                               │
          │                               │
    ┌─────▼─────┐                  ┌──────▼──────┐
    │ Supabase  │                  │   Twilio    │
    │ Realtime  │                  │   Cloud     │
    │ Signaling │                  │             │
    └─────┬─────┘                  └──────┬──────┘
          │                               │
          │                               │
    ┌─────▼─────────┐              ┌──────▼──────────┐
    │  Other TV     │              │  Regular Phone  │
    │  Caller App   │              │  Numbers (PSTN) │
    │               │              │  Mobile/Landline│
    └───────────────┘              └─────────────────┘
```

### How It Works

**Scenario 1: TV calls another TV Caller app user**
```
TV App → Checks if contact has TV Caller (online in profiles table)
       → Uses WebRTC (FREE, peer-to-peer)
       → Supabase Realtime for signaling
       → Direct audio connection
```

**Scenario 2: TV calls a regular phone number**
```
TV App → Detects phone number not in app (or offline)
       → Uses Twilio Voice SDK
       → Twilio routes call to phone network (PSTN)
       → Regular phone receives call
       → Billed per minute
```

**Smart Routing Logic:**
```kotlin
fun makeCall(contact: Contact) {
    if (contact.isOnlineInApp()) {
        // FREE: Use WebRTC
        webRTCManager.initiateCall(contact.userId)
    } else {
        // PAID: Use Twilio
        twilioManager.makePhoneCall(contact.phoneNumber)
    }
}
```

---

## 📋 Implementation Plan

### Phase 1: Setup Twilio Account (1 hour)

**Step 1.1: Create Twilio Account**
1. Go to [twilio.com](https://www.twilio.com)
2. Sign up (credit card required but free trial available)
3. Get **$15.50 free credit** (good for testing ~2000 minutes)

**Step 1.2: Get Credentials**
```
Account SID: ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
Auth Token: xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
Phone Number: +1234567890 (you'll need to buy one for $1/month)
```

**Step 1.3: Add to local.properties**
```properties
# Existing Supabase
supabase.url=https://your-project.supabase.co
supabase.key=your-anon-key

# New Twilio credentials
twilio.accountSid=ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
twilio.authToken=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
twilio.phoneNumber=+1234567890
```

**Step 1.4: Update build.gradle.kts**
```kotlin
android {
    defaultConfig {
        // Existing config...

        // Add Twilio credentials
        buildConfigField("String", "TWILIO_ACCOUNT_SID", "\"${project.properties["twilio.accountSid"]}\"")
        buildConfigField("String", "TWILIO_AUTH_TOKEN", "\"${project.properties["twilio.authToken"]}\"")
        buildConfigField("String", "TWILIO_PHONE_NUMBER", "\"${project.properties["twilio.phoneNumber"]}\"")
    }
}

dependencies {
    // Existing dependencies...

    // Twilio Voice SDK
    implementation("com.twilio:voice-android:6.1.0")

    // WebRTC (for app-to-app)
    implementation("org.webrtc:google-webrtc:1.0.32006")
}
```

---

### Phase 2: Update Database Schema (30 minutes)

**Add online presence tracking to profiles:**

```sql
-- Track which users are online in the app
ALTER TABLE profiles
ADD COLUMN is_online BOOLEAN DEFAULT false,
ADD COLUMN last_seen TIMESTAMPTZ DEFAULT now(),
ADD COLUMN device_type TEXT CHECK (device_type IN ('phone', 'tablet', 'tv')),
ADD COLUMN fcm_token TEXT; -- For push notifications (future)

-- Create index for fast online lookup
CREATE INDEX idx_profiles_online ON profiles(is_online) WHERE is_online = true;

-- Create presence trigger (update last_seen when user is active)
CREATE OR REPLACE FUNCTION update_user_presence()
RETURNS TRIGGER AS $$
BEGIN
    NEW.last_seen = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER on_profiles_update
    BEFORE UPDATE ON profiles
    FOR EACH ROW
    EXECUTE FUNCTION update_user_presence();
```

**Add call type to call_history:**

```sql
-- Track whether call was app-to-app or app-to-phone
ALTER TABLE call_history
ADD COLUMN call_method TEXT CHECK (call_method IN ('webrtc', 'pstn')) DEFAULT 'pstn';
```

---

### Phase 3: Create TwilioManager (3 hours)

**File:** `app/src/main/java/com/example/tv_caller_app/calling/TwilioManager.kt`

```kotlin
package com.example.tv_caller_app.calling

import android.content.Context
import android.util.Log
import com.twilio.voice.Call
import com.twilio.voice.CallException
import com.twilio.voice.CallInvite
import com.twilio.voice.ConnectOptions
import com.twilio.voice.Voice
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Manages Twilio Voice calls to regular phone numbers.
 * Handles outgoing calls via PSTN (paid per minute).
 *
 * Singleton pattern for consistent state.
 */
class TwilioManager private constructor(
    private val context: Context,
    private val accountSid: String,
    private val authToken: String,
    private val fromNumber: String
) {

    private val TAG = "TwilioManager"
    private var activeCall: Call? = null

    companion object {
        @Volatile
        private var instance: TwilioManager? = null

        fun getInstance(
            context: Context,
            accountSid: String,
            authToken: String,
            fromNumber: String
        ): TwilioManager {
            return instance ?: synchronized(this) {
                instance ?: TwilioManager(
                    context.applicationContext,
                    accountSid,
                    authToken,
                    fromNumber
                ).also { instance = it }
            }
        }
    }

    /**
     * Make a call to a regular phone number via Twilio.
     * This is a PAID call that goes through PSTN.
     *
     * @param toNumber Phone number to call (E.164 format: +1234567890)
     * @return Result with call details or error
     */
    suspend fun makePhoneCall(toNumber: String): CallResult {
        // Validate phone number format
        val cleanNumber = formatPhoneNumber(toNumber)
        if (!isValidPhoneNumber(cleanNumber)) {
            return CallResult.Error("Invalid phone number format. Use +1234567890")
        }

        return suspendCancellableCoroutine { continuation ->
            try {
                Log.d(TAG, "Initiating Twilio call to: $cleanNumber")

                // Get access token from your backend
                // NOTE: In production, you MUST get this from a secure backend endpoint
                // NEVER put credentials in the app
                val accessToken = generateAccessToken() // See below for implementation

                // Configure call
                val connectOptions = ConnectOptions.Builder(accessToken)
                    .params(hashMapOf(
                        "To" to cleanNumber,
                        "From" to fromNumber
                    ))
                    .build()

                // Make the call
                activeCall = Voice.connect(context, connectOptions, object : Call.Listener {
                    override fun onConnectFailure(call: Call, error: CallException) {
                        Log.e(TAG, "Call failed: ${error.message}")
                        activeCall = null
                        continuation.resume(CallResult.Error(error.message ?: "Call failed"))
                    }

                    override fun onRinging(call: Call) {
                        Log.d(TAG, "Call ringing...")
                    }

                    override fun onConnected(call: Call) {
                        Log.d(TAG, "Call connected!")
                        continuation.resume(CallResult.Success(
                            phoneNumber = cleanNumber,
                            callMethod = "pstn",
                            estimatedCostPerMinute = 0.0085 // US rate
                        ))
                    }

                    override fun onReconnecting(call: Call, error: CallException) {
                        Log.w(TAG, "Call reconnecting: ${error.message}")
                    }

                    override fun onReconnected(call: Call) {
                        Log.d(TAG, "Call reconnected")
                    }

                    override fun onDisconnected(call: Call, error: CallException?) {
                        Log.d(TAG, "Call disconnected")
                        activeCall = null
                    }

                    override fun onCallQualityWarningsChanged(
                        call: Call,
                        currentWarnings: Set<Call.CallQualityWarning>,
                        previousWarnings: Set<Call.CallQualityWarning>
                    ) {
                        Log.w(TAG, "Call quality warnings: $currentWarnings")
                    }
                })

                // Handle cancellation
                continuation.invokeOnCancellation {
                    activeCall?.disconnect()
                    activeCall = null
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to initiate call: ${e.message}", e)
                continuation.resume(CallResult.Error(e.message ?: "Failed to make call"))
            }
        }
    }

    /**
     * End the active call.
     */
    fun hangUp() {
        activeCall?.disconnect()
        activeCall = null
        Log.d(TAG, "Call ended")
    }

    /**
     * Check if there's an active call.
     */
    fun hasActiveCall(): Boolean = activeCall != null

    /**
     * Format phone number to E.164 format.
     * E.164: +[country code][number] (e.g., +12025551234)
     */
    private fun formatPhoneNumber(phoneNumber: String): String {
        var cleaned = phoneNumber.filter { it.isDigit() || it == '+' }

        // If no country code, assume US (+1)
        if (!cleaned.startsWith("+")) {
            cleaned = "+1$cleaned"
        }

        return cleaned
    }

    /**
     * Validate phone number is in E.164 format.
     */
    private fun isValidPhoneNumber(phoneNumber: String): Boolean {
        // E.164: + followed by 1-15 digits
        val e164Regex = Regex("^\\+[1-9]\\d{1,14}$")
        return e164Regex.matches(phoneNumber)
    }

    /**
     * Generate Twilio access token.
     *
     * ⚠️ SECURITY WARNING ⚠️
     * This is for TESTING ONLY. In production, you MUST:
     * 1. Create a backend API endpoint (e.g., Supabase Edge Function)
     * 2. Generate token server-side with proper identity
     * 3. Never expose credentials in the app
     *
     * See: https://www.twilio.com/docs/voice/sdks/android/guide/access-tokens
     */
    private fun generateAccessToken(): String {
        // TODO: Replace with actual backend call
        // For now, this is a placeholder
        // You'll need to set up a backend endpoint that generates tokens

        throw NotImplementedError(
            "Access token generation must be implemented. " +
            "See Twilio docs: https://www.twilio.com/docs/voice/sdks/android/guide/access-tokens"
        )
    }
}

/**
 * Result of a Twilio call attempt.
 */
sealed class TwilioCallResult {
    data class Success(
        val phoneNumber: String,
        val callMethod: String,
        val estimatedCostPerMinute: Double
    ) : TwilioCallResult()

    data class Error(val message: String) : TwilioCallResult()
}
```

---

### Phase 4: Create WebRTCManager (4 days)

**File:** `app/src/main/java/com/example/tv_caller_app/calling/WebRTCManager.kt`

This is complex - full implementation would be 500+ lines. Here's the high-level structure:

```kotlin
package com.example.tv_caller_app.calling

import android.content.Context
import org.webrtc.*
import io.github.jan.supabase.realtime.RealtimeChannel

/**
 * Manages WebRTC peer-to-peer calls between TV Caller app users.
 * FREE - no per-minute costs.
 *
 * Uses Supabase Realtime for signaling (SDP exchange, ICE candidates).
 */
class WebRTCManager(
    private val context: Context,
    private val userId: String
) {
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private var signalingChannel: RealtimeChannel? = null

    /**
     * Initialize WebRTC with STUN servers.
     */
    fun initialize() {
        val peerConnectionFactory = createPeerConnectionFactory()

        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        peerConnection = peerConnectionFactory.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                // Implement callbacks...
            }
        )
    }

    /**
     * Initiate call to another app user.
     * @param targetUserId The other user's ID from profiles table
     */
    suspend fun makeCall(targetUserId: String): CallResult {
        // 1. Create offer (SDP)
        // 2. Send offer via Supabase Realtime
        // 3. Wait for answer
        // 4. Exchange ICE candidates
        // 5. Establish peer connection
        // Implementation details in full code...
    }

    /**
     * Answer incoming call.
     */
    suspend fun answerCall(offer: SessionDescription): CallResult {
        // 1. Set remote description (offer)
        // 2. Create answer (SDP)
        // 3. Send answer via Supabase Realtime
        // 4. Exchange ICE candidates
        // Implementation details in full code...
    }

    /**
     * End active call.
     */
    fun hangUp() {
        peerConnection?.close()
        localAudioTrack?.dispose()
        signalingChannel?.unsubscribe()
    }
}
```

---

### Phase 5: Create Unified CallManager (2 hours)

**File:** `app/src/main/java/com/example/tv_caller_app/calling/UnifiedCallManager.kt`

```kotlin
package com.example.tv_caller_app.calling

import android.content.Context
import android.util.Log
import com.example.tv_caller_app.repository.ProfileRepository

/**
 * Unified call manager that intelligently routes calls:
 * - App-to-app: FREE WebRTC
 * - App-to-phone: PAID Twilio
 *
 * Automatically detects if contact is online in app and routes accordingly.
 */
class UnifiedCallManager(
    private val context: Context,
    private val profileRepository: ProfileRepository,
    private val twilioManager: TwilioManager,
    private val webRTCManager: WebRTCManager
) {

    private val TAG = "UnifiedCallManager"

    /**
     * Make a call using the best available method.
     *
     * Decision tree:
     * 1. Check if contact has TV Caller app and is online
     *    → YES: Use WebRTC (FREE)
     *    → NO: Use Twilio (PAID)
     *
     * @param contactId Contact from database
     * @param phoneNumber Fallback phone number
     * @return CallResult with method used and cost info
     */
    suspend fun makeCall(
        contactId: String?,
        phoneNumber: String
    ): UnifiedCallResult {

        // Step 1: Check if contact is online in app
        val isOnlineInApp = if (contactId != null) {
            profileRepository.isUserOnline(contactId)
        } else {
            false
        }

        return if (isOnlineInApp) {
            // FREE: Use WebRTC for app-to-app call
            Log.d(TAG, "Contact online - using FREE WebRTC")
            val result = webRTCManager.makeCall(contactId!!)

            when (result) {
                is CallResult.Success -> UnifiedCallResult.Success(
                    method = CallMethod.WEBRTC,
                    cost = 0.0,
                    message = "Connected via app (FREE)"
                )
                is CallResult.Error -> {
                    // WebRTC failed, fallback to Twilio
                    Log.w(TAG, "WebRTC failed, falling back to Twilio")
                    makeTwilioCall(phoneNumber)
                }
                is CallResult.PermissionRequired -> UnifiedCallResult.PermissionRequired
            }
        } else {
            // PAID: Use Twilio for app-to-phone call
            Log.d(TAG, "Contact offline - using PAID Twilio")
            makeTwilioCall(phoneNumber)
        }
    }

    /**
     * Make Twilio call (paid).
     */
    private suspend fun makeTwilioCall(phoneNumber: String): UnifiedCallResult {
        return when (val result = twilioManager.makePhoneCall(phoneNumber)) {
            is TwilioCallResult.Success -> UnifiedCallResult.Success(
                method = CallMethod.TWILIO_PSTN,
                cost = result.estimatedCostPerMinute,
                message = "Connected via phone network ($${result.estimatedCostPerMinute}/min)"
            )
            is TwilioCallResult.Error -> UnifiedCallResult.Error(result.message)
        }
    }

    /**
     * End active call (works for both WebRTC and Twilio).
     */
    fun hangUp() {
        webRTCManager.hangUp()
        twilioManager.hangUp()
    }
}

/**
 * Call method used.
 */
enum class CallMethod {
    WEBRTC,         // Free app-to-app
    TWILIO_PSTN     // Paid app-to-phone
}

/**
 * Result of unified call attempt.
 */
sealed class UnifiedCallResult {
    data class Success(
        val method: CallMethod,
        val cost: Double,          // Cost per minute (0.0 for WebRTC)
        val message: String
    ) : UnifiedCallResult()

    data class Error(val message: String) : UnifiedCallResult()
    object PermissionRequired : UnifiedCallResult()
}
```

---

### Phase 6: Update ProfileRepository (1 hour)

**Add online presence methods:**

```kotlin
/**
 * Check if a user is currently online in the app.
 * Used to determine if we can use free WebRTC calling.
 */
suspend fun isUserOnline(userId: String): Boolean {
    return try {
        val profile = supabase.from("profiles")
            .select {
                filter {
                    eq("id", userId)
                }
            }
            .decodeSingleOrNull<Profile>()

        profile?.isOnline == true
    } catch (e: Exception) {
        Log.e(TAG, "Failed to check online status: ${e.message}")
        false
    }
}

/**
 * Update current user's online status.
 * Call this when app starts/stops.
 */
suspend fun setOnlineStatus(isOnline: Boolean) {
    try {
        val userId = sessionManager.getUserId() ?: return

        supabase.from("profiles")
            .update({
                set("is_online", isOnline)
                set("last_seen", Clock.System.now().toString())
            }) {
                filter {
                    eq("id", userId)
                }
            }

        Log.d(TAG, "Online status updated: $isOnline")
    } catch (e: Exception) {
        Log.e(TAG, "Failed to update online status: ${e.message}")
    }
}
```

---

### Phase 7: Update UI to Show Call Method & Cost (2 hours)

**Update ContactDetailFragment:**

```kotlin
btnCall.setOnClickListener {
    val contact = viewModel.contact.value ?: return@setOnClickListener
    val phoneNumber = contact.phoneNumber

    // Show loading
    progressBar.visibility = View.VISIBLE
    btnCall.isEnabled = false

    viewModel.makeCall(contact.id, phoneNumber) { result ->
        progressBar.visibility = View.GONE
        btnCall.isEnabled = true

        when (result) {
            is UnifiedCallResult.Success -> {
                val costInfo = if (result.cost > 0) {
                    " • $${result.cost}/min"
                } else {
                    " • FREE"
                }

                Toast.makeText(
                    requireContext(),
                    "${result.message}$costInfo",
                    Toast.LENGTH_SHORT
                ).show()
            }
            is UnifiedCallResult.Error -> {
                Toast.makeText(
                    requireContext(),
                    "Call failed: ${result.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
            is UnifiedCallResult.PermissionRequired -> {
                requestCallPermission()
            }
        }
    }
}
```

**Add visual indicator for online users:**

```xml
<!-- In contact list items -->
<ImageView
    android:id="@+id/online_indicator"
    android:layout_width="12dp"
    android:layout_height="12dp"
    android:src="@drawable/ic_online_dot"
    android:visibility="gone" />
```

```kotlin
// In adapter
if (contact.isOnline) {
    holder.onlineIndicator.visibility = View.VISIBLE
    holder.callCostLabel.text = "FREE call"
} else {
    holder.onlineIndicator.visibility = View.GONE
    holder.callCostLabel.text = "Phone call"
}
```

---

## 💰 Cost Breakdown & Estimates

### Setup Costs
- **Twilio Phone Number:** $1/month
- **Initial Setup:** $0 (use free trial credit)

### Per-Call Costs

**App-to-App Calls (WebRTC):**
- TV → TV: **$0**
- TV → Phone (app): **$0**
- Phone → TV: **$0**
- Phone → Phone (app): **$0**

**App-to-Phone Number Calls (Twilio PSTN):**
- US calls: **$0.0085/minute** (~$0.51/hour)
- International: **$0.02-0.50/minute** (varies by country)

### Monthly Cost Examples

**Light User (Mostly app-to-app):**
- 500 minutes app-to-app: $0
- 50 minutes to phone numbers: $0.43
- **Total: ~$1.43/month**

**Medium User (Mixed):**
- 1000 minutes app-to-app: $0
- 200 minutes to phone numbers: $1.70
- **Total: ~$2.70/month**

**Heavy User (Lots of PSTN):**
- 2000 minutes app-to-app: $0
- 1000 minutes to phone numbers: $8.50
- **Total: ~$9.50/month**

### Cost Optimization Tips

1. **Encourage app installs** - Free calls between users
2. **Show cost before calling** - Let users know which method will be used
3. **Set budget alerts** in Twilio dashboard
4. **Use free tier wisely** - $15.50 credit = ~1800 minutes of testing

---

## 📊 Implementation Timeline

### Week 1: Foundation (2-3 days)
- ✅ Set up Twilio account
- ✅ Update database schema (presence tracking)
- ✅ Create TwilioManager class
- ✅ Test basic Twilio calling

### Week 2: WebRTC (4-5 days)
- ✅ Implement WebRTCManager
- ✅ Set up Supabase Realtime signaling
- ✅ Test app-to-app calls
- ✅ Handle ICE candidates and SDP exchange

### Week 3: Integration (3-4 days)
- ✅ Create UnifiedCallManager
- ✅ Update all call buttons in UI
- ✅ Add online indicators
- ✅ Show cost information

### Week 4: Polish & Testing (4-5 days)
- ✅ In-call UI (mute, speaker, hang up)
- ✅ Call notifications (incoming calls)
- ✅ Error handling and edge cases
- ✅ Comprehensive testing
- ✅ Documentation

**Total Time: 3-4 weeks**

---

## 🔐 Security Considerations

### Critical: Twilio Token Generation

**❌ NEVER DO THIS:**
```kotlin
// DON'T put credentials in app!
val token = "your_auth_token_here" // WRONG!
```

**✅ DO THIS:**
```kotlin
// Generate token server-side
val token = backendApi.getTwilioAccessToken(userId)
```

**Implementation:**
1. Create Supabase Edge Function
2. Edge Function generates Twilio token with user identity
3. App requests token from Edge Function
4. Token expires after 1 hour (security)

Example Edge Function:
```typescript
// supabase/functions/generate-twilio-token/index.ts
import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"
const twilio = require('twilio');

serve(async (req) => {
  // Verify user is authenticated
  const supabaseClient = createClient(...)
  const { data: { user } } = await supabaseClient.auth.getUser()

  if (!user) {
    return new Response('Unauthorized', { status: 401 })
  }

  // Generate Twilio token
  const AccessToken = twilio.jwt.AccessToken;
  const VoiceGrant = AccessToken.VoiceGrant;

  const voiceGrant = new VoiceGrant({
    outgoingApplicationSid: 'YOUR_TWIML_APP_SID',
    incomingAllow: true,
  });

  const token = new AccessToken(
    process.env.TWILIO_ACCOUNT_SID,
    process.env.TWILIO_API_KEY,
    process.env.TWILIO_API_SECRET,
    { identity: user.id }
  );

  token.addGrant(voiceGrant);

  return new Response(
    JSON.stringify({ token: token.toJwt() }),
    { headers: { "Content-Type": "application/json" } }
  )
})
```

---

## 🎯 Decision Time

### Do you want to proceed with this hybrid approach?

**✅ Pros:**
- Achieves your goal (TV ↔ Phone calling)
- Mostly free (app-to-app via WebRTC)
- Reasonable cost for PSTN calls (~$1-10/month)
- Best user experience
- Future-proof (can add video later)

**❌ Cons:**
- More complex than single solution
- Requires Twilio account (credit card needed)
- Monthly costs for phone number + usage
- 3-4 weeks implementation time

### Alternative: WebRTC Only (Free but Limited)

If costs are a concern, you could start with **WebRTC-only**:
- ✅ Completely free
- ✅ TV ↔ TV works
- ✅ Phone app ↔ TV works
- ❌ Can't call regular phone numbers
- ❌ Both users must have app

**This would take 2-3 weeks and cost $0.**

---

## 📋 Next Steps

1. **Decide on approach:**
   - Option A: Hybrid (WebRTC + Twilio) - Full cross-platform
   - Option B: WebRTC only - Free but app-to-app only

2. **If Option A (Hybrid):**
   - Create Twilio account
   - Add credentials to local.properties
   - Start with Week 1 tasks

3. **If Option B (WebRTC only):**
   - Skip Twilio setup
   - Focus on WebRTC implementation
   - User can only call other app users

**Which approach do you prefer?** 🤔
