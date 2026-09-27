package com.banter.app

import android.content.Context
import com.banter.app.data.BanterStore
import com.banter.app.llm.EngineManager
import com.banter.app.llm.ModelStore

/** Hand-rolled dependency graph. One app, one screenful of wiring; a DI library would be noise. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val store: BanterStore by lazy { BanterStore(appContext) }
    val modelStore: ModelStore by lazy { ModelStore(appContext) }
    val engineManager: EngineManager by lazy { EngineManager(appContext, modelStore) }
}
