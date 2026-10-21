package com.blackcocadastudios.zeno.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.blackcocadastudios.zeno.R
import com.blackcocadastudios.zeno.design.component.ZenoPillButton
import com.blackcocadastudios.zeno.design.icon.ZenoIcons
import com.blackcocadastudios.zeno.design.theme.Ma
import com.blackcocadastudios.zeno.design.theme.Zeno
import com.blackcocadastudios.zeno.design.theme.ZenoShapes

/**
 * Shown once on the home page: Zeno blocks trackers by itself, and uBlock
 * Origin (one tap away) takes care of ads.
 */
@Composable
fun UblockCard(installing: Boolean, onInstall: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Zeno.colors
    Column(
        modifier
            .widthIn(max = 380.dp)
            .fillMaxWidth()
            .clip(ZenoShapes.medium)
            .background(colors.paperRaised)
            .border(0.5.dp, colors.line, ZenoShapes.medium)
            .padding(Ma.l),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(ZenoIcons.ShieldCheck, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(Ma.s))
            Text(stringResource(R.string.onboarding_ublock_title), style = Zeno.type.heading, color = colors.ink)
        }
        Spacer(Modifier.height(Ma.s))
        Text(stringResource(R.string.onboarding_ublock_body), style = Zeno.type.bodySmall, color = colors.inkSoft)
        Spacer(Modifier.height(Ma.l))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Ma.s, Alignment.End)) {
            ZenoPillButton(stringResource(R.string.onboarding_later), onDismiss)
            ZenoPillButton(
                stringResource(if (installing) R.string.extensions_installing else R.string.onboarding_ublock_install),
                onInstall,
                filled = true,
                enabled = !installing,
            )
        }
    }
}
