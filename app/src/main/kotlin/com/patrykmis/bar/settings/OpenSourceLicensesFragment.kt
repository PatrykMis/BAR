/*
 * SPDX-FileCopyrightText: 2026 Patryk Miś <foss@patrykmis.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.patrykmis.bar.settings

import android.os.Bundle
import android.util.TypedValue
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.patrykmis.bar.R

class OpenSourceLicensesFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = requireContext()
        val screen = preferenceManager.createPreferenceScreen(context)
        preferenceScreen = screen

        val category = PreferenceCategory(context).apply {
            isIconSpaceReserved = false
            title = getString(R.string.open_source_licenses_libraries)
        }
        screen.addPreference(category)

        val libraries = loadLibraries()
        if (libraries.isEmpty()) {
            category.addPreference(
                Preference(context).apply {
                    isEnabled = false
                    isIconSpaceReserved = false
                    summary = getString(R.string.open_source_licenses_empty)
                }
            )
            return
        }

        for (library in libraries) {
            category.addPreference(
                Preference(context).apply {
                    key = "library_license_${library.uniqueId}"
                    isIconSpaceReserved = false
                    title = library.name
                    summary = library.summaryText()
                    onPreferenceClickListener = Preference.OnPreferenceClickListener {
                        showLibraryDetails(library)
                        true
                    }
                }
            )
        }
    }

    private fun loadLibraries(): List<Library> {
        val json = resources.openRawResource(R.raw.aboutlibraries)
            .bufferedReader()
            .use { it.readText() }

        return Libs.Builder()
            .withJson(json)
            .build()
            .libraries
    }

    private fun Library.summaryText(): String {
        val licenses = licenseNames()
        val parts = listOfNotNull(
            artifactVersion?.takeIf { it.isNotBlank() },
            licenses.ifBlank { null }
        )

        return parts.joinToString(separator = " - ").ifBlank {
            getString(R.string.open_source_licenses_no_license)
        }
    }

    private fun Library.licenseNames(): String =
        licenses.joinToString { it.name }.ifBlank {
            getString(R.string.open_source_licenses_no_license)
        }

    private fun showLibraryDetails(library: Library) {
        val context = requireContext()
        val padding = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            DIALOG_PADDING_DP,
            resources.displayMetrics
        ).toInt()

        val text = TextView(context).apply {
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setPadding(padding, padding, padding, padding)
            setTextIsSelectable(true)
            this.text = library.detailsText()
        }

        val scrollView = NestedScrollView(context).apply {
            addView(text)
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(library.name)
            .setView(scrollView)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun Library.detailsText(): String = buildString {
        artifactVersion?.let {
            appendLine(getString(R.string.open_source_licenses_version, it))
            appendLine()
        }

        appendLine(getString(R.string.open_source_licenses_licenses, licenseNames()))

        website?.takeIf { it.isNotBlank() }?.let {
            appendLine()
            appendLine(getString(R.string.open_source_licenses_website, it))
        }

        description?.takeIf { it.isNotBlank() }?.let {
            appendLine()
            appendLine(getString(R.string.open_source_licenses_description, it))
        }

        for (license in licenses) {
            appendLine()
            appendLine(license.name)
            license.url?.takeIf { it.isNotBlank() }?.let { appendLine(it) }
            license.licenseContent?.takeIf { it.isNotBlank() }?.let {
                appendLine()
                appendLine(it)
            }
        }
    }.trim()

    private companion object {
        const val DIALOG_PADDING_DP = 24f
    }
}
