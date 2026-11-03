@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.example.pixelisland.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.pixelisland.IslandPrefs
import com.example.pixelisland.IslandRepository
import com.example.pixelisland.IslandService
import com.example.pixelisland.model.CallEvent
import com.example.pixelisland.model.MediaEvent
import com.example.pixelisland.model.NavigationEvent
import com.example.pixelisland.model.NotificationEvent
import com.example.pixelisland.model.TimerEvent
import androidx.compose.material.icons.rounded.Navigation
import com.example.pixelisland.model.ProgressEvent

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val settings by IslandPrefs.settings.collectAsState()

    var overlayOk by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var listenerOk by remember {
        mutableStateOf(NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName))
    }
    var postOk by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        overlayOk = Settings.canDrawOverlays(context)
        listenerOk = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        postOk = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        // Keep service state in sync with the toggle (e.g. after the permission was just granted)
        if (IslandPrefs.settings.value.enabled && overlayOk) IslandService.start(context)
    }

    val postLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { postOk = it }

    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Pixel Island") },
                subtitle = { Text("Остров вокруг камеры") },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ---- hero
            Surface(
                shape = RoundedCornerShape(36.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Row(
                    Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(
                        Modifier
                            .size(64.dp)
                            .clip(MaterialShapes.Cookie9Sided.toShape())
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (settings.enabled && overlayOk) {
                            LoadingIndicator(
                                modifier = Modifier.size(48.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(
                                Icons.Rounded.Notifications, null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Остров", style = MaterialTheme.typography.headlineSmallEmphasized)
                        Text(
                            when {
                                !overlayOk -> "Нужно разрешение «Поверх других окон»"
                                settings.enabled -> "Работает"
                                else -> "Выключен"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Switch(
                        checked = settings.enabled,
                        onCheckedChange = { on ->
                            if (on && !overlayOk) {
                                openOverlaySettings(context)
                            } else {
                                IslandPrefs.update { it.copy(enabled = on) }
                                if (on) IslandService.start(context) else IslandService.stop(context)
                            }
                        },
                        thumbContent = if (settings.enabled) {
                            { Icon(Icons.Rounded.Check, null, Modifier.size(SwitchDefaults.IconSize)) }
                        } else null,
                    )
                }
            }

            // ---- permissions
            SectionTitle("Разрешения")
            SegmentedGroup(
                listOf(
                    {
                        PermissionRow("Поверх других окон", "Рисует остров над системной панелью", overlayOk) {
                            openOverlaySettings(context)
                        }
                    },
                    {
                        PermissionRow("Доступ к уведомлениям", "Музыка, звонки, Live Updates и уведомления", listenerOk) {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }
                    },
                    {
                        PermissionRow("Уведомление сервиса", "Нужно для работы в фоне", postOk) {
                            postLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                )
            )

            // ---- what to show
            SectionTitle("Что показывать")
            SegmentedGroup(
                listOf(
                    { SwitchRow("Музыка и видео", "Обложка, эквалайзер, управление", settings.showMedia) { v -> IslandPrefs.update { it.copy(showMedia = v) } } },
                    { SwitchRow("Звонки", "Входящие и текущие вызовы", settings.showCalls) { v -> IslandPrefs.update { it.copy(showCalls = v) } } },
                    { SwitchRow("Live Updates и таймеры", "Прогресс-уведомления Android 16", settings.showProgress) { v -> IslandPrefs.update { it.copy(showProgress = v) } } },
                    { SwitchRow("Навигация", "Дистанция и манёвр из карт", settings.showNavigation) { v -> IslandPrefs.update { it.copy(showNavigation = v) } } },
                    { SwitchRow("Таймеры и секундомер", "Обратный отсчёт прямо у камеры", settings.showTimers) { v -> IslandPrefs.update { it.copy(showTimers = v) } } },
                    { SwitchRow("Уведомления", "Короткая карточка при новом уведомлении", settings.showNotifications) { v -> IslandPrefs.update { it.copy(showNotifications = v) } } },
                    { SwitchRow("Раскрывать автоматически", "Новые уведомления раскрываются сами", settings.autoExpand) { v -> IslandPrefs.update { it.copy(autoExpand = v) } } },
                )
            )

            // ---- look
            SectionTitle("Внешний вид")
            SegmentedGroup(
                listOf(
                    {
                        SliderRow("Размер", "%.0f%%".format(settings.scale * 100), settings.scale, 0.8f..1.3f) { v ->
                            IslandPrefs.update { it.copy(scale = v) }
                        }
                    },
                    {
                        SliderRow("Сдвиг по вертикали", "%.0f dp".format(settings.topOffset), settings.topOffset, -8f..16f) { v ->
                            IslandPrefs.update { it.copy(topOffset = v) }
                        }
                    },
                )
            )

            // ---- demo
            SectionTitle("Проверка")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                DemoButton("Музыка", Icons.Rounded.MusicNote, Modifier.weight(1f), RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp, topEnd = 6.dp, bottomEnd = 6.dp)) {
                    IslandRepository.post(
                        MediaEvent("demo:media", "Midnight City", "M83", null, true, 243_000, null)
                    )
                }
                DemoButton("Звонок", Icons.Rounded.Call, Modifier.weight(1f), RoundedCornerShape(6.dp)) {
                    IslandRepository.post(
                        CallEvent("demo:call", "Мама", false, System.currentTimeMillis(), null, null, null)
                    )
                }
                DemoButton("Таймер", Icons.Rounded.Timer, Modifier.weight(1f), RoundedCornerShape(6.dp)) {
                    IslandRepository.post(
                        ProgressEvent("demo:progress", "Такси", "Водитель в пути", "Toyota Camry · А 123 БВ", 0.6f, "4 мин", null, null)
                    )
                }
                DemoButton("Уведомл.", Icons.Rounded.Notifications, Modifier.weight(1f), RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 28.dp, bottomEnd = 28.dp)) {
                    IslandRepository.post(
                        NotificationEvent(
                            "demo:notif", "Сообщения", "Анна",
                            "Ты уже вышел? Мы ждём у входа", null, null, System.currentTimeMillis() + 6_000,
                        )
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                DemoButton("Навигация", Icons.Rounded.Navigation, Modifier.weight(1f), RoundedCornerShape(topStart = 6.dp, bottomStart = 28.dp, topEnd = 6.dp, bottomEnd = 6.dp)) {
                    IslandRepository.post(
                        NavigationEvent("demo:nav", "Карты", "250 м", "Поверните налево на улицу Ленина", null, null)
                    )
                }
                DemoButton("Таймер 5м", Icons.Rounded.Timer, Modifier.weight(1f), RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 6.dp, bottomEnd = 28.dp)) {
                    IslandRepository.post(
                        TimerEvent("demo:timer", "Часы", "Паста", System.currentTimeMillis() + 5 * 60_000, true, null, null)
                    )
                }
            }
            Text(
                "Долгое нажатие на остров убирает текущую активность.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Text(
                "Если «таблетка» чуть не совпадает с вырезом камеры, подвиньте её слайдером «Сдвиг по вертикали».",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ------------------------------------------------------------------ pieces

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmallEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** Pixel-Settings-style grouped list: big outer corners, tiny gaps and corners between rows. */
@Composable
private fun SegmentedGroup(rows: List<@Composable () -> Unit>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rows.forEachIndexed { index, row ->
            val big = 28.dp
            val small = 6.dp
            val shape = RoundedCornerShape(
                topStart = if (index == 0) big else small,
                topEnd = if (index == 0) big else small,
                bottomStart = if (index == rows.lastIndex) big else small,
                bottomEnd = if (index == rows.lastIndex) big else small,
            )
            Surface(
                shape = shape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) { row() }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                thumbContent = if (checked) {
                    { Icon(Icons.Rounded.Check, null, Modifier.size(SwitchDefaults.IconSize)) }
                } else null,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun PermissionRow(title: String, subtitle: String, granted: Boolean, onGrant: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            if (granted) {
                Icon(Icons.Rounded.Check, "Выдано", tint = MaterialTheme.colorScheme.primary)
            } else {
                FilledTonalButton(onClick = onGrant) { Text("Выдать") }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun SliderRow(
    title: String,
    value: String,
    current: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = current, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun DemoButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = shape,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

private fun openOverlaySettings(context: android.content.Context) {
    context.startActivity(
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
    )
}
