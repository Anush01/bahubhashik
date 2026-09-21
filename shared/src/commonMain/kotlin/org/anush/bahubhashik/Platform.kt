package org.anush.bahubhashik

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform