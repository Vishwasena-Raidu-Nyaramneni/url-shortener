package com.vishwasena.urlshortener.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IpHasherTest {

    @Test
    void testHashIpConsistent() {
        String ip = "192.168.1.1";
        String hash1 = IpHasher.hashIp(ip);
        String hash2 = IpHasher.hashIp(ip);
        assertEquals(hash1, hash2);
    }

    @Test
    void testHashIpDifferent() {
        String hash1 = IpHasher.hashIp("192.168.1.1");
        String hash2 = IpHasher.hashIp("192.168.1.2");
        assertNotEquals(hash1, hash2);
    }

    @Test
    void testHashIpIsSha256() {
        String hash = IpHasher.hashIp("127.0.0.1");
        // SHA-256 produces 64 character hex string
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[a-f0-9]+"));
    }

    @Test
    void testHashIpNull() {
        assertNull(IpHasher.hashIp(null));
    }

    @Test
    void testHashIpBlank() {
        assertNull(IpHasher.hashIp("   "));
    }
}
