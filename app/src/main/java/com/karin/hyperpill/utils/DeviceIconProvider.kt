package com.karin.hyperpill.utils

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import com.karin.hyperpill.R
import com.karin.hyperpill.hook.Log
import com.karin.hyperpill.pods.PillProduct
import com.karin.hyperpill.pods.PillProducts

/**
 * Device icon provider for the persistent headset notification.
 *
 * Returns a collab/product-specific icon based on the device name.
 * All icons are provided by the user in `res/` and copied into drawable-nodpi.
 */
object DeviceIconProvider {

    private val uuidToResId: Map<String, Int> = mapOf(
        "20f874df-7f71-4446-880c-95bbc39995d4" to R.drawable.pill_moondrop,
        "7afb7e3c-99c6-45be-b0a8-6adb8603643a" to R.drawable.pill_legacy,
        "fb36c2bb-845d-4e0a-9b83-193b046bc6cb" to R.drawable.pill_gotoh_hitori,
        "91e6febd-d61b-4849-9c0f-5d4e9627700d" to R.drawable.pill_ijichi_nijika,
        "655903e7-046f-49d8-be63-bbadb3ea7881" to R.drawable.pill_yamada_ryo,
        "42b775b3-2781-47f2-95b1-86ef7de4f9bd" to R.drawable.pill_kita_ikuyo,
        "3795b453-41f8-4f7b-aaa8-2709481a2f91" to R.drawable.pill_pandaer_open_air,
        "0767fc45-888d-4e99-b81d-c0566a42b4a2" to R.drawable.pill_laplace_oba_ii
    )

    private val uuidToLeftResId: Map<String, Int> = mapOf(
        "20f874df-7f71-4446-880c-95bbc39995d4" to R.drawable.pill_moondrop_l,
        "7afb7e3c-99c6-45be-b0a8-6adb8603643a" to R.drawable.pill_legacy_l,
        "fb36c2bb-845d-4e0a-9b83-193b046bc6cb" to R.drawable.pill_gotoh_hitori_l,
        "91e6febd-d61b-4849-9c0f-5d4e9627700d" to R.drawable.pill_ijichi_nijika_l,
        "655903e7-046f-49d8-be63-bbadb3ea7881" to R.drawable.pill_yamada_ryo_l,
        "42b775b3-2781-47f2-95b1-86ef7de4f9bd" to R.drawable.pill_kita_ikuyo_l,
        "3795b453-41f8-4f7b-aaa8-2709481a2f91" to R.drawable.pill_pandaer_open_air_l,
        "0767fc45-888d-4e99-b81d-c0566a42b4a2" to R.drawable.pill_laplace_oba_ii_l
    )

    private val uuidToRightResId: Map<String, Int> = mapOf(
        "20f874df-7f71-4446-880c-95bbc39995d4" to R.drawable.pill_moondrop_r,
        "7afb7e3c-99c6-45be-b0a8-6adb8603643a" to R.drawable.pill_legacy_r,
        "fb36c2bb-845d-4e0a-9b83-193b046bc6cb" to R.drawable.pill_gotoh_hitori_r,
        "91e6febd-d61b-4849-9c0f-5d4e9627700d" to R.drawable.pill_ijichi_nijika_r,
        "655903e7-046f-49d8-be63-bbadb3ea7881" to R.drawable.pill_yamada_ryo_r,
        "42b775b3-2781-47f2-95b1-86ef7de4f9bd" to R.drawable.pill_kita_ikuyo_r,
        "3795b453-41f8-4f7b-aaa8-2709481a2f91" to R.drawable.pill_pandaer_open_air_r,
        "0767fc45-888d-4e99-b81d-c0566a42b4a2" to R.drawable.pill_laplace_oba_ii_r
    )

    fun getDeviceIconResId(deviceName: String?): Int {
        val product = resolveAndLog(deviceName, "main")
        return product?.let { uuidToResId[it.uuid] } ?: R.drawable.pill_moondrop
    }

    fun getDeviceLeftIconResId(deviceName: String?): Int {
        val product = resolveAndLog(deviceName, "left")
        return product?.let { uuidToLeftResId[it.uuid] } ?: R.drawable.pill_moondrop_l
    }

    fun getDeviceRightIconResId(deviceName: String?): Int {
        val product = resolveAndLog(deviceName, "right")
        return product?.let { uuidToRightResId[it.uuid] } ?: R.drawable.pill_moondrop_r
    }

    private fun resolveAndLog(deviceName: String?, kind: String): PillProduct? {
        val product = PillProducts.fromDeviceName(deviceName)
        Log.n(
            "HyperPILL-Icon",
            "$kind resolve device='${deviceName ?: "null"}' " +
                "uuid=${product?.uuid ?: "UNKNOWN"} " +
                "product=${product?.name ?: "UNKNOWN"} " +
                "fallback=${product == null}"
        )
        return product
    }

    fun getDeviceIcon(context: Context, deviceName: String?): Icon {
        val moduleContext = runCatching {
            context.createPackageContext("com.karin.hyperpill", Context.CONTEXT_IGNORE_SECURITY)
        }.getOrNull() ?: context

        val product = resolveAndLog(deviceName, "notification")
        val resId = product?.let { uuidToResId[it.uuid] } ?: R.drawable.pill_moondrop
        val resName = runCatching { moduleContext.resources.getResourceEntryName(resId) }.getOrNull()
        Log.n("HyperPILL-Icon", "notification icon res=$resName id=$resId")

        val bitmap = BitmapFactory.decodeResource(moduleContext.resources, resId)
            ?: BitmapFactory.decodeResource(moduleContext.resources, R.drawable.pill_moondrop)
            ?: BitmapFactory.decodeResource(context.resources, android.R.drawable.stat_sys_data_bluetooth)
        return Icon.createWithBitmap(bitmap)
    }
}
