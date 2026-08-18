package com.karin.hyperpill.utils

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import com.karin.hyperpill.R
import com.karin.hyperpill.hook.Log
import com.karin.hyperpill.pods.BatteryParams
import com.xzakota.hyper.notification.focus.FocusNotification

@SuppressLint("WrongConstant")
object FocusIslandUtil {
    private const val TAG = "HyperPILL-FocusIsland"
    private const val CHANNEL_ID = "hyperpill_focus_island"
    private const val CHANNEL_NAME = "HyperPILL Battery"
    private const val NOTIFICATION_ID = 10086
    private const val MODULE_PACKAGE = "com.karin.hyperpill"
    private const val DISMISS_DELAY_MS = 4000L

    fun showBatteryIsland(context: Context, battery: BatteryParams, deviceName: String?): Boolean {
        try {
            val leftConnected = battery.left?.isConnected == true
            val rightConnected = battery.right?.isConnected == true
            if (!leftConnected && !rightConnected) return false

            val leftText = if (leftConnected) "${battery.left!!.battery}" else "-"
            val rightText = if (rightConnected) "${battery.right!!.battery}" else "-"

            val moduleContext = context.createPackageContext(
                MODULE_PACKAGE, Context.CONTEXT_IGNORE_SECURITY
            )
            val leftBitmap = BitmapFactory.decodeResource(
                moduleContext.resources,
                DeviceIconProvider.getDeviceLeftIconResId(deviceName)
            )
            val rightBitmap = BitmapFactory.decodeResource(
                moduleContext.resources,
                DeviceIconProvider.getDeviceRightIconResId(deviceName)
            )
            if (leftBitmap == null || rightBitmap == null) {
                Log.e(TAG, "Failed to decode earphone icon bitmaps")
                return false
            }

            val leftIcon = Icon.createWithBitmap(leftBitmap)
            val rightIcon = Icon.createWithBitmap(rightBitmap)

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                    setSound(null, null)
                    enableVibration(false)
                    setAllowBubbles(true)
                }
            )

            val contentParts = mutableListOf<String>()
            if (leftConnected) contentParts.add("L: ${battery.left!!.battery}%")
            if (rightConnected) contentParts.add("R: ${battery.right!!.battery}%")
            val contentText = contentParts.joinToString("  ")

            val extras = FocusNotification.buildV3 {
                val picLeft = createPicture("key_pic_left", leftIcon)
                val picRight = createPicture("key_pic_right", rightIcon)

                enableFloat = true
                ticker = "HyperPILL"
                tickerPic = picLeft

                isShowNotification = false
                island {
                    islandProperty = 1
                    bigIslandArea {
                        imageTextInfoLeft {
                            type = 1
                            picInfo {
                                type = 1
                                pic = picLeft
                            }
                            textInfo {
                                title = leftText
                                content = "%"
                            }
                        }
                        imageTextInfoRight {
                            type = 2
                            picInfo {
                                type = 1
                                pic = picRight
                            }
                            textInfo {
                                title = rightText
                                content = "%"
                            }
                        }
                    }
                    shareData {
                        title = "HyperPILL"
                        content = contentText
                        shareContent = contentText
                    }
                }
            } ?: return false

            val notification = Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle("HyperPILL")
                .setContentText(contentText)
                .setTicker("HyperPILL")
                .addExtras(extras)
                .build()

            nm.notify(NOTIFICATION_ID, notification)

            Handler(Looper.getMainLooper()).postDelayed({
                try { nm.cancel(NOTIFICATION_ID) } catch (_: Exception) {}
            }, DISMISS_DELAY_MS)

            Log.d(TAG, "Focus Island shown: L=$leftText% R=$rightText%")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show Focus Island", e)
            return false
        }
    }
}
