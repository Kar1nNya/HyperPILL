package com.karin.hyperpill.utils

import android.app.Notification
import android.app.NotificationManager
import android.os.UserHandle
import com.karin.hyperpill.hook.callMethod

object SystemApisUtils {

    fun getUserAllUserHandle(): UserHandle {
        return UserHandle::class.java.getDeclaredField("ALL").apply { isAccessible = true }.get(null) as UserHandle
    }

    fun NotificationManager.notifyAsUser(tag: String, id: Int, notification: Notification, userHandle: UserHandle) {
        callMethod(this, "notifyAsUser", tag, id, notification, userHandle)
    }

    fun NotificationManager.cancelAsUser(tag: String, id: Int, userHandle: UserHandle) {
        callMethod(this, "cancelAsUser", tag, id, userHandle)
    }
}
