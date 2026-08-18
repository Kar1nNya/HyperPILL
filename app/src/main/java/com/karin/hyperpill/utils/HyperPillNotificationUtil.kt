package com.karin.hyperpill.utils

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Bundle
import com.karin.hyperpill.hook.Log
import com.karin.hyperpill.pods.BatteryParams
import com.karin.hyperpill.utils.SystemApisUtils.cancelAsUser
import com.karin.hyperpill.utils.SystemApisUtils.notifyAsUser
import com.xzakota.hyper.notification.focus.FocusNotification

@SuppressLint("MissingPermission", "WrongConstant")
object HyperPillNotificationUtil {
    private const val TAG = "HyperPILL-Notification"
    private const val NOTIFICATION_ID = 10003
    private const val NOTIFICATION_TAG_PREFIX = "HyperPILL"

    fun showPodsNotification(context: Context, device: BluetoothDevice, battery: BatteryParams) {
        val address = runCatching { device.address }.getOrNull() ?: return
        val alias = runCatching { device.alias ?: device.name }.getOrNull() ?: address
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("HyperPILL$address", alias, NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(null, null)
                setAllowBubbles(true)
            }
        )

        val contentText = buildContentText(battery)
        val headsetIcon = DeviceIconProvider.getDeviceIcon(context, alias)
        val contentIntent = Intent(Intent.ACTION_MAIN)
            .setClassName("com.karin.hyperpill", "com.karin.hyperpill.MainActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val disconnectAction = buildDisconnectAction(context, device, address)
        val focusExtras = FocusNotification.buildV3 {
            val logo = createPicture("key_headset", headsetIcon)
            enableFloat = true
            ticker = alias
            updatable = true

            iconTextInfo {
                animIconInfo {
                    type = 0
                    src = logo
                }
                title = alias
                content = contentText
            }

            textButton {
                addActionInfo {
                    action = createAction("key_disconnect", disconnectAction)
                    actionTitle = "断开"
                }
            }
        }

        val notification = Notification.Builder(context, "HyperPILL$address")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setWhen(0L)
            .setTicker(alias)
            .setContentTitle(alias)
            .setContentText(contentText)
            .setContentIntent(pendingIntent)
            .addAction(disconnectAction)
            .apply { focusExtras?.let { addExtras(it) } }
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()

        runCatching {
            nm.notifyAsUser("$NOTIFICATION_TAG_PREFIX$address", NOTIFICATION_ID, notification, SystemApisUtils.getUserAllUserHandle())
            Log.n(TAG, "pods notification shown $alias $contentText")
        }.onFailure {
            Log.w(TAG, "pods notification failed", it)
        }
    }

    fun cancelPodsNotification(context: Context, address: String?) {
        if (address.isNullOrBlank()) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching {
            nm.cancelAsUser("$NOTIFICATION_TAG_PREFIX$address", NOTIFICATION_ID, SystemApisUtils.getUserAllUserHandle())
            Log.n(TAG, "pods notification cancelled $address")
        }.onFailure {
            Log.w(TAG, "cancel pods notification failed", it)
        }
    }

    private fun buildContentText(battery: BatteryParams): String {
        val parts = mutableListOf<String>()
        battery.left?.takeIf { it.isConnected }?.let { parts.add("L ${it.battery}%") }
        battery.right?.takeIf { it.isConnected }?.let { parts.add("R ${it.battery}%") }
        battery.case?.takeIf { it.isConnected }?.let { parts.add("C ${it.battery}%") }
        return parts.joinToString("  ").ifBlank { "已连接" }
    }

    private fun buildDisconnectAction(context: Context, device: BluetoothDevice, address: String): Notification.Action {
        val bundle = Bundle().apply { putParcelable("Device", device) }
        val intent = Intent("com.android.bluetooth.headset.notification").apply {
            putExtra("btData", bundle)
            putExtra("disconnect", "1")
            setIdentifier("HyperPILL$address")
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Action.Builder(
            Icon.createWithResource(context, android.R.drawable.ic_menu_close_clear_cancel),
            "断开",
            pendingIntent
        ).build()
    }

}
