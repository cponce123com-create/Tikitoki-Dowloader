package com.tikitoki.downloader.data

import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException
import org.junit.Assert.*
import org.junit.Test

class SafePublicDnsTest {
    @Test fun rejectsLiteralAndLocalHosts() {
        for (host in listOf("127.0.0.1", "127.1", "2130706433", "0x7f000001", "0177.0.0.1",
                "[::1]", "::ffff:127.0.0.1", "localhost", "foo.localhost", "foo.local", "foo.internal")) {
            assertThrows(host, IllegalArgumentException::class.java) { requireSafeEndpointHost(host) }
        }
        requireSafeEndpointHost("www.youtube.com")
    }

    @Test fun rejectsPrivateAndReservedAddresses() {
        for (address in listOf("127.0.0.1", "10.0.0.1", "100.64.0.1", "169.254.1.1",
                "172.16.0.1", "192.168.1.1", "192.0.2.1", "198.18.0.1",
                "::1", "fc00::1", "fe80::1", "2001:db8::1")) {
            assertFalse(address, isPublicAddress(InetAddress.getByName(address)))
        }
        assertTrue(isPublicAddress(InetAddress.getByName("8.8.8.8")))
        assertTrue(isPublicAddress(InetAddress.getByName("2606:4700:4700::1111")))
    }

    @Test fun rejectsMixedDnsAnswersBeforeConnection() {
        val dns = SafePublicDns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> =
                listOf(InetAddress.getByName("8.8.8.8"), InetAddress.getByName("10.0.0.1"))
        })
        assertThrows(UnknownHostException::class.java) { dns.lookup("example.com") }
        val publicOnly = SafePublicDns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> = listOf(InetAddress.getByName("8.8.8.8"))
        })
        assertEquals(1, publicOnly.lookup("example.com").size)
    }
}
