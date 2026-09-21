package org.anush.bahubhashik.data

/**
 * Where the backend lives.
 *
 * Points at the deployed service. For local development swap in your Mac's
 * LAN address (`ipconfig getifaddr en0`) and keep the phone on the same
 * Wi-Fi — a phone can't reach "localhost", that's the phone itself. The
 * Android emulator is the exception: it maps the host to 10.0.2.2.
 */
object ServerConfig {
    var baseUrl: String = "https://bahubhashik-api.onrender.com"
}
