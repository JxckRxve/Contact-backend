package com.jrstudio.svyazsbogom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.jrstudio.svyazsbogom.ui.GosRootApp
import com.jrstudio.svyazsbogom.ui.theme.SvyazSBogomTheme
import com.jrstudio.svyazsbogom.util.scheduleReplyWatch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        scheduleReplyWatch(this)
        setContent {
            SvyazSBogomTheme {
                GosRootApp()
            }
        }
    }
}
