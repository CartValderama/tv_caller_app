# Signaling Implementation Testing Guide

## Overview
This guide will help you test the WebRTC signaling infrastructure (Day 2 deliverable) before moving to WebRTC peer connection implementation (Day 3+).

---

## Prerequisites

### 1. Build Verification
First, ensure the code compiles without errors:

```bash
./gradlew clean build
```

**Expected Result:**
- Build should succeed with no compilation errors
- Look for: `BUILD SUCCESSFUL` at the end

**Common Issues:**
- ❌ Type mismatch errors → Already fixed in SignalingManager.kt
- ❌ Missing dependencies → Check build.gradle.kts has `realtime-kt:2.0.0`
- ❌ Supabase credentials missing → Verify local.properties has `supabase.url` and `supabase.key`

---

## Testing Setup

### Option A: Two Physical Android TV Devices
- Install app on both devices
- Both must have internet connection
- Both must be logged in as DIFFERENT users

### Option B: One Android TV Device + One Emulator
- Install on physical TV
- Run Android TV emulator on PC
- Log in as different users on each

### Option C: Two Emulators (NOT RECOMMENDED)
- Create 2 Android TV emulators
- Run simultaneously
- May be slow on some PCs

---

## Test Plan

### Phase 1: Basic Channel Subscription (5 minutes)

**Objective:** Verify SignalingManager can subscribe to Realtime channels

#### Steps:
1. **Add test code** to your app (temporarily in MainActivity or a test fragment):

```kotlin
import com.example.tv_caller_app.calling.signaling.SignalingManager
import com.example.tv_caller_app.calling.signaling.SignalingEvent
import com.example.tv_caller_app.auth.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// In onCreate or onViewCreated:
val sessionManager = SessionManager.getInstance(requireContext())
val currentUserId = sessionManager.getCurrentUserId() ?: return

val signalingManager = SignalingManager(currentUserId)

CoroutineScope(Dispatchers.Main).launch {
    // Initialize signaling
    signalingManager.initialize()

    // Observe events
    signalingManager.events.collect { event ->
        when (event) {
            is SignalingEvent.Connected -> {
                Log.i("TEST", "✅ Signaling connected!")
            }
            is SignalingEvent.Error -> {
                Log.e("TEST", "❌ Signaling error: ${event.message}", event.exception)
            }
            else -> {
                Log.d("TEST", "📨 Event received: $event")
            }
        }
    }
}
```

2. **Run app** on Device A
3. **Check logcat** for:

```
SignalingManager: Initializing SignalingManager for user: [userId]
SignalingManager: Subscribed to channel: call:[userId]
TEST: ✅ Signaling connected!
```

**✅ Success Indicators:**
- Log shows "Subscribed to channel: call:[userId]"
- No errors about channel subscription
- SignalingEvent.Connected is emitted

**❌ Failure Indicators:**
- Error: "Failed to initialize SignalingManager"
- No "Subscribed to channel" log
- Realtime connection timeout

**Common Issues:**
- **No userId found** → User not logged in, check SessionManager
- **Realtime connection fails** → Check internet, verify Supabase credentials
- **Channel subscription fails** → Check Supabase dashboard → Realtime is enabled

---

### Phase 2: Message Sending (10 minutes)

**Objective:** Verify messages can be broadcast to another user's channel

#### Steps:
1. **Get User B's ID:**
   - Log in on Device B
   - Note the user ID from logs or database
   - Alternative: Query contacts table for another user

2. **Add send test** on Device A:

```kotlin
// After initialization succeeds:
val targetUserId = "USER_B_ID_HERE"  // Replace with actual User B ID

CoroutineScope(Dispatchers.Main).launch {
    delay(2000)  // Wait 2 seconds for initialization

    Log.i("TEST", "📤 Sending test call offer to: $targetUserId")

    signalingManager.sendCallOffer(
        targetUserId = targetUserId,
        callerName = "Test Caller",
        callerUsername = "test_user",
        offer = "test_sdp_offer_12345",
        mediaType = "audio"
    )

    Log.i("TEST", "✅ Call offer sent!")
}
```

3. **Check logcat on Device A:**

```
TEST: 📤 Sending test call offer to: [userBId]
SignalingManager: Sending call offer to: [userBId]
SignalingManager: Call offer sent to: [userBId]
TEST: ✅ Call offer sent!
```

**✅ Success Indicators:**
- No exceptions thrown
- Logs show "Call offer sent to: [userId]"
- No Supabase connection errors

**❌ Failure Indicators:**
- Error: "Failed to send call offer"
- JsonException or serialization errors
- Channel broadcast fails

---

### Phase 3: Message Receiving (15 minutes)

**Objective:** Verify messages are received on the target user's channel

#### Steps:
1. **Run app on Device B** (logged in as User B)
2. **Add receive test** on Device B:

