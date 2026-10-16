/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import dev.zacsweers.metro.Inject
import io.element.android.libraries.bipe.BipePhoneNumber
import io.element.android.libraries.di.annotations.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Reads phone numbers from the address book for contact discovery. Nothing is stored: the numbers go to the lookup and are dropped. */
@Inject
class BipeAddressBook(
    @ApplicationContext private val context: Context,
) {
    fun hasAccess(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** E.164 number to the contact's name. Numbers that [BipePhoneNumber] rejects are left out; the first name for a number wins. */
    suspend fun namesByPhone(): Map<String, String> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, String>()
        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                val nameColumn = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
                val numberColumn = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    val e164 = cursor.getString(numberColumn)?.let(BipePhoneNumber::normalize) ?: continue
                    if (e164 !in result) result[e164] = cursor.getString(nameColumn).orEmpty()
                }
            }
        } catch (error: SecurityException) {
            Timber.e(error, "Bipe address book: no access")
        }
        result
    }
}
