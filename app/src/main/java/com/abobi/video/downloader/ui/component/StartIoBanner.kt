package com.abobi.video.downloader.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.startapp.sdk.ads.banner.Banner

@Composable
fun StartIoBanner(modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx -> Banner(ctx).also { it.loadAd() } },
        modifier = modifier.fillMaxWidth(),
    )
}
