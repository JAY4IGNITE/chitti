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
        assertEquals("iitm.ac.in", LinkScanner.registrableDomain("www.iitm.ac.in"))
        assertEquals("bbc.co.uk", LinkScanner.registrableDomain("news.bbc.co.uk"))
        assertEquals("globo.com.br", LinkScanner.registrableDomain("g1.globo.com.br"))
    }

    // ---- F42: multi-part suffixes ----

    @Test
    fun multiPartSuffixBrandDomainsAreSafe() {
        assertEquals(RiskLevel.SAFE, LinkScanner.scan("https://www.google.co.in/search?q=x").level)
        assertEquals(RiskLevel.SAFE, LinkScanner.scan("https://www.amazon.co.uk/dp/B0TEST").level)
        // Not in the trusted list, but the brand label is the registrable owner, so no subdomain-abuse hit.
        assertEquals(RiskLevel.SAFE, LinkScanner.scan("https://www.microsoft.co.in/").level)
    }

    @Test
    fun typosquatUnderMultiPartSuffixIsCaught() {
        val verdict = LinkScanner.scan("https://sbl.co.in/kyc")
        assertEquals(RiskLevel.DANGER, verdict.level)
        assertTrue(verdict.reasons.any { it.contains("sbi") })
    }

    @Test
    fun brandBuriedUnderMultiPartSuffixIsCaught() {
        val verdict = LinkScanner.scan("https://paytm.evil.co.in/login")
        assertTrue(verdict.level != RiskLevel.SAFE)
        assertTrue(verdict.reasons.any { it.contains("paytm") })
    }

    // ---- F43: unicode homograph ----

    @Test
    fun unicodeHomographIsDanger() {
        // Cyrillic 'а' (U+0430) imitating apple.com — java.net.URI cannot parse this host
        val verdict = LinkScanner.scan("https://аpple.com/login")
        assertEquals(RiskLevel.DANGER, verdict.level)
        assertEquals("xn--pple-43d.com", verdict.host)
        // Original text is preserved for display
        assertEquals("https://аpple.com/login", verdict.url)
    }

    @Test
    fun unicodeHomographKeepsOtherSignals() {
        val withPort = LinkScanner.scan("https://аpple.com:8080/login?q=1#f")
        assertTrue(withPort.reasons.any { it.contains("8080") })
        val withUserInfo = LinkScanner.scan("https://sbi.co.in@аpple.com/verify")
        assertEquals(RiskLevel.DANGER, withUserInfo.level)
        assertTrue(withUserInfo.reasons.any { it.contains("'@' trick") })
    }

    // ---- F44: short-brand typosquat false positives ----

    @Test
    fun shortWordsNearShortBrandsAreNotTyposquats() {
        assertEquals(RiskLevel.SAFE, LinkScanner.scan("https://bio.link/abc").level)
        assertEquals(RiskLevel.SAFE, LinkScanner.scan("https://bio.site/x").level)
        assertEquals(RiskLevel.SAFE, LinkScanner.scan("https://rio.com/").level)
        assertEquals(RiskLevel.SAFE, LinkScanner.scan("https://pay.example.com/").level)
        // genuine short-brand typosquats still fire
        assertTrue(LinkScanner.scan("https://jlo.com/recharge").score >= 45)
        assertTrue(LinkScanner.scan("https://sbl.co.in/login").score >= 45)
    }

    @Test
    fun brandPaddedWithDigitsOrHyphensIsTyposquat() {
        assertTrue(LinkScanner.scan("https://pay-tm.com/offer").score >= 45)
        assertTrue(LinkScanner.scan("https://sbi123.in/").score >= 45)
        assertTrue(LinkScanner.scan("https://phone-pe.co.in/").score >= 45)
    }

    // ---- F46: UPI links that java.net.URI rejects ----

    @Test
    fun upiLinkWithUnencodedSpaceStillGetsUpiChecks() {
        assertEquals(RiskLevel.DANGER, LinkScanner.scan("upi://pay?pn=Shop Name&am=9999").level)
        val ok = LinkScanner.scan("upi://pay?pa=merchant@okaxis&pn=Shop Name&am=100")
        assertEquals("merchant@okaxis", ok.host)
        assertEquals(RiskLevel.CAUTION, ok.level)
    }

    @Test
    fun upiPercentEncodedPayeeIsNotMalformed() {
        val verdict = LinkScanner.scan("upi://pay?pa=merchant%40okaxis&pn=Shop&am=100")
        assertEquals("merchant@okaxis", verdict.host)
        assertTrue(verdict.reasons.none { it.contains("malformed") })
    }

    @Test
    fun garbagePrefixIsNotTreatedAsScheme() {
        // "ht!tp" is not a valid scheme, so the fallback must not invent one
        assertEquals(RiskLevel.CAUTION, LinkScanner.scan("ht!tp://???").level)
        assertEquals("", LinkScanner.scan("ht!tp://???").host)
    }
}
