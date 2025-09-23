package raf.console.tablememory.vk_admob

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.my.target.ads.InterstitialAd
import com.my.target.ads.MyTargetView
import com.my.target.common.MyTargetManager
import com.my.target.common.models.IAdLoadingError


/**
 * Баннер 320Х50
 */
@Composable
fun VKBannerAd(slotId: Int) {
    //MyTargetManager.setDebugMode(true)
    AndroidView(
        factory = { context ->
            MyTargetView(context).apply {
                setSlotId(slotId) // Укажите ваш Slot ID
                load() // Загрузите рекламу

                // Обработка событий
                setListener(object : MyTargetView.MyTargetViewListener {
                    override fun onLoad(ad: MyTargetView) {
                        // Реклама успешно загружена
                    }

                    override fun onNoAd(p0: IAdLoadingError, p1: MyTargetView) {
                        // Реклама не загружена
                    }

                    override fun onClick(ad: MyTargetView) {
                        // Пользователь нажал на рекламу

                        val clickUrl = ad.adSource //getClickUrl() // Получаем ссылку из рекламы
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(clickUrl))

                        if (browserIntent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(browserIntent)
                        } else {
                            // Браузер недоступен, покажите сообщение пользователю
                        }
                    }

                    override fun onShow(ad: MyTargetView) {
                        // Реклама показана
                    }
                })
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp) // Высота баннера 50dp (соответствует 320x50)
    )
}


/**
 * Полноэкранная реклама
 *
 */
fun loadAndShowInterstitialAd(context: Context, slotId: Int) {
    //MyTargetManager.setDebugMode(true)
    val interstitialAd = InterstitialAd(slotId, context)

    // Обработка событий
    interstitialAd.setListener(object : InterstitialAd.InterstitialAdListener {
        override fun onLoad(ad: InterstitialAd) {
            // Реклама успешно загружена, показываем её
            ad.show()
        }

        override fun onNoAd(error: IAdLoadingError, ad: InterstitialAd) {
            // Реклама не загружена
        }

        override fun onClick(ad: InterstitialAd) {
            // Пользователь нажал на рекламу
            val clickUrl = ad.adSource // Получаем ссылку из рекламы
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(clickUrl))

            if (browserIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(browserIntent)
            } else {
                // Браузер недоступен, покажите сообщение пользователю
            }
        }

        override fun onDisplay(ad: InterstitialAd) {
            // Реклама показана
        }

        override fun onDismiss(ad: InterstitialAd) {
            // Реклама закрыта
        }

        override fun onVideoCompleted(ad: InterstitialAd) {
            // Видео завершено (если это видео-реклама)
        }
    })

    // Загружаем рекламу
    interstitialAd.load()
}

fun loadAndShowInterstitialAdWithCallback(context: Context, slotId: Int, onAdClosed: () -> Unit) {
    MyTargetManager.setDebugMode(true)
    val interstitialAd = InterstitialAd(slotId, context)

    interstitialAd.setListener(object : InterstitialAd.InterstitialAdListener {
        override fun onLoad(ad: InterstitialAd) {
            ad.show()
        }

        override fun onNoAd(error: IAdLoadingError, ad: InterstitialAd) {
            // Если рекламы нет, сразу выполняем действие
            onAdClosed()
        }

        override fun onClick(ad: InterstitialAd) {
            // Можно добавить логику при нажатии на рекламу
            // Пользователь нажал на рекламу
            val clickUrl = ad.adSource // Получаем ссылку из рекламы
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(clickUrl))

            if (browserIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(browserIntent)
            } else {
                // Браузер недоступен, покажите сообщение пользователю
            }
        }

        override fun onDisplay(ad: InterstitialAd) {
            // Реклама показана
        }

        override fun onDismiss(ad: InterstitialAd) {
            // Когда реклама закрыта, выполняем переданный callback
            onAdClosed()
        }

        override fun onVideoCompleted(ad: InterstitialAd) {
            // Видео завершено (если видео-реклама)
        }
    })

    interstitialAd.load()
}


/**
 * Нативная реклама для встраивания элементов
 *
 */
@Composable
fun ScrollableListWithAds(
    items: List<String>, // Список элементов
    adSlotId: Int, // Slot ID для рекламы
    adInterval: Int = 5 // Интервал размещения рекламы (например, каждые 5 элементов)
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(items) { index, item ->
            Text(
                text = item,
                fontSize = 18.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )

            // Вставляем баннер через каждые `adInterval` элементов
            if ((index + 1) % adInterval == 0) {
                VKBannerAd(slotId = adSlotId)
            }
        }
    }
}

@Composable
fun InterstitialAdTextButton(
    slotId: Int,
    onEvent: () -> Unit,
    modifier: Modifier = Modifier
) {
    MyTargetManager.setDebugMode(true)
    val context = LocalContext.current

    TextButton(
        modifier = modifier,
        shape = RoundedCornerShape(100),
        contentPadding = ButtonDefaults.ContentPadding,
        onClick = {
            loadAndShowInterstitialAdWithCallback(
                context = context,
                slotId = slotId,
                onAdClosed = {
                    onEvent()
                }
            )
        }
    ) {
        Text(text = "Нет")
    }
}