```kotlin
val sessionManager = SessionManager.getInstance(requireContext())
val currentUserId = sessionManager.getCurrentUserId() ?: return

val signalingManager = SignalingManager(currentUserId)

CoroutineScope(Dispatchers.Main).launch {
    signalingManager.initialize()

    // Observe events
    signalingManager.events.collect { event ->
        when (event) {
            is SignalingEvent.Connected -> {
                Log.i("TEST", "✅ Device B: Signaling connected, waiting for calls...")
            }

            is SignalingEvent.IncomingCall -> {
                Log.i("TEST", "📞 INCOMING CALL!")
                Log.i("TEST", "   Caller ID: ${event.callerId}")
                Log.i("TEST", "   Caller Name: ${event.callerName}")
                Log.i("TEST", "   Caller Username: ${event.callerUsername}")
                Log.i("TEST", "   Offer SDP: ${event.offer}")
                Log.i("TEST", "   Media Type: ${event.mediaType}")
            }

            is SignalingEvent.Error -> {
                Log.e("TEST", "❌ Error: ${event.message}", event.exception)
            }

            else -> {
                Log.d("TEST", "📨 Event: $event")
            }
        }
    }
}
```

3. **Send test from Device A** (repeat Phase 2 send test)
4. **Check logcat on Device B:**

```
SignalingManager: Received message: {"callerId":"...","callerName":"Test Caller",...}
TEST: 📞 INCOMING CALL!
TEST:    Caller ID: [userAId]
TEST:    Caller Name: Test Caller
TEST:    Caller Username: test_user
TEST:    Offer SDP: test_sdp_offer_12345
TEST:    Media Type: audio
```

**✅ Success Indicators:**
- Device B receives IncomingCall event
- All fields are correctly parsed (callerId, callerName, offer, etc.)
- Message arrives within 1-2 seconds of sending

**❌ Failure Indicators:**
- No message received on Device B
- Error: "Failed to parse signaling message"
- Message received but fields are null/empty
- Long delay (>5 seconds) between send and receive

**Common Issues:**
- **No message received:**
  - Check both devices have internet
  - Verify User IDs are correct
  - Check Supabase Realtime is enabled in dashboard
  - Check both devices are subscribed to correct channels

- **Message parsing fails:**
  - Check JSON structure in logs
  - Verify SignalingMessage serialization
  - Check for missing fields

---

### Phase 4: Bidirectional Communication (10 minutes)

**Objective:** Verify both devices can send and receive

#### Test Scenario: Call Answer Flow

1. **Device A sends offer** (already tested in Phase 3)
2. **Device B receives offer** (already tested in Phase 3)
3. **Device B sends answer:**

```kotlin
// On Device B, after receiving IncomingCall event:
when (event) {
    is SignalingEvent.IncomingCall -> {
        Log.i("TEST", "📞 Incoming call, sending answer...")

        signalingManager.sendCallAnswer(
            callerId = event.callerId,
            calleeName = "Test Callee",
            answer = "test_sdp_answer_67890"
        )

        Log.i("TEST", "✅ Answer sent!")
    }
}
```

4. **Device A receives answer:**

```kotlin
// On Device A, add to event observer:
is SignalingEvent.CallAnswered -> {
    Log.i("TEST", "🎉 CALL ANSWERED!")
    Log.i("TEST", "   Answer SDP: ${event.answer}")
}
```

5. **Verify logs:**

**Device B:**
```
TEST: 📞 Incoming call, sending answer...
SignalingManager: Sending call answer to: [userAId]
SignalingManager: Call answer sent to: [userAId]
TEST: ✅ Answer sent!
```

**Device A:**
```
SignalingManager: Received message: {"calleeId":"...","calleeName":"Test Callee",...}
TEST: 🎉 CALL ANSWERED!
TEST:    Answer SDP: test_sdp_answer_67890
```

**✅ Success Indicators:**
- Device A receives CallAnswered event
- Answer SDP is correct
- Round-trip time < 3 seconds

---

### Phase 5: ICE Candidate Exchange (5 minutes)

**Objective:** Verify ICE candidates can be exchanged

#### Test on Device A:

```kotlin
// After receiving CallAnswered:
signalingManager.sendIceCandidate(
    targetUserId = targetUserId,
    candidate = "candidate:1 1 UDP 2130706431 192.168.1.100 54321 typ host",
    sdpMid = "audio",
    sdpMLineIndex = 0
)

Log.i("TEST", "❄️ ICE candidate sent")
```

#### Observe on Device B:

```kotlin
is SignalingEvent.NewIceCandidate -> {
    Log.i("TEST", "❄️ ICE CANDIDATE RECEIVED!")
    Log.i("TEST", "   Candidate: ${event.candidate}")
    Log.i("TEST", "   SDP Mid: ${event.sdpMid}")
    Log.i("TEST", "   SDP MLine Index: ${event.sdpMLineIndex}")
}
```

**✅ Success Indicators:**
- ICE candidate received on Device B
- All fields parsed correctly

---

### Phase 6: Call Termination (5 minutes)

**Objective:** Verify call rejection and ending work

#### Test Call Rejection:

**Device B rejects call:**
```kotlin
is SignalingEvent.IncomingCall -> {
    Log.i("TEST", "Rejecting call...")

    signalingManager.rejectCall(
        callerId = event.callerId,
        reason = "user_declined"
    )
}
```

