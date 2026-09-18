package com.owlcoders.chitti.security

import java.net.URI
import java.util.Locale

/**
 * LinkGuard — 100% on-device URL threat analysis (no network calls, works in airplane mode).
 *
 * Design goals (aligned with Chitti's zero-cloud pitch):
 *  - Pure Kotlin + java.net only, so it is unit-testable on the JVM and adds ~0 ms of I/O.
 *  - Heuristic, explainable scoring: every point added comes with a human-readable reason
 *    that the warning screen shows to the user.
 *  - Tuned for scams common in India: fake KYC/UPI links, bank typosquats, lottery bait,
 *    shortened links forwarded in WhatsApp groups.
 *
 * Verdict levels:
 *  SAFE    (score < 30)  -> open directly
 *  CAUTION (30..59)      -> interstitial with "Proceed" enabled
 *  DANGER  (>= 60)       -> full-screen warning, proceed requires explicit override
 */
object LinkScanner {

    enum class RiskLevel { SAFE, CAUTION, DANGER }

    data class LinkVerdict(
        val url: String,
        val host: String,
        val score: Int,
        val level: RiskLevel,
        val reasons: List<String>
    )

    /** Well-known domains that get a fast-path SAFE verdict (unless a hard red flag fires). */
    private val trustedDomains = setOf(
        "google.com", "youtube.com", "whatsapp.com", "wa.me", "web.whatsapp.com",
        "facebook.com", "instagram.com", "twitter.com", "x.com", "linkedin.com",
        "microsoft.com", "apple.com", "github.com", "wikipedia.org", "telegram.org", "t.me",
        "amazon.in", "amazon.com", "flipkart.com", "myntra.com", "meesho.com",
        "paytm.com", "phonepe.com", "bhimupi.org.in", "npci.org.in",
        "onlinesbi.sbi", "sbi.co.in", "hdfcbank.com", "icicibank.com", "axisbank.com",
        "kotak.com", "irctc.co.in", "indiapost.gov.in", "uidai.gov.in", "incometax.gov.in",
        "zoom.us", "meet.google.com", "maps.google.com", "netflix.com", "spotify.com",
        "reskilll.com", "iqoo.com", "community.iqoo.com"
    )

    /** Brands criminals typosquat most often (checked with edit distance + subdomain abuse). */
    private val protectedBrands = listOf(
        "google", "whatsapp", "facebook", "instagram", "youtube", "amazon", "flipkart",
        "paytm", "phonepe", "gpay", "sbi", "onlinesbi", "hdfc", "hdfcbank", "icici",
        "icicibank", "axisbank", "irctc", "netflix", "microsoft", "apple", "airtel",
        "jio", "uidai", "aadhaar", "incometax"
    )

    private val shortenerDomains = setOf(
        "bit.ly", "tinyurl.com", "t.co", "goo.gl", "cutt.ly", "is.gd", "rb.gy",
        "tiny.cc", "rebrand.ly", "shorturl.at", "lnkd.in", "s.id", "surl.li", "clck.ru"
    )

    private val suspiciousTlds = setOf(
        "xyz", "top", "tk", "ml", "ga", "cf", "gq", "icu", "click", "link", "rest",
        "zip", "mov", "work", "loan", "men", "cam", "quest", "buzz", "monster"
    )

    private val scamKeywords = listOf(
        "kyc", "lottery", "lucky-draw", "luckydraw", "you-won", "youwon", "free-gift",
        "freegift", "claim-prize", "claimprize", "verify-account", "account-blocked",
        "accountblocked", "suspend", "redeem", "cashback-offer", "refund-pending",
        "update-pan", "pan-card", "aadhaar-update", "electricity-bill-due", "sim-block",
        "otp-verify", "reward-points", "gift-card", "work-from-home-earn"
    )

    private val credentialWords = listOf("login", "signin", "sign-in", "verify", "secure", "account", "update", "password", "otp")

