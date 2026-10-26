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

    // last answers that worked (kept on the device): used when every DNS fails, e.g. "No address associated with hostname"
    private var store: android.content.SharedPreferences? = null
    private val lastGood = java.util.concurrent.ConcurrentHashMap<String, List<String>>()

    fun init(ctx: android.content.Context) {
        if (store != null) return
        val p = ctx.applicationContext.getSharedPreferences("joyplayer_dns", android.content.Context.MODE_PRIVATE)
        store = p
        for ((k, v) in p.all) (v as? String)?.let { lastGood[k] = it.split(",").filter { x -> x.isNotBlank() } }
    }

    private fun remember(host: String, list: List<InetAddress>) {
        val ips = list.mapNotNull { it.hostAddress }
        if (ips.isEmpty() || lastGood[host] == ips) return
        lastGood[host] = ips
        store?.edit()?.putString(host, ips.joinToString(","))?.apply()
    }

    private fun tryDoh(m: Int, host: String): List<InetAddress>? = try {
        doh(m)?.lookup(host)?.takeIf { it.isNotEmpty() }
    } catch (e: Exception) { null }

    override fun lookup(hostname: String): List<InetAddress> {
        val m = mode
        var sysError: Exception? = null
        if (m != 0) tryDoh(m, hostname)?.let { remember(hostname, it); return it }
        try {
            val r = Dns.SYSTEM.lookup(hostname)
            if (r.isNotEmpty()) { remember(hostname, r); return r }
        } catch (e: Exception) {
            sysError = e
        }
        // the normal DNS failed: ask secure DNS servers by themselves, then use the last good answer
        for (alt in listOf(1, 2, 3)) {
            if (alt == m) continue
            tryDoh(alt, hostname)?.let {
                AppLog.add("dns", "system DNS failed for $hostname, secure DNS answered")
                remember(hostname, it); return it
            }
        }
        lastGood[hostname]?.takeIf { it.isNotEmpty() }?.let { ips ->
            AppLog.add("dns", "all DNS failed for $hostname, using the last known address")
            return ips.map { InetAddress.getByName(it) }
        }
        throw (sysError ?: java.net.UnknownHostException(hostname))
    }
}
