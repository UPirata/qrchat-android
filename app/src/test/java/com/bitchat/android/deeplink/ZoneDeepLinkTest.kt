package com.bitchat.android.deeplink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ZoneDeepLinkTest {
    @Test fun parsesCustomScheme() {
        assertEquals(ZoneLink("u0nd9h", null), ZoneDeepLink.parse("qrchat://zona/u0nd9h"))
    }
    @Test fun parsesWebLinkWithVenue() {
        assertEquals(ZoneLink("srbj", "Bar Sport"), ZoneDeepLink.parse("https://qradio.grok.me/c/SRBJ?nome=Bar%20Sport"))
    }
    @Test fun rejectsInvalid() {
        assertNull(ZoneDeepLink.parse("qrchat://zona/u0nda"))      // 'a' not in alphabet
        assertNull(ZoneDeepLink.parse("qrchat://zona/u"))          // too short
        assertNull(ZoneDeepLink.parse("qrchat://zona/u0nd9h7kq"))  // too long
        assertNull(ZoneDeepLink.parse("https://qradio.grok.me/x/u0nd"))
        assertNull(ZoneDeepLink.parse("bitchat://verify?x=1"))
        assertNull(ZoneDeepLink.parse(null))
    }
}
