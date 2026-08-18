package com.karin.hyperpill.pods

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PillProductsTest {

    @Test
    fun `known Pill device names resolve to expected products`() {
        val cases = listOf(
            "MOONDROP Pill" to "20f874df-7f71-4446-880c-95bbc39995d4",
            "PILL" to "7afb7e3c-99c6-45be-b0a8-6adb8603643a",
            "Pill Gotoh Hitori" to "fb36c2bb-845d-4e0a-9b83-193b046bc6cb",
            "Pill Ijichi Nijika" to "91e6febd-d61b-4849-9c0f-5d4e9627700d",
            "Pill Yamada Ryo" to "655903e7-046f-49d8-be63-bbadb3ea7881",
            "Pill Kita Ikuyo" to "42b775b3-2781-47f2-95b1-86ef7de4f9bd",
            "PANDAER Open Air Pill" to "3795b453-41f8-4f7b-aaa8-2709481a2f91",
            "LAPLACE-OBA-Ⅱ" to "0767fc45-888d-4e99-b81d-c0566a42b4a2"
        )

        cases.forEach { (name, uuid) ->
            val product = PillProducts.fromDeviceName(name)
            assertNotNull("device name should resolve: $name", product)
            assertEquals("uuid mismatch for $name", uuid, product?.uuid)
        }
    }

    @Test
    fun `OBA II variants with ASCII hyphen or partial names resolve`() {
        listOf(
            "LAPLACE-OBA-II",
            "LAPLACE-OBA-Ⅱ",
            "OBA-II",
            "OBA-Ⅱ",
            "laplace-oba-ii",
            "Laplace OBA 2"
        ).forEach { name ->
            val product = PillProducts.fromDeviceName(name)
            assertNotNull("OBA variant should resolve: $name", product)
            assertEquals(
                "OBA variant should point to OBA-II: $name",
                "0767fc45-888d-4e99-b81d-c0566a42b4a2",
                product?.uuid
            )
        }
    }

    @Test
    fun `Bocchi collab products are marked as collab`() {
        listOf(
            "Pill Gotoh Hitori",
            "Pill Ijichi Nijika",
            "Pill Yamada Ryo",
            "Pill Kita Ikuyo"
        ).forEach { name ->
            val product = PillProducts.fromDeviceName(name)
            assertNotNull(product)
            assertTrue("$name should be a collab", product?.collabBrand?.contains("Bocchi") == true)
        }
    }

    @Test
    fun `OBA II is marked as Reverse 1999 collab`() {
        val product = PillProducts.fromDeviceName("LAPLACE-OBA-Ⅱ")
        assertNotNull(product)
        assertTrue(
            "OBA-II should mention Reverse 1999",
            product?.collabBrand?.contains("1999") == true
        )
    }

    @Test
    fun `standard Pill is not collab`() {
        val standard = PillProducts.fromDeviceName("MOONDROP Pill")
        assertNotNull(standard)
        assertNull(standard?.collabBrand)

        val legacy = PillProducts.fromDeviceName("PILL")
        assertNotNull(legacy)
        assertNull(legacy?.collabBrand)
    }

    @Test
    fun `unknown device names resolve to null`() {
        listOf("Unknown Earbuds", "AirPods Pro", "Sony WH-1000XM5", "").forEach { name ->
            assertNull("$name should not match", PillProducts.fromDeviceName(name))
        }
    }

    @Test
    fun `static table covers all known Pill and OBA product UUIDs`() {
        val expectedUuids = setOf(
            "20f874df-7f71-4446-880c-95bbc39995d4",
            "7afb7e3c-99c6-45be-b0a8-6adb8603643a",
            "fb36c2bb-845d-4e0a-9b83-193b046bc6cb",
            "91e6febd-d61b-4849-9c0f-5d4e9627700d",
            "655903e7-046f-49d8-be63-bbadb3ea7881",
            "42b775b3-2781-47f2-95b1-86ef7de4f9bd",
            "3795b453-41f8-4f7b-aaa8-2709481a2f91",
            "0767fc45-888d-4e99-b81d-c0566a42b4a2"
        )
        val actual = PillProducts.all.map { it.uuid }.toSet()
        assertEquals(expectedUuids, actual)
    }
}
