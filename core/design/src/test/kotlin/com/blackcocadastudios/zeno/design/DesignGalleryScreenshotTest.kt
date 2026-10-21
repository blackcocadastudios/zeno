package com.blackcocadastudios.zeno.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.blackcocadastudios.zeno.design.component.Enso
import com.blackcocadastudios.zeno.design.component.EmptyState
import com.blackcocadastudios.zeno.design.component.Hairline
import com.blackcocadastudios.zeno.design.component.ProgressLine
import com.blackcocadastudios.zeno.design.component.Seal
import com.blackcocadastudios.zeno.design.component.SectionLabel
import com.blackcocadastudios.zeno.design.component.TabCounter
import com.blackcocadastudios.zeno.design.component.ZenoListRow
import com.blackcocadastudios.zeno.design.component.ZenoSwitchRow
import com.blackcocadastudios.zeno.design.component.ZenoWordmark
import com.blackcocadastudios.zeno.design.icon.ZenoIcons
import com.blackcocadastudios.zeno.design.theme.Ma
import com.blackcocadastudios.zeno.design.theme.Zeno
import com.blackcocadastudios.zeno.design.theme.ZenoShapes
import com.blackcocadastudios.zeno.design.theme.ZenoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the whole design system so a change to a token is visible in review. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h1900dp-xxhdpi")
class DesignGalleryScreenshotTest {

    @Test
    fun day() = capture("gallery_day", dark = false, privateMode = false)

    @Test
    fun night() = capture("gallery_night", dark = true, privateMode = false)

    @Test
    fun privateBrowsing() = capture("gallery_private", dark = false, privateMode = true)

    private fun capture(name: String, dark: Boolean, privateMode: Boolean) {
        captureRoboImage("build/outputs/roborazzi/$name.png") {
            ZenoTheme(dark = dark, privateMode = privateMode) {
                Gallery()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Gallery() {
    val c = Zeno.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.paper)
            .padding(bottom = Ma.xl),
    ) {
        Column(Modifier.padding(Ma.xl)) {
            ZenoWordmark()
            Spacer(Modifier.height(Ma.l))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Enso(Modifier.size(56.dp))
                Spacer(Modifier.width(Ma.l))
                Enso(Modifier.size(56.dp), drawProgress = 0.55f)
                Spacer(Modifier.width(Ma.l))
                Enso(Modifier.size(56.dp), color = c.ink)
            }
        }
        SectionLabel("Cores")
        FlowRow(
            Modifier.padding(horizontal = Ma.xl),
            horizontalArrangement = Arrangement.spacedBy(Ma.s),
            verticalArrangement = Arrangement.spacedBy(Ma.s),
        ) {
            Swatch("paper", c.paper)
            Swatch("raised", c.paperRaised)
            Swatch("sunken", c.paperSunken)
            Swatch("ink", c.ink)
            Swatch("soft", c.inkSoft)
            Swatch("faint", c.inkFaint)
            Swatch("line", c.line)
            Swatch("accent", c.accent)
            Swatch("accentInk", c.accentInk)
            Swatch("container", c.accentContainer)
            Swatch("seal", c.seal)
        }
        SectionLabel("Tipografia")
        Column(Modifier.padding(horizontal = Ma.xl), verticalArrangement = Arrangement.spacedBy(Ma.xs)) {
            Text("Zeno", style = Zeno.type.display, color = c.ink)
            Text("Configurações", style = Zeno.type.title, color = c.ink)
            Text("Privacidade e segurança", style = Zeno.type.heading, color = c.ink)
            Text("Pesquisar ou digitar endereço", style = Zeno.type.body, color = c.inkSoft)
            Text("Bloqueia rastreadores conhecidos em todos os sites.", style = Zeno.type.bodySmall, color = c.inkSoft)
            Text("Abrir em nova aba", style = Zeno.type.label, color = c.accentInk)
            Text("há 3 minutos · 1,2 MB", style = Zeno.type.caption, color = c.inkFaint)
            Text("O silêncio também é uma página.", style = Zeno.type.mincho, color = c.ink)
        }
        SectionLabel("Ícones")
        FlowRow(
            Modifier.padding(horizontal = Ma.xl),
            horizontalArrangement = Arrangement.spacedBy(Ma.m),
            verticalArrangement = Arrangement.spacedBy(Ma.m),
        ) {
            ZenoIcons.all.forEach { (name, icon) ->
                Icon(icon, contentDescription = name, tint = c.ink, modifier = Modifier.size(24.dp))
            }
        }
        SectionLabel("Selos e contadores")
        Row(
            Modifier.padding(horizontal = Ma.xl),
            horizontalArrangement = Arrangement.spacedBy(Ma.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Seal("W", "wikipedia.org")
            Seal("G", "github.com")
            Seal("Y", "youtube.com")
            Seal("N", "news.ycombinator.com")
            Seal("F", "folha.uol.com.br")
            TabCounter(3)
            TabCounter(42)
            TabCounter(120)
        }
        Spacer(Modifier.height(Ma.l))
        ProgressLine(progress = 0.42f, visible = true)
        Hairline()
        SectionLabel("Listas")
        ZenoListRow(title = "Histórico", subtitle = "Páginas visitadas, mais recentes primeiro", icon = ZenoIcons.History, onClick = {})
        ZenoSwitchRow(
            title = "Somente HTTPS",
            subtitle = "Avisa antes de abrir sites sem conexão segura",
            icon = ZenoIcons.Lock,
            checked = true,
            onCheckedChange = {},
        )
        ZenoSwitchRow(title = "Sugestões de pesquisa", icon = ZenoIcons.Search, checked = false, onCheckedChange = {})
        EmptyState(
            title = "Nenhum download ainda",
            body = "Arquivos que você baixar aparecem aqui.",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Swatch(name: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(width = 64.dp, height = 40.dp)
                .background(color, ZenoShapes.small)
                .border(0.5.dp, Zeno.colors.line, ZenoShapes.small),
        )
        Text(name, style = Zeno.type.caption, color = Zeno.colors.inkSoft)
    }
}
