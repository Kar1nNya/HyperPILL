package com.karin.hyperpill.pods

/**
 * Pill family product identity table.
 * Source: Moondrop Link `GET https://cdn-service.moondroplab.tech/api/v1/products/all`
 * Names are also the Bluetooth broadcast names used by the app to identify each variant.
 */
data class PillProduct(
    val name: String,
    val uuid: String,
    val model: String,
    val collabBrand: String?
)

object PillProducts {

    val all: List<PillProduct> = listOf(
        PillProduct(
            name = "MOONDROP Pill",
            uuid = "20f874df-7f71-4446-880c-95bbc39995d4",
            model = "MOONDROP Pill",
            collabBrand = null
        ),
        PillProduct(
            name = "PILL",
            uuid = "7afb7e3c-99c6-45be-b0a8-6adb8603643a",
            model = "MOONDROP PILL",
            collabBrand = null
        ),
        PillProduct(
            name = "Pill Gotoh Hitori",
            uuid = "fb36c2bb-845d-4e0a-9b83-193b046bc6cb",
            model = "Pill Gotoh Hitori",
            collabBrand = "Bocchi the Rock! / 孤独摇滚"
        ),
        PillProduct(
            name = "Pill Ijichi Nijika",
            uuid = "91e6febd-d61b-4849-9c0f-5d4e9627700d",
            model = "Pill Ijichi Nijika",
            collabBrand = "Bocchi the Rock! / 孤独摇滚"
        ),
        PillProduct(
            name = "Pill Yamada Ryo",
            uuid = "655903e7-046f-49d8-be63-bbadb3ea7881",
            model = "Pill Yamada Ryo",
            collabBrand = "Bocchi the Rock! / 孤独摇滚"
        ),
        PillProduct(
            name = "Pill Kita Ikuyo",
            uuid = "42b775b3-2781-47f2-95b1-86ef7de4f9bd",
            model = "Pill Kita Ikuyo",
            collabBrand = "Bocchi the Rock! / 孤独摇滚"
        ),
        PillProduct(
            name = "PANDAER Open Air Pill",
            uuid = "3795b453-41f8-4f7b-aaa8-2709481a2f91",
            model = "PANDAER Open Air Pill",
            collabBrand = "PANDAER"
        ),
        PillProduct(
            name = "LAPLACE-OBA-Ⅱ",
            uuid = "0767fc45-888d-4e99-b81d-c0566a42b4a2",
            model = "LAPLACE-OBA-Ⅱ",
            collabBrand = "重返未来：1999 (Reverse:1999)"
        )
    )

    fun fromDeviceName(name: String?): PillProduct? {
        if (name.isNullOrBlank()) return null
        all.firstOrNull {
            it.name.equals(name, ignoreCase = true) || it.model.equals(name, ignoreCase = true)
        }?.let { return it }
        val n = name.lowercase()
        if (n.contains("oba") || n.contains("laplace")) {
            return all.firstOrNull { it.model.contains("OBA", ignoreCase = true) }
        }
        return null
    }

    fun fromUuid(uuid: String?): PillProduct? {
        if (uuid.isNullOrBlank()) return null
        return all.firstOrNull { it.uuid.equals(uuid, ignoreCase = true) }
    }
}
