package com.owlcoders.chitti

import com.owlcoders.chitti.security.LinkScanner
import com.owlcoders.chitti.security.LinkScanner.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkScannerTest {

    @Test
    fun trustedDomainsAreSafe() {
        assertEquals(RiskLevel.SAFE, LinkScanner.scan("https://google.com/search?q=x").level)
        assertEquals(RiskLevel.SAFE, LinkScanner.scan("https://meet.google.com/abc-defg-hij").level)
        assertEquals(RiskLevel.SAFE, LinkScanner.scan("https://www.amazon.in/dp/B0TEST").level)
        assertEquals(RiskLevel.SAFE, LinkScanner.scan("https://onlinesbi.sbi/").level)
        assertEquals(RiskLevel.SAFE, LinkScanner.scan("https://community.iqoo.com/in/thread/167130").level)
    }

    @Test
    fun ordinaryUnknownHttpsIsSafe() {
        assertEquals(RiskLevel.SAFE, LinkScanner.scan("https://myfriendblog.dev/post/1").level)
    }

    @Test
    fun typosquatsAreDanger() {
        // paytrn.com imitating paytm
        assertEquals(RiskLevel.DANGER, LinkScanner.scan("https://paytrn.com/kyc-update").level)
        // flipkert imitating flipkart
        assertTrue(LinkScanner.scan("https://flipkert.com/big-sale").score >= 45)
    }

    @Test
    fun atSymbolTrickIsDanger() {
        assertEquals(RiskLevel.DANGER, LinkScanner.scan("https://sbi.co.in@203.0.113.7/verify").level)
    }

    @Test
    fun brandBuriedInSubdomainIsDanger() {
        assertEquals(RiskLevel.DANGER, LinkScanner.scan("http://phonepe.secure-verify.xyz/login").level)
    }

    @Test
    fun punycodeLookalikeIsDanger() {
        assertEquals(RiskLevel.DANGER, LinkScanner.scan("https://xn--pypal-4ve.com/signin").level)
    }

    @Test
    fun rawIpWithScamWordIsDanger() {
        assertEquals(RiskLevel.DANGER, LinkScanner.scan("http://192.168.1.55/kyc").level)
    }

    @Test
    fun shortenersAreCaution() {
        assertEquals(RiskLevel.CAUTION, LinkScanner.scan("https://bit.ly/3xyz").level)
        assertEquals(RiskLevel.CAUTION, LinkScanner.scan("https://tinyurl.com/abc").level)
    }

    @Test
    fun malformedUrlIsCautionNotCrash() {
        assertEquals(RiskLevel.CAUTION, LinkScanner.scan("ht!tp://???").level)
    }

    @Test
    fun upiLinksAlwaysPause() {
        // Even a well-formed merchant UPI request should make the user confirm
        val ok = LinkScanner.scan("upi://pay?pa=merchant@okaxis&pn=Shop&am=100")
        assertTrue(ok.level != RiskLevel.SAFE)
        // Missing payee + large amount is a classic fake-payment pattern
        assertEquals(RiskLevel.DANGER, LinkScanner.scan("upi://pay?pn=Shop&am=9999").level)
    }

    @Test
    fun extractUrlsFindsLinksInMessages() {
        val urls = LinkScanner.extractUrls(
            "Fee portal: https://college.edu/pay, also see www.bit.ly/abc now!"
        )
        assertEquals(2, urls.size)
        assertEquals("https://college.edu/pay", urls[0])
        assertEquals("https://www.bit.ly/abc", urls[1])
    }

    @Test
    fun registrableDomainHandlesIndianSuffixes() {
        assertEquals("google.com", LinkScanner.registrableDomain("mail.google.com"))
        assertEquals("sbi.co.in", LinkScanner.registrableDomain("portal.sbi.co.in"))
        assertEquals("uidai.gov.in", LinkScanner.registrableDomain("resident.uidai.gov.in"))
    }
}
