package com.myprojects.scanwisp.ui.screens.home.components

import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.myprojects.scanwisp.R

@Composable
fun NativeAdCard(nativeAd: NativeAd) {

    Card(
        modifier = Modifier.aspectRatio(0.7f),
        shape = RoundedCornerShape(4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                val adView = NativeAdView(context)
                val composeView = ComposeView(context)
                adView.addView(composeView)

                composeView.setContent {
                    NativeAdContent(nativeAd = nativeAd, adView = adView)
                }
                adView
            }
        )
    }
}

@Composable
private fun NativeAdContent(nativeAd: NativeAd, adView: NativeAdView) {
    // Устанавливаем NativeAd для NativeAdView. Это обязательно.
    adView.setNativeAd(nativeAd)

    Box(modifier = Modifier.fillMaxSize()) {
        // --- Скрытые View для регистрации кликов AdMob ---
        // Мы создаем настоящие View, связываем их с AdView и делаем невидимыми.
        // AdMob будет отслеживать клики по ним, даже если они не видны.
        AndroidView(
            factory = { context ->
                // Создаем и связываем все необходимые View
                val hiddenViews = FrameLayout(context).apply {
                    visibility = View.GONE // Делаем контейнер невидимым
                    addView(android.widget.ImageView(context).also { adView.iconView = it })
                    addView(android.widget.TextView(context).also { adView.headlineView = it })
                    addView(android.widget.Button(context).also { adView.callToActionView = it })
                    addView(MediaView(context).also { adView.mediaView = it })
                }
                hiddenViews
            }
        )

        // --- Видимый UI на Jetpack Compose ---
        // Теперь рисуем наш красивый UI, который видит пользователь.

        // Основное изображение рекламы
        val image = nativeAd.images.firstOrNull()
        if (image != null) {
            AsyncImage(
                model = image.uri,
                // START: AI_MODIFIED_BLOCK
                contentDescription = stringResource(R.string.ad_cd_image),
                // END: AI_MODIFIED_BLOCK
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Оверлей для текста
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Заголовок и иконка
            Row(verticalAlignment = Alignment.CenterVertically) {
                nativeAd.icon?.uri?.let {
                    AsyncImage(
                        model = it,
                        // START: AI_MODIFIED_BLOCK
                        contentDescription = stringResource(R.string.ad_cd_icon),
                        // END: AI_MODIFIED_BLOCK
                        modifier = Modifier.size(40.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                nativeAd.headline?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2
                    )
                }
            }

            // Кнопка действия
            nativeAd.callToAction?.let {
                // Наша красивая Compose-кнопка. AdMob обработает клик, т.к. AdView является ее родителем.
                Button(
                    onClick = { /* AdMob handles this click on the parent AdView */ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = it)
                }
            }
        }
        // Маленькая метка "Ad"
        Text(
            // START: AI_MODIFIED_BLOCK
            text = stringResource(R.string.ad_label),
            // END: AI_MODIFIED_BLOCK
            modifier = Modifier
                .align(Alignment.TopEnd)
                .background(
                    MaterialTheme.colorScheme.tertiary,
                    RoundedCornerShape(bottomStart = 4.dp)
                )
                .padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiary
        )
    }
}