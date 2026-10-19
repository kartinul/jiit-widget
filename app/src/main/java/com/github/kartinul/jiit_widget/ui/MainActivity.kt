package com.github.kartinul.jiit_widget.ui

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import com.github.kartinul.jiit_widget.R
import com.github.kartinul.jiit_widget.data.MenuStore
import com.github.kartinul.jiit_widget.widget.MenuWidget
import com.github.kartinul.jiit_widget.widget.MenuWidgetReceiver
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { JiitTheme { HomeScreen() } }
    }
}

@Composable
private fun HomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var compact by remember { mutableStateOf(MenuStore.isCompact(context)) }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            GlanceAppWidgetManager(context).setWidgetPreviews(MenuWidgetReceiver::class)
        }
    }
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.app_widget_description), style = MaterialTheme.typography.bodyLarge)
            Button(onClick = {
                scope.launch { GlanceAppWidgetManager(context).requestPinGlanceAppWidget(MenuWidgetReceiver::class.java) }
            }) {
                Text(stringResource(R.string.add_widget))
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.compact_view)) },
                supportingContent = { Text(stringResource(R.string.compact_view_summary)) },
                trailingContent = {
                    Switch(checked = compact, onCheckedChange = {
                        compact = it
                        MenuStore.setCompact(context, it)
                        scope.launch { MenuWidget().updateAll(context) }
                    })
                },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() = JiitTheme { HomeScreen() }
