package com.hmdm.launcher.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WifiSetterTest {
    @Test
    public void allowList() {
        assertTrue(WifiSetter.csvContains("a.b, C.D", "c.d"));
        assertFalse(WifiSetter.csvContains("a.b", "a.bc"));
        assertFalse(WifiSetter.csvContains("", ""));
    }

    @Test
    public void sha256() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", WifiSetter.sha256Hex("abc".getBytes()));
    }
}
