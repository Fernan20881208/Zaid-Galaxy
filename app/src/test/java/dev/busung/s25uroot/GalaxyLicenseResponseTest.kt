package dev.busung.s25uroot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class GalaxyLicenseResponseTest {
    @Test fun rejectsInvalidLicenseEvenWhenRequestSucceeded() {
        assertFalse(parseGalaxyLicenseResponse(200, """{"ok":true,"valid":false,"reason":"revoked"}""").valid)
    }

    @Test fun acceptsExplicitValidLeaseOnly() {
        val result = parseGalaxyLicenseResponse(200, """{"ok":true,"valid":true,"lease_seconds":120}""")
        assertTrue(result.valid)
        assertEquals(120L, result.leaseSeconds)
        assertFalse(parseGalaxyLicenseResponse(200, """{"ok":true,"valid":true}""").valid)
        assertFalse(parseGalaxyLicenseResponse(503, """{"ok":true,"valid":true,"lease_seconds":120}""").valid)
        assertFalse(parseGalaxyLicenseResponse(200, "not json").valid)
    }
}
