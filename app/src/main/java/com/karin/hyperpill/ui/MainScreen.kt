package com.karin.hyperpill.ui

import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.karin.hyperpill.PillUiState
import com.karin.hyperpill.ui.navigation.LocalNavigator
import com.karin.hyperpill.ui.navigation.Navigator
import com.karin.hyperpill.ui.navigation.Route
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.navBackStackOf

@Composable
fun MainScreen(
    state: PillUiState,
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    onSelectDevice: (BluetoothDevice) -> Unit,
    onDisconnect: () -> Unit,
    onRefreshDevices: () -> Unit,
    onRefreshBattery: () -> Unit,
    onSetGain: (Int) -> Unit,
    onSelectEq: (Int) -> Unit,
    onSetOneBringTwo: (Boolean) -> Unit,
    onSetOneBringTwoTimeout: (Int) -> Unit,
    onSetVoiceEnabled: (Boolean) -> Unit,
    onSetVoiceVolume: (Int) -> Unit,
    onSpoofDevice: (String?) -> Unit
) {
    val backStack = remember { navBackStackOf(Route.Main) }
    val navigator = remember(backStack) { Navigator(backStack) }

    CompositionLocalProvider(LocalNavigator provides navigator) {
        NavDisplay(
            backStack = backStack,
            onBack = { navigator.pop() }
        ) {
            entry<Route.Main> {
                MainTabContent(
                    state = state,
                    permissionGranted = permissionGranted,
                    onRequestPermission = onRequestPermission,
                    onSelectDevice = onSelectDevice,
                    onDisconnect = onDisconnect,
                    onRefreshDevices = onRefreshDevices,
                    onRefreshBattery = onRefreshBattery,
                    onOpenConfig = { navigator.push(Route.Config) },
                    onOpenDebug = { navigator.push(Route.Debug) }
                )
            }
            entry<Route.Config> {
                DeviceConfigPage(
                    state = state,
                    onBack = { navigator.pop() },
                    onSetGain = onSetGain,
                    onSelectEq = onSelectEq,
                    onSetOneBringTwo = onSetOneBringTwo,
                    onSetOneBringTwoTimeout = onSetOneBringTwoTimeout,
                    onSetVoiceEnabled = onSetVoiceEnabled,
                    onSetVoiceVolume = onSetVoiceVolume
                )
            }
            entry<Route.Debug> {
                DebugPage(
                    currentSpoofedName = state.spoofedDeviceName,
                    onSpoofDevice = onSpoofDevice,
                    onBack = { navigator.pop() }
                )
            }
        }
    }
}

@Composable
private fun MainTabContent(
    state: PillUiState,
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    onSelectDevice: (BluetoothDevice) -> Unit,
    onDisconnect: () -> Unit,
    onRefreshDevices: () -> Unit,
    onRefreshBattery: () -> Unit,
    onOpenConfig: () -> Unit,
    onOpenDebug: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val navigator = LocalNavigator.current
    val pagerState = rememberPagerState(pageCount = { 2 })
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var lastBackPressed by rememberSaveable { mutableLongStateOf(0L) }
    val effectiveConnected = state.connected || state.spoofedDeviceName != null

    LaunchedEffect(pagerState.settledPage) {
        selectedTab = pagerState.settledPage
    }

    BackHandler(enabled = navigator.current() is Route.Main && navigator.backStackSize() == 1) {
        if (selectedTab == 1) {
            selectedTab = 0
            scope.launch { pagerState.animateScrollToPage(0) }
        } else {
            val now = System.currentTimeMillis()
            if (now - lastBackPressed < 2000) {
                activity?.finish()
            } else {
                lastBackPressed = now
                Toast.makeText(context, "再滑一次返回桌面", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = "HyperPILL")
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = {
                        selectedTab = 0
                        scope.launch { pagerState.animateScrollToPage(0) }
                    },
                    icon = MiuixIcons.Home,
                    label = "主页"
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        scope.launch { pagerState.animateScrollToPage(1) }
                    },
                    icon = MiuixIcons.Info,
                    label = "关于"
                )
            }
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) { page ->
            when (page) {
                0 -> HomePage(
                    modifier = Modifier.fillMaxSize(),
                    state = state,
                    permissionGranted = permissionGranted,
                    onRequestPermission = onRequestPermission,
                    onSelectDevice = onSelectDevice,
                    onDisconnect = onDisconnect,
                    onRefreshDevices = onRefreshDevices,
                    onRefreshBattery = onRefreshBattery,
                    onOpenConfig = {
                        if (effectiveConnected) {
                            onOpenConfig()
                        }
                    }
                )
                else -> AboutPage(
                    modifier = Modifier.fillMaxSize(),
                    onOpenDebug = onOpenDebug
                )
            }
        }
    }
}
