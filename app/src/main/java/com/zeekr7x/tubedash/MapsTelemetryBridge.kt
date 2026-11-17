package com.zeekr7x.tubedash

import android.webkit.JavascriptInterface

data class NavTelemetry(
    var maneuver: String = "⇡",
    var distance: String = "-- m",
    var street: String = "Ready to Navigate",
    var eta: String = "-- min",
    var remainingDist: String = "-- km",
    var arrivalTime: String = "--:--",
    var isNavigating: Boolean = false
)

class MapsTelemetryBridge(private val onUpdate: (NavTelemetry) -> Unit) {

    @JavascriptInterface
    fun onNavTelemetry(
        maneuver: String,
        distance: String,
        street: String,
        eta: String,
        remainingDist: String,
        arrivalTime: String,
        isNavigating: Boolean
    ) {
        onUpdate(
            NavTelemetry(
                maneuver = maneuver.ifBlank { "⇡" },
                distance = distance.ifBlank { "-- m" },
                street = street.ifBlank { "Cruising" },
                eta = eta.ifBlank { "-- min" },
                remainingDist = remainingDist.ifBlank { "-- km" },
                arrivalTime = arrivalTime.ifBlank { "--:--" },
                isNavigating = isNavigating
            )
        )
    }

    companion object {
        const val JS_INJECT_MAPS = """
            (function() {
                if (window.zeekrMapsInjected) return;
                window.zeekrMapsInjected = true;

                // Function to extract navigation telemetry from Google Maps
                function scrapeNav() {
                    let maneuver = "⇡";
                    let distance = "";
                    let street = "";
                    let eta = "";
                    let remaining = "";
                    let arrival = "";
                    let isNav = false;

                    // Search for navigation banner / direction step
                    const stepEl = document.querySelector('.directions-mode-step, [data-step-index], .directions-step-description, .unified-directions-step');
                    if (stepEl) {
                        isNav = true;
                        street = stepEl.innerText.split('\n')[0] || "";
                    }

                    const distEl = document.querySelector('.directions-mode-distance, .directions-step-distance, .section-directions-trip-description');
                    if (distEl) {
                        distance = distEl.innerText.trim();
                    }

                    // Trip summary / ETA / Remaining
                    const summaryEl = document.querySelector('.section-directions-trip-duration, .directions-summary-duration, .directions-mode-eta');
                    if (summaryEl) {
                        isNav = true;
                        eta = summaryEl.innerText.trim();
                    }

                    const remainEl = document.querySelector('.section-directions-trip-distance, .directions-summary-distance');
                    if (remainEl) {
                        remaining = remainEl.innerText.trim();
                    }

                    // Parse maneuver icons
                    const iconEl = document.querySelector('.directions-mode-icon, .directions-step-icon, [class*="maneuver-icon"]');
                    if (iconEl) {
                        const iconClass = (iconEl.className || "").toLowerCase();
                        if (iconClass.includes('right')) maneuver = "↱";
                        else if (iconClass.includes('left')) maneuver = "↰";
                        else if (iconClass.includes('uturn')) maneuver = "↺";
                        else if (iconClass.includes('straight')) maneuver = "⇡";
                        else if (iconClass.includes('fork')) maneuver = "⮬";
                    }

                    if (window.ZeekrNavBridge) {
                        window.ZeekrNavBridge.onNavTelemetry(
                            maneuver, distance, street, eta, remaining, arrival, isNav
                        );
                    }
                }

                // Poll every 1 second
                setInterval(scrapeNav, 1000);

                // Auto-hide Google Maps bottom cookie / app promo banners
                setInterval(() => {
                    const promo = document.querySelector('.app-banner, .google-maps-promo, button[aria-label*="Stay on web"]');
                    if (promo) promo.click();
                }, 2000);
            })();
        """

        const val JS_TOGGLE_SATELLITE = """
            (function() {
                const btn = document.querySelector('button[aria-label*="Satellite"], button[data-value="satellite"], button[aria-label*="Layers"], button[jsaction*="layer"]');
                if (btn) btn.click();
                else {
                    // Fallback to URL reload with layer parameter
                    if (!window.location.href.includes('layer=c') && !window.location.href.includes('t=k')) {
                        window.location.href = window.location.href + (window.location.href.includes('?') ? '&' : '?') + 't=k';
                    }
                }
            })();
        """

        const val JS_TOGGLE_DEFAULT = """
            (function() {
                const btn = document.querySelector('button[aria-label*="Map"], button[data-value="default"], button[aria-label*="Layers"]');
                if (btn) btn.click();
                else {
                    if (window.location.href.includes('t=k') || window.location.href.includes('layer=c')) {
                        window.location.href = window.location.href.replace(/([?&])t=k(&|$)/, '$1').replace(/([?&])layer=c(&|$)/, '$1');
                    }
                }
            })();
        """

        const val JS_ZOOM_IN = """
            (function() {
                const btn = document.querySelector('#widget-zoom-in, button[aria-label*="Zoom in"], button[jsaction*="zoomIn"]');
                if (btn) btn.click();
            })();
        """

        const val JS_ZOOM_OUT = """
            (function() {
                const btn = document.querySelector('#widget-zoom-out, button[aria-label*="Zoom out"], button[jsaction*="zoomOut"]');
                if (btn) btn.click();
            })();
        """

        const val JS_RECENTER = """
            (function() {
                const btn = document.querySelector('#mylocation, button[aria-label*="Your location"], button[aria-label*="Show your location"]');
                if (btn) btn.click();
            })();
        """
    }
}