    fun scan(rawUrl: String): LinkVerdict {
        val reasons = mutableListOf<String>()
        var score = 0
        val trimmed = rawUrl.trim()

        val uri = runCatching { URI(trimmed) }.getOrNull()
        val scheme = uri?.scheme?.lowercase(Locale.ROOT) ?: ""

        // Non-web schemes: UPI payment links get their own lightweight checks.
        if (scheme == "upi") {
            return scanUpiLink(trimmed)
        }

        // Unparseable or schemeless input — treat with caution rather than crash.
        if (uri == null || uri.host.isNullOrBlank()) {
            return LinkVerdict(trimmed, "", 40, RiskLevel.CAUTION, listOf("The link is malformed or hides its real destination"))
        }

        val host = uri.host.lowercase(Locale.ROOT).removePrefix("www.")
        val registrable = registrableDomain(host)
        val fullUrlLower = trimmed.lowercase(Locale.ROOT)

        // ---- HARD RED FLAGS (fire even for trusted-looking hosts) ----

        // 1. userinfo trick: https://sbi.co.in@evil.xyz/ — browser goes to evil.xyz
        if (!uri.rawUserInfo.isNullOrBlank()) {
            score += 45
            reasons += "Uses an '@' trick — the real destination is '$host', not what appears at the start"
        }

        // 2. Raw IP address instead of a domain
        if (isIpLiteral(host)) {
            score += 40
            reasons += "Points to a raw IP address instead of a named website"
        }

        // 3. Punycode / lookalike unicode domain (e.g. xn--pypal-4ve.com)
        if (host.contains("xn--") || host.any { it.code > 127 }) {
            score += 60
            reasons += "Domain uses lookalike characters to imitate a real website"
        }

        // Trusted fast path — only if no hard red flag fired.
        if (score == 0 && (registrable in trustedDomains || host in trustedDomains)) {
            return LinkVerdict(trimmed, host, 0, RiskLevel.SAFE, listOf("Well-known verified domain"))
        }

        // ---- BRAND IMPERSONATION ----

        val hostLabels = host.split('.')
        val coreLabel = hostLabels.getOrNull(hostLabels.size - 2) ?: host

        val maybeTyposquat = registrable !in trustedDomains && registrable !in shortenerDomains
        for (brand in protectedBrands) {
            // Typosquat: paytrn.com, flipkert.com — close but not equal, and not the real domain.
            // Allowed distance scales with brand length so short brands (sbi, jio) don't
            // false-positive on unrelated short words (e.g. bit.ly vs sbi).
            val maxDist = if (brand.length <= 4) 1 else 2
            val dist = levenshtein(coreLabel, brand)
            if (maybeTyposquat && coreLabel != brand && dist in 1..maxDist && coreLabel.length >= brand.length - 1) {
                score += 45
                reasons += "Domain '$registrable' imitates '$brand' with a small spelling change"
                break
            }
            // Subdomain abuse: paytm.secure-verify.xyz — brand name buried under a stranger's domain.
            if (coreLabel != brand && hostLabels.dropLast(2).any { it == brand } && registrable !in trustedDomains) {
                score += 40
                reasons += "'$brand' appears in the address, but the site actually belongs to '$registrable'"
                break
            }
        }

        // ---- SOFT SIGNALS ----

        if (scheme == "http") {
            score += 15
            reasons += "Connection is not encrypted (http, not https)"
        }

        if (registrable in shortenerDomains) {
            score += 30
            reasons += "Shortened link — the true destination is hidden"
        }

        val tld = hostLabels.lastOrNull() ?: ""
        if (tld in suspiciousTlds) {
            score += 20
            reasons += "Uses '.$tld', a domain ending heavily abused by scammers"
        }

        if (hostLabels.size >= 5) {
            score += 10
            reasons += "Unusually deep chain of subdomains"
        }

        if (uri.port != -1 && uri.port != 80 && uri.port != 443) {
            score += 20
            reasons += "Connects on a non-standard network port (${uri.port})"
        }

        val matchedScamWord = scamKeywords.firstOrNull { fullUrlLower.contains(it) }
        if (matchedScamWord != null) {
            score += 20
            reasons += "Contains bait wording often used in scams ('$matchedScamWord')"
        }

        val credWord = credentialWords.firstOrNull { fullUrlLower.contains(it) }
        if (credWord != null && registrable !in trustedDomains) {
            score += 10
            reasons += "Asks for logins/verification on an unrecognised site"
        }

        if (trimmed.length > 120) {
            score += 5
            reasons += "Abnormally long web address"
        }

        if (Regex("%[0-9a-fA-F]{2}").findAll(trimmed).count() > 6) {
            score += 10
            reasons += "Heavy character encoding, often used to disguise a link"
        }

        val level = toLevel(score)
        if (reasons.isEmpty()) reasons += "No risk signals found"
        return LinkVerdict(trimmed, host, score, level, reasons)
    }

