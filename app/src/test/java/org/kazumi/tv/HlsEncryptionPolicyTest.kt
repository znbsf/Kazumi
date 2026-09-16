package org.kazumi.tv
import org.junit.Assert.*
import org.junit.Test
import org.kazumi.tv.download.*
class HlsEncryptionPolicyTest {
    @Test fun allowsClearAndOrdinaryAes128() {
        HlsEncryptionPolicy.validate("#EXTM3U\n#EXT-X-KEY:METHOD=NONE")
        HlsEncryptionPolicy.validate("#EXT-X-KEY:METHOD=AES-128,URI=\"key,a.bin\",KEYFORMAT=\"identity\"")
    }
    @Test fun rejectsSampleEncryptionAndNonIdentityKeys() {
        for(tag in listOf("#EXT-X-KEY:METHOD=SAMPLE-AES,URI=\"key\"","#EXT-X-SESSION-KEY:METHOD=AES-128,KEYFORMAT=\"com.widevine\",URI=\"key\"")) {
            assertEquals(DownloadFailure.DRM,(runCatching { HlsEncryptionPolicy.validate(tag) }.exceptionOrNull() as DownloadRejected).reason)
        }
    }
    @Test fun quotedCommasCannotSpoofMethodAndDuplicatesFail() {
        assertTrue(runCatching { HlsEncryptionPolicy.validate("#EXT-X-KEY:URI=\"x,METHOD=NONE\",METHOD=SAMPLE-AES") }.isFailure)
        assertTrue(runCatching { HlsEncryptionPolicy.validate("#EXT-X-KEY:METHOD=NONE,METHOD=AES-128") }.isFailure)
        assertTrue(runCatching { HlsEncryptionPolicy.validate("#EXT-X-KEY:METHOD=AES-128,URI=\"unfinished") }.isFailure)
    }
    @Test fun persistedFailureNeverDisplaysUntrustedText() {
        assertEquals(DownloadFailure.OTHER,DownloadFailure.stored("https://example.test/?secret=token"))
        assertEquals(DownloadFailure.LIVE,DownloadFailure.stored("LIVE"))
    }
}
