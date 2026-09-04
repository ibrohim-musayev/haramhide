package com.haramhide.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import com.haramhide.app.R

/**
 * **TZ FR-001 / FR-006 — prominent disclosure.**
 *
 * Birinchi ishga tushirishda, menyusiz, "Roziman" bosilmaguncha davom etmaydi.
 *
 * Bu ekranning maqsadi ilovani sotish emas, balki **kutilmani to'g'ri
 * o'rnatish**. Shu sababli ikkinchi va to'rtinchi sahifalar ataylab
 * ilovaning kamchiliklari haqida: u 100 % ishlamaydi va ba'zi joylarda
 * umuman ishlamaydi. Foydalanuvchi buni keyinroq o'zi topib, ishonchini
 * yo'qotgandan ko'ra, hozir bilgani yaxshi.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    val pages = listOf(
        R.string.ob_welcome_title to R.string.ob_welcome_body,
        R.string.ob_honesty_title to R.string.ob_honesty_body,
        R.string.ob_privacy_title to R.string.ob_privacy_body,
        R.string.ob_limits_title to R.string.ob_limits_body,
    )
    val last = page == pages.lastIndex

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp)) {
            Spacer(Modifier.height(32.dp))

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    stringResource(pages[page].first),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    HtmlCompat.fromHtml(
                        stringResource(pages[page].second),
                        HtmlCompat.FROM_HTML_MODE_COMPACT,
                    ).toString(),
                    style = MaterialTheme.typography.bodyLarge,
                    lineHeight = MaterialTheme.typography.bodyLarge.fontSize * 1.5,
                )
            }

            // Sahifa ko'rsatkichi
            Row(
                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                pages.indices.forEach { i ->
                    Surface(
                        Modifier.padding(4.dp).size(if (i == page) 10.dp else 7.dp),
                        shape = CircleShape,
                        color = if (i == page) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                    ) {}
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (page > 0) {
                    TextButton(onClick = { page-- }) { Text(stringResource(R.string.ob_back)) }
                } else {
                    Spacer(Modifier.size(1.dp))
                }
                Button(onClick = { if (last) onDone() else page++ }) {
                    Text(stringResource(if (last) R.string.ob_consent else R.string.ob_next))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
