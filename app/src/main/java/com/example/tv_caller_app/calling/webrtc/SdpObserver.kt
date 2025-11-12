package com.example.tv_caller_app.calling.webrtc

import android.util.Log
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

/**
 * Simple SDP observer for handling SDP callbacks.
 * WebRTC requires this for async operations.
 */
class SimpleSdpObserver(
    private val tag: String = "SimpleSdpObserver"
) : SdpObserver {

    override fun onCreateSuccess(sdp: SessionDescription?) {
        Log.d(tag, "Create SDP success: ${sdp?.type}")
    }

    override fun onSetSuccess() {
        Log.d(tag, "Set SDP success")
    }

    override fun onCreateFailure(error: String?) {
        Log.e(tag, "Create SDP failure: $error")
    }

    override fun onSetFailure(error: String?) {
        Log.e(tag, "Set SDP failure: $error")
    }
}

/**
 * SDP observer with custom callbacks.
 */
class CallbackSdpObserver(
    private val tag: String = "CallbackSdpObserver",
    private val onCreateSuccess: ((SessionDescription) -> Unit)? = null,
    private val onSetSuccess: (() -> Unit)? = null,
    private val onCreateFailure: ((String) -> Unit)? = null,
    private val onSetFailure: ((String) -> Unit)? = null
) : SdpObserver {

    override fun onCreateSuccess(sdp: SessionDescription?) {
        Log.d(tag, "Create SDP success: ${sdp?.type}")
        sdp?.let { onCreateSuccess?.invoke(it) }
    }

    override fun onSetSuccess() {
        Log.d(tag, "Set SDP success")
        onSetSuccess?.invoke()
    }

    override fun onCreateFailure(error: String?) {
        Log.e(tag, "Create SDP failure: $error")
        onCreateFailure?.invoke(error ?: "Unknown error")
    }

    override fun onSetFailure(error: String?) {
        Log.e(tag, "Set SDP failure: $error")
        onSetFailure?.invoke(error ?: "Unknown error")
    }
}
