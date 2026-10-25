package com.joyplayer.iptv

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress

/**
 * Optional secure DNS (DNS over HTTPS). Some internet providers block or break the normal DNS answer for
 * IPTV servers; asking Cloudflare / Google / Quad9 over HTTPS avoids that. If the secure lookup fails the
 * normal system DNS is used, so turning it on can never make things worse.
 */
object SmartDns : Dns {
    /** 0 = off (system DNS), 1 = Cloudflare, 2 = Google, 3 = Quad9 */
    @Volatile var mode = 0

    private val boot by lazy { OkHttpClient.Builder().build() }   // plain client: must not use SmartDns itself
    private val cache = HashMap<Int, DnsOverHttps>()

    private fun build(m: Int): DnsOverHttps? {
        val (url, ips) = when (m) {
            1 -> "https://cloudflare-dns.com/dns-query" to listOf("1.1.1.1", "1.0.0.1")
            2 -> "https://dns.google/dns-query" to listOf("8.8.8.8", "8.8.4.4")
            3 -> "https://dns.quad9.net/dns-query" to listOf("9.9.9.9", "149.112.112.112")
            else -> return null
        }
        return DnsOverHttps.Builder().client(boot).url(url.toHttpUrl())
            .bootstrapDnsHosts(ips.map { InetAddress.getByName(it) })
            .includeIPv6(false)
            .build()
    }

    private fun doh(m: Int): DnsOverHttps? = synchronized(cache) {
        cache[m] ?: build(m)?.also { cache[m] = it }
    }

    /** Lookup with the secure DNS only (for the test button). */
    fun secureLookup(host: String): List<InetAddress> {
        val d = doh(if (mode == 0) 1 else mode) ?: return emptyList()
        return d.lookup(host)
    }

    override fun lookup(hostname: String): List<InetAddress> {
        val m = mode
        if (m != 0) {
            try {
                val r = doh(m)?.lookup(hostname).orEmpty()
                if (r.isNotEmpty()) return r
            } catch (e: Exception) {
                // fall back to the system DNS below
            }
        }
        return Dns.SYSTEM.lookup(hostname)
    }
}
