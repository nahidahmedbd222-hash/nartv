package com.example

import android.net.Uri
import android.util.Log

object AdBlocker {
    private const val TAG = "AdBlocker"

    // List of common ad/tracker domains and patterns
    private val AD_DOMAINS = hashSetOf(
        "doubleclick.net",
        "googleads.g.doubleclick.net",
        "googlesyndication.com",
        "googletagservices.com",
        "google-analytics.com",
        "pagead2.googlesyndication.com",
        "pubads.g.doubleclick.net",
        "adservice.google.com",
        "adnxs.com",
        "adsrvr.org",
        "quantserve.com",
        "popads.net",
        "popcash.net",
        "yandex.ru",
        "onclickads.net",
        "exoclick.com",
        "propellerads.com",
        "juicyads.com",
        "adsterra.com",
        "mgid.com",
        "outbrain.com",
        "taboola.com",
        "revcontent.com",
        "scorecardresearch.com",
        "pubmatic.com",
        "rubiconproject.com",
        "openx.net",
        "casalemedia.com",
        "criteo.com",
        "bet365.com",
        "1xbet.com",
        "adbrau.com",
        "yepdirect.com",
        "cpx24.com",
        "revenuehits.com",
        "bidvertiser.com",
        "popmyads.com",
        "provesrc.com",
        "adpushup.com",
        "buysellads.com",
        "adform.net",
        "smartadserver.com",
        "exponential.com",
        "clickase.com",
        "directrev.com",
        "nativeads.com",
        "infolinks.com",
        "chitika.net",
        "epom.com",
        "adriver.ru",
        "popunder.net",
        "propellerclick.com",
        "onclickpredictativ.com",
        "adkeeper.com",
        "adzerk.net",
        "adverticum.net",
        "advg.ru",
        "admicro.vn",
        "adtech.de",
        "bannerbank.ru",
        "begun.ru",
        "octasell.com"
    )

    // Substrings that if found in URL indicate an ad
    private val AD_KEYWORDS = listOf(
        "googleads",
        "/ads/",
        "pagead",
        "adserver",
        "adservice",
        "adsystem",
        "analytics",
        "advertement",
        "advertisement",
        "advertising",
        "tracking",
        "telemetry",
        "popup",
        "popunder",
        "bannerad",
        "ad_type=",
        "ad_box",
        "ad-box",
        "ad-machina",
        "ad-delivery",
        "ad_delivery-",
        "adhost",
        "clicks.poker",
        "click.php",
        "adlink",
        "adclick",
        "histats.com",
        "statcounter.com",
        "hotjar.com"
    )

    fun isAdUrl(url: String, customDomains: Set<String> = emptySet()): Boolean {
        val uri = try {
            Uri.parse(url)
        } catch (e: Exception) {
            return false
        }
        val host = uri.host?.lowercase() ?: return false

        // Check custom user-defined blocklist
        for (customDomain in customDomains) {
            val trimmed = customDomain.trim().lowercase()
            if (trimmed.isNotEmpty() && (host == trimmed || host.endsWith(".$trimmed"))) {
                Log.d(TAG, "Blocked Ad (Custom Domain rule): $url")
                return true
            }
        }

        // Check exact or subdomain domain match
        for (adDomain in AD_DOMAINS) {
            if (host == adDomain || host.endsWith(".$adDomain")) {
                Log.d(TAG, "Blocked Ad (Domain): $url")
                return true
            }
        }

        // Check keyword match in path or query
        val urlLower = url.lowercase()
        for (keyword in AD_KEYWORDS) {
            if (urlLower.contains(keyword)) {
                // Ignore safe domains that might have ad-like keywords
                if (!isSafeOverride(host)) {
                    Log.d(TAG, "Blocked Ad (Keyword '$keyword'): $url")
                    return true
                }
            }
        }

        return false
    }

    private fun isSafeOverride(host: String): Boolean {
        // Don't block core features of rootittv or trusted CDN/hosting providers
        return host.contains("rootitsystem.com") || 
               host.contains("youtube.com") || 
               host.contains("youtu.be") || 
               host.contains("vimeo.com") ||
               host.contains("cloudflare.com") ||
               host.contains("google.com") && !host.contains("adservice") && !host.contains("googleads")
    }

    // Custom CSS to inject to hide empty ad spaces
    val CSS_BLOCK_STYLES = """
        .advertisement, .ad-box, .adsbygoogle, [class*="ad-"], [id*="ad-"], 
        iframe[src*="doubleclick"], iframe[src*="googleads"], 
        div[id*="pop"], div[class*="pop"], div[id*="banner"], div[class*="banner"] {
            display: none !important;
            visibility: hidden !important;
            height: 0px !important;
            width: 0px !important;
            opacity: 0 !important;
            pointer-events: none !important;
        }
    """.trimIndent()

    // JS to inject to hide ads, remove scripts, override window.open
    val JS_BLOCK_INJECTION = """
        (function() {
            // Override window.open to block popups
            var originalWindowOpen = window.open;
            window.open = function(url, name, specs) {
                console.log('Blocked popup window: ' + url);
                return null; // Block opening child popup
            };

            // Periodically clean up leftover ad spaces
            function removeAds() {
                var selector = '.advertisement, .ad-box, .adsbygoogle, [class*="ad-"], [id*="ad-"], iframe[src*="doubleclick"], iframe[src*="googleads"], div[id*="pop"], div[class*="pop"], div[id*="banner"], div[class*="banner"]';
                var elements = document.querySelectorAll(selector);
                elements.forEach(function(el) {
                    el.style.display = 'none';
                    el.style.visibility = 'hidden';
                    el.style.height = '0px';
                    el.style.width = '0px';
                });
            }

            // Run once right away
            removeAds();

            // Run on mutations
            var observer = new MutationObserver(function(mutations) {
                removeAds();
            });
            if (document.body) {
                observer.observe(document.body, { childList: true, subtree: true });
            } else {
                document.addEventListener('DOMContentLoaded', function() {
                    observer.observe(document.body, { childList: true, subtree: true });
                });
            }
        })();
    """.trimIndent()
}
