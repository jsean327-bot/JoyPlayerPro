package com.joyplayer.iptv

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** One HTTP client for the whole app: connections are kept and reused (fast repeated calls), HTTP/2 and gzip are automatic. */
object Http {
    val client: OkHttpClient by lazy {
        val d = Dispatcher().apply { maxRequests = 64; maxRequestsPerHost = 16 }
        OkHttpClient.Builder().dns(SmartDns)
            .dispatcher(d)
            .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)      // big channel / movie lists can take a while to arrive
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}
