package com.myprojects.scanwisp.ui.screens.home.components

import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.myprojects.scanwisp.R

@Composable
fun NativeAdListItem(nativeAd: NativeAd, modifier: Modifier = Modifier) {

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { context ->
                val adView = NativeAdView(context)
                val composeView = ComposeView(context)
                adView.addView(composeView)

                composeView.setContent {
                    NativeAdListContent(nativeAd = nativeAd, adView = adView)
                }
                adView
            }
        )
    }
}

@Composable
private fun NativeAdListContent(nativeAd: NativeAd, adView: NativeAdView) {
    adView.setNativeAd(nativeAd)

    Box {
        AndroidView(
            factory = { context ->
                val hiddenViews = FrameLayout(context).apply {
                    visibility = View.GONE
                    addView(android.widget.ImageView(context).also { adView.iconView = it })
                    addView(android.widget.TextView(context).also { adView.headlineView = it })
                    addView(android.widget.Button(context).also { adView.callToActionView = it })
                    addView(MediaView(context).also { adView.mediaView = it })
                }
                hiddenViews
            }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            nativeAd.icon?.uri?.let {
                AsyncImage(
                    model = it,
                    contentDescription = stringResource(R.string.ad_cd_icon),
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.ad_label),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.tertiaryContainer,
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    nativeAd.headline?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                nativeAd.body?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            nativeAd.callToAction?.let {
                Button(
                    onClick = { /* AdMob handles this click */ },
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(text = it)
                }
            }
        }
    }
}