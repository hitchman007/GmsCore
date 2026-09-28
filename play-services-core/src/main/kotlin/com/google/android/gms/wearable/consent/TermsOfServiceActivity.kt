/**
 * SPDX-FileCopyrightText: 2025 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.wearable.consent

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class TermsOfServiceActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.wearable_tos_title)
            .setMessage(R.string.wearable_tos_message)
            .setPositiveButton(R.string.allow) { _, _ -> finishWithConsent(true) }
            .setNegativeButton(R.string.deny) { _, _ -> finishWithConsent(false) }
            .setOnCancelListener { finishWithConsent(false) }
            .show()
    }

    private fun finishWithConsent(accepted: Boolean) {
        val result = Intent()
            .putExtra("consents_accepted", accepted)
            .putExtra("tos_accepted", accepted)
            .putExtra("privacy_policy_accepted", accepted)
        setResult(if (accepted) RESULT_OK else RESULT_CANCELED, result)
        finish()
    }
}
