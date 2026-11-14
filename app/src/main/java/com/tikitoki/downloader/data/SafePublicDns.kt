package com.tikitoki.downloader.data

import okhttp3.Dns
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.Locale

/** Used by the HTTP client itself, so checked addresses are the addresses it connects to. */
internal class SafePublicDns(private val delegate: Dns = Dns.SYSTEM) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        requireSafeEndpointHost(hostname)
        val addresses = delegate.lookup(hostname)
        if (addresses.isEmpty() || addresses.any { !isPublicAddress(it) })
            throw UnknownHostException("Extractor endpoint resolved to a non-public address")
        return addresses
    }
}

internal fun requireSafeEndpointHost(raw: String) {
    val host = raw.lowercase(Locale.ROOT)
    val numeric = Regex("(?i)(?:0x[0-9a-f]+|[0-9]+)(?:\\.(?:0x[0-9a-f]+|[0-9]+))*")
    require(host.isNotBlank() && host != "localhost" && !host.endsWith(".") &&
        !host.endsWith(".localhost") && !host.endsWith(".local") && !host.endsWith(".internal") &&
        !host.contains(':') && !host.contains('[') && !host.contains(']') && !numeric.matches(host)) {
        "Unsafe extractor endpoint"
    }
}

internal fun isPublicAddress(address: InetAddress): Boolean {
    if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
        address.isSiteLocalAddress || address.isMulticastAddress) return false
    val b = address.address.map { it.toInt() and 255 }
    return when (address) {
        is Inet4Address -> {
            val a = b[0]; val c = b[1]; val d = b[2]
            !(a == 0 || a == 10 || a == 127 || a >= 224 ||
                a == 100 && c in 64..127 || a == 169 && c == 254 ||
                a == 172 && c in 16..31 || a == 192 && c == 168 ||
                a == 192 && c == 0 && d == 0 || a == 192 && c == 0 && d == 2 ||
                a == 198 && c in 18..19 || a == 198 && c == 51 && d == 100 ||
                a == 203 && c == 0 && d == 113)
        }
        is Inet6Address -> {
            // Restrict IPv6 to global unicast; exclude documentation space.
            (b[0] and 0xe0) == 0x20 && !(b[0] == 0x20 && b[1] == 0x01 && b[2] == 0x0d && b[3] == 0xb8)
        }
        else -> false
    }
}