    /** Minimal checks for upi:// deep links (fake payment requests forwarded in groups). */
    private fun scanUpiLink(rawUrl: String): LinkVerdict {
        val reasons = mutableListOf<String>()
        var score = 30 // any payment link deserves a pause by default
        reasons += "This is a payment link — confirm you trust the sender"

        val query = rawUrl.substringAfter('?', "")
        val params = query.split('&').mapNotNull {
            val kv = it.split('=', limit = 2)
            if (kv.size == 2) kv[0].lowercase(Locale.ROOT) to kv[1] else null
        }.toMap()

        val payee = params["pa"] ?: ""
        if (payee.isBlank()) {
            score += 20
            reasons += "Payment link does not declare a payee UPI ID"
        } else if (!Regex("^[A-Za-z0-9._-]{2,}@[A-Za-z]{2,}$").matches(payee)) {
            score += 30
            reasons += "Payee UPI ID '$payee' looks malformed"
        }
        if ((params["am"]?.toDoubleOrNull() ?: 0.0) > 5000.0) {
            score += 15
            reasons += "Requests a large amount (₹${params["am"]})"
        }
        return LinkVerdict(rawUrl, payee, score, toLevel(score), reasons)
    }

    private fun toLevel(score: Int) = when {
        score >= 60 -> RiskLevel.DANGER
        score >= 30 -> RiskLevel.CAUTION
        else -> RiskLevel.SAFE
    }

    private fun isIpLiteral(host: String): Boolean =
        Regex("^\\d{1,3}(\\.\\d{1,3}){3}$").matches(host) || host.startsWith("[") // IPv6

    /**
     * Best-effort registrable domain (eTLD+1). Handles common Indian multi-part suffixes
     * without shipping the full public-suffix list.
     */
    internal fun registrableDomain(host: String): String {
        val multiPartSuffixes = setOf(
            "co.in", "org.in", "gov.in", "ac.in", "net.in", "res.in", "nic.in",
            "co.uk", "org.uk", "com.au", "co.jp"
        )
        val labels = host.split('.')
        if (labels.size <= 2) return host
        val lastTwo = labels.takeLast(2).joinToString(".")
        return if (lastTwo in multiPartSuffixes) labels.takeLast(3).joinToString(".")
        else lastTwo
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val temp = dp[j]
                dp[j] = minOf(
                    dp[j] + 1,
                    dp[j - 1] + 1,
                    prev + if (a[i - 1] == b[j - 1]) 0 else 1
                )
                prev = temp
            }
        }
        return dp[b.length]
    }

    /** Extracts http(s) URLs from free text (used on captured notifications). */
    fun extractUrls(text: String): List<String> =
        Regex("""(?i)\b(?:https?://|www\.)[^\s<>"')\]]+""")
            .findAll(text)
            .map { m -> m.value.trimEnd('.', ',', ';', '!', '?') }
            .map { if (it.startsWith("www.", ignoreCase = true)) "https://$it" else it }
            .distinct()
            .toList()
}
