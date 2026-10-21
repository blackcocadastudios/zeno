package com.blackcocadastudios.zeno.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h200dp-xhdpi")
class SmokeScreenshotTest {
    @Test
    fun smoke() {
        captureRoboImage("build/outputs/roborazzi/smoke.png") {
            ZenoTheme {
                Text(
                    "Zeno",
                    modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(24.dp),
                )
            }
        }
    }
}
