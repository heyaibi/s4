/*
 * Copyright (C) 2026 The S4 project contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.s4.buildmatrix

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards ARBITER-001: the app must package a native library for every ABI that
 * `minSdk` admits. `minSdk = 26` allows 32-bit ARM (armeabi-v7a) devices; if
 * that ABI is missing from `abiFilters`, install succeeds but the first
 * `System.loadLibrary("slip39_jni")` throws an uncaught UnsatisfiedLinkError.
 */
class BuildMatrixTest {

    private fun appBuildFile(): File =
        listOf(File("build.gradle.kts"), File("app/build.gradle.kts"))
            .firstOrNull { it.exists() }
            ?: error("could not locate app/build.gradle.kts")

    @Test
    fun abiFiltersIncludeArmeabiV7a() {
        val gradle = appBuildFile().readText()
        assertTrue(
            "abiFilters must include armeabi-v7a so 32-bit ARM devices do not crash",
            gradle.contains("armeabi-v7a"),
        )
    }

    @Test
    fun minSdk26IsCoveredByTheAbiMatrix() {
        val gradle = appBuildFile().readText()
        val minSdk = Regex("minSdk\\s*=\\s*(\\d+)").find(gradle)?.groupValues?.get(1)?.toInt()
        assertTrue("minSdk must be declared", minSdk != null)
        assertTrue(
            "minSdk $minSdk admits 32-bit ARM devices, so armeabi-v7a must be packaged",
            gradle.contains("armeabi-v7a"),
        )
    }
}
