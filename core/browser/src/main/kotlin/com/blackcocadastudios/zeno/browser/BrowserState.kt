package com.blackcocadastudios.zeno.browser

import kotlinx.serialization.Serializable

@Serializable
data class TabSnapshot(val id: String, val url: String?, val title: String)
