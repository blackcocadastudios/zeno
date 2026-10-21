package com.blackcocadastudios.zeno.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun ZenoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(background = Color(0xFFFCFAF2), primary = Color(0xFF2792C3)),
        content = content,
    )
}
