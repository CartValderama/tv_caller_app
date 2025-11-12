package com.example.tv_caller_app.calling.webrtc

import org.webrtc.PeerConnection

/**
 * Configuration constants for WebRTC.
 * Optimized for voice calls with video-ready architecture.
 */
object WebRTCConfig {

    /**
     * STUN servers for NAT traversal (connection discovery).
     * Using Google's free public STUN servers.
     */
    val stunServers = listOf(
        "stun:stun.l.google.com:19302",
        "stun:stun1.l.google.com:19302",
        "stun:stun2.l.google.com:19302",
        "stun:stun3.l.google.com:19302",
        "stun:stun4.l.google.com:19302"
    )

    /**
     * TURN servers for relay (fallback if peer-to-peer fails).
     * Leave empty for Phase 1 (free tier).
     * Add in Phase 2 if needed (costs money).
     */
    val turnServers = emptyList<String>()

    /**
     * Create PeerConnection.RTCConfiguration with STUN/TURN servers.
     */
    fun createRTCConfiguration(): PeerConnection.RTCConfiguration {
        val iceServers = mutableListOf<PeerConnection.IceServer>()

        // Add STUN servers
        stunServers.forEach { url ->
            iceServers.add(
                PeerConnection.IceServer.builder(url)
                    .createIceServer()
            )
        }

        // Add TURN servers (if any)
        turnServers.forEach { url ->
            iceServers.add(
                PeerConnection.IceServer.builder(url)
                    .createIceServer()
            )
        }

        return PeerConnection.RTCConfiguration(iceServers).apply {
            // Optimize for continuous connectivity
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY

            // SDP semantics (use unified plan for modern WebRTC)
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
    }

    /**
     * Audio constraints for voice calls.
     */
    object AudioConstraints {
        const val ECHO_CANCELLATION = true
        const val AUTO_GAIN_CONTROL = true
        const val NOISE_SUPPRESSION = true
        const val HIGH_PASS_FILTER = true
        const val TYPED_NOISE_DETECTION = true

        // Audio codec preference (Opus is best)
        const val PREFERRED_CODEC = "opus"

        // Bitrate limits (kbps)
        const val MIN_BITRATE = 16  // 16 kbps minimum
        const val MAX_BITRATE = 128 // 128 kbps maximum
        const val START_BITRATE = 64 // 64 kbps initial
    }

    /**
     * Video constraints for future video calls (Phase 2).
     */
    object VideoConstraints {
        const val WIDTH = 1280
        const val HEIGHT = 720
        const val FPS = 30
        const val MIN_BITRATE = 300  // 300 kbps
        const val MAX_BITRATE = 2000 // 2 Mbps
        const val START_BITRATE = 800 // 800 kbps
    }

    /**
     * Connection timeouts.
     */
    object Timeouts {
        const val CONNECTION_TIMEOUT_MS = 30000L  // 30 seconds
        const val KEEP_ALIVE_INTERVAL_MS = 5000L  // 5 seconds
        const val ANSWER_TIMEOUT_MS = 60000L      // 60 seconds (ringing)
    }

    /**
     * Media stream labels.
     */
    object StreamLabels {
        const val AUDIO_TRACK_ID = "audio_track"
        const val VIDEO_TRACK_ID = "video_track"
        const val STREAM_ID = "tv_caller_stream"
    }
}
