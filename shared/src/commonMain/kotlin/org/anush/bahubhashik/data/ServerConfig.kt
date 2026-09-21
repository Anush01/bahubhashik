package org.anush.bahubhashik.data

/**
 * Where the backend lives.
 *
 * Local development: a phone can't reach "localhost" — that's the phone itself.
 * Use the Mac's LAN address (`ipconfig getifaddr en0`) and keep both on the
 * same Wi-Fi. The Android emulator is the exception: it maps the host to
 * 10.0.2.2.
 */
object ServerConfig {
    var baseUrl: String = "http://192.168.1.5:8787"
}
