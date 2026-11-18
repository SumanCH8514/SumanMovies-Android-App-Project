package com.sumanonline.sumanmovies

import com.sumanonline.sumanmovies.data.remote.fourkhdhub.FourKHdHubMobileClient
import com.sumanonline.sumanmovies.data.remote.hdhub4u.HDHub4uMobileClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GreenMotorsTest {
    @Test
    fun testResolvers() = runBlocking {
        val fourk = FourKHdHubMobileClient()
        val hdhub = HDHub4uMobileClient()

        val gmUrl = "https://greenmotors.club/?id=Q1dxTzFnbjhrQm5kMG5lL01tQzcvdXl1cFhjakQxNnVDc0JsT08vR3RFZytFQnJtbWZudEh3ajFxMlJFc2lqSFBkaHlnTDV3RnBic3d6YXNKYkhidEE9PQ=="
        val gmRes = fourk.resolveLockerUrl(gmUrl)
        assertNotNull(gmRes)
        assertTrue(gmRes!!.directFile)

        val hubcdnUrl = "https://hubcdn.club/file/qKhlrI3g8YdA7J0VoWtI0R4ri"
        val cdnRes = fourk.resolveLockerUrl(hubcdnUrl)
        assertNotNull(cdnRes)
        assertTrue(cdnRes!!.directFile)

        val cdnResHdhub = hdhub.resolveLockerUrl(hubcdnUrl)
        assertNotNull(cdnResHdhub)
        assertTrue(cdnResHdhub!!.directFile)
    }
}
