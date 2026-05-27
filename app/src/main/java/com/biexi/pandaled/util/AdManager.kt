package com.biexi.pandaled.util

import android.app.Activity
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

object AdManager {

    private const val INTERSTITIAL_UNIT_ID = "ca-app-pub-7768614948828316/2394673168"

    /** Load ad, then show it. Retries on non-network errors. */
    fun loadAndShow(activity: Activity, onDismissed: () -> Unit, onNetworkError: () -> Unit) {
        if (!DebugConfig.enableAds()) {
            android.os.Handler(activity.mainLooper).postDelayed({ onDismissed() }, 1500)
            return
        }
        loadInternal(activity, onDismissed, onNetworkError)
    }

    private fun loadInternal(activity: Activity, onDismissed: () -> Unit, onNetworkError: () -> Unit) {
        InterstitialAd.load(
            activity,
            INTERSTITIAL_UNIT_ID,
            com.google.android.gms.ads.AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    ad.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            onDismissed()
                        }

                        override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                            if (error.code == 2) onNetworkError()
                            else loadInternal(activity, onDismissed, onNetworkError)
                        }
                    }
                    ad.show(activity)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    if (error.code == 2) onNetworkError()
                    else loadInternal(activity, onDismissed, onNetworkError)
                }
            }
        )
    }
}
