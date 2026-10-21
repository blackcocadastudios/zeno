package com.blackcocadastudios.zeno.engine.gecko

import android.content.Context
import com.blackcocadastudios.zeno.engine.Engine
import org.mozilla.geckoview.GeckoRuntime

class GeckoEngine(private val context: Context) : Engine {
    override val name: String = "Gecko"

    val runtime: GeckoRuntime by lazy { GeckoRuntime.create(context) }
}