**Device A observes rejection:**
```kotlin
is SignalingEvent.CallRejected -> {
    Log.i("TEST", "❌ CALL REJECTED: ${event.reason}")
}
```

#### Test Call End:

**Device A ends call:**
```kotlin
signalingManager.endCall(
    otherUserId = targetUserId,
    reason = "user_hangup",
    duration = 120  // 2 minutes
)
```

**Device B observes end:**
```kotlin
is SignalingEvent.CallEnded -> {
    Log.i("TEST", "📴 CALL ENDED: ${event.reason}, Duration: ${event.duration}s")
}
```

**✅ Success Indicators:**
- Rejection/End messages received
- Channels are cleaned up (check cleanup() is called)

---

## What to Look For: Red Flags 🚩

### Critical Issues:
1. **Messages not arriving** → Realtime configuration problem
2. **Long delays (>5s)** → Network or Supabase region issues
3. **JSON parsing errors** → Serialization mismatch
4. **Channel subscription fails** → Supabase auth or permissions issue
5. **Memory leaks** → Check SignalingManager.cleanup() is called on logout

### Warning Signs:
1. **Intermittent message loss** → Network instability
2. **Delayed events** → Device performance issues
3. **Multiple Connected events** → SignalingManager initialized multiple times
4. **"Unknown message type" warnings** → Message format mismatch between devices

---

## Debugging Tips

### 1. Enable Verbose Logging

Add to your test code:
```kotlin
// Before initializing SignalingManager
android.util.Log.isLoggable("SignalingManager", android.util.Log.VERBOSE)
```

### 2. Check Supabase Realtime Dashboard

- Go to Supabase Dashboard → Realtime
- Should see active connections (2 when both devices connected)
- Can monitor messages in real-time

### 3. Use ADB Logcat Filtering

```bash
# Filter for signaling logs only:
adb logcat | grep -E "SignalingManager|TEST"

# For specific device (if multiple):
adb -s <device_id> logcat | grep SignalingManager
```

### 4. Network Inspection

```kotlin
// Add network state check:
val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
val isConnected = cm.activeNetwork != null
Log.i("TEST", "Network connected: $isConnected")
```

---

## Success Criteria Checklist

Before moving to Day 3 (WebRTC implementation), verify:

- [ ] Both devices can initialize SignalingManager
- [ ] Both devices connect to Realtime (SignalingEvent.Connected)
- [ ] Device A can send offer to Device B
- [ ] Device B receives IncomingCall event
- [ ] Device B can send answer to Device A
- [ ] Device A receives CallAnswered event
- [ ] ICE candidates can be exchanged in both directions
- [ ] Call rejection works (CallRejected event)
- [ ] Call ending works (CallEnded event)
- [ ] Message round-trip time < 3 seconds
- [ ] No memory leaks (cleanup() works)
- [ ] No JSON parsing errors
- [ ] Logs are clean (no exceptions)

---

## Next Steps

Once all tests pass:

1. **Remove test code** from MainActivity
2. **Proceed to Day 3:** WebRTC peer connection implementation
3. **Create WebRTCManager.kt** (800+ lines)
4. **Integrate SignalingManager with WebRTC**

---

## Troubleshooting Common Errors

### Error: "Failed to initialize SignalingManager"
- **Cause:** Supabase connection failure
- **Fix:** Check internet, verify credentials in local.properties

### Error: "Channel subscription failed"
- **Cause:** Realtime not enabled in Supabase
- **Fix:** Dashboard → Settings → API → Realtime → Enable

### Error: "JsonDecodingException"
- **Cause:** Message format mismatch
- **Fix:** Ensure both devices have same SignalingMessage.kt version

### No messages received
- **Cause:** Wrong user ID or channel name
- **Fix:** Log channel names, verify format "call:[userId]"

### Messages delayed >5s
- **Cause:** Network or Supabase region latency
- **Fix:** Check Supabase region, test on different network

---

## Performance Expectations

| Metric | Expected | Red Flag |
|--------|----------|----------|
| Channel subscription | < 1s | > 3s |
| Message delivery | < 500ms | > 2s |
| Round-trip (offer→answer) | < 1s | > 5s |
| ICE candidate delivery | < 200ms | > 1s |
| Memory usage (idle) | < 5 MB | > 20 MB |
| Memory usage (active call) | < 10 MB | > 50 MB |

---

## Questions to Answer During Testing

1. **Does signaling work reliably?** → Should be 100% success rate
2. **Is latency acceptable?** → Should be < 1s for most messages
3. **Are errors handled gracefully?** → No crashes, errors logged
4. **Does cleanup work?** → No memory leaks on logout
5. **Is the code production-ready?** → All tests pass consistently

---

## Final Notes

- **Don't skip testing!** WebRTC will fail silently if signaling is broken
- **Test on real devices** when possible (emulators may have network quirks)
- **Test with poor network** to verify error handling
- **Document any issues** for future debugging

Good luck! 🚀
