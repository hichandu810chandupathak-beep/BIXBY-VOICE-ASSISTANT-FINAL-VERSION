package com.bixby.voiceassistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import androidx.core.content.ContextCompat

/** Handles only call and message commands that must use local device APIs. */
class CommandExecutor(private val context: Context) {
    sealed class Result {
        data class Handled(val message: String) : Result()
        data object NotHandled : Result()
    }

    fun executeIfSupported(rawText: String): Result {
        val command = rawText.trim()
        val lower = command.lowercase()
        if (!lower.contains("call") && !lower.contains("message")) return Result.NotHandled
        return if (lower.contains("message")) Result.Handled(handleMessage(command)) else Result.Handled(handleCall(command))
    }

    private fun handleMessage(command: String): String {
        val withoutPrefix = command.replace(Regex("^.*?\\bmessage\\b\\s*", RegexOption.IGNORE_CASE), "").trim()
        val targetAndBody = Regex("^(?:to\\s+)?(.+?)(?:\\s+(?:saying|that says|says|:)\\s+)(.+)$", RegexOption.IGNORE_CASE).find(withoutPrefix)
            ?: return "Tell me the contact and message, for example: message Rahul saying hello."
        val target = targetAndBody.groupValues[1].trim()
        val body = targetAndBody.groupValues[2].trim()
        val number = extractPhoneNumber(target) ?: resolveContactNumber(target)
            ?: return "I couldn't find that contact."
        return try {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}")).apply {
                putExtra("sms_body", body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opening messages to $target."
        } catch (_: Exception) {
            "I couldn't open messaging right now."
        }
    }

    private fun handleCall(command: String): String {
        val target = command.replace(Regex("^.*?\\b(?:call|dial)\\b\\s*", RegexOption.IGNORE_CASE), "")
            .trim().removeSuffix(".").trim()
        if (target.isEmpty()) return "Please tell me a contact name or phone number."
        val number = extractPhoneNumber(target) ?: resolveContactNumber(target)
            ?: return "I couldn't find that contact."
        val uri = Uri.parse("tel:${Uri.encode(number)}")
        val action = if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            Intent.ACTION_CALL
        } else {
            Intent.ACTION_DIAL
        }
        return try {
            context.startActivity(Intent(action, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            if (action == Intent.ACTION_CALL) "Calling $target." else "Opening the dialer for $target."
        } catch (_: SecurityException) {
            context.startActivity(Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "Opening the dialer for $target."
        } catch (_: Exception) {
            "I couldn't start the call right now."
        }
    }

    private fun extractPhoneNumber(target: String): String? {
        val cleaned = target.replace(Regex("[^0-9+*#]"), "")
        return if (cleaned.count { it.isDigit() } >= 3 && PhoneNumberUtils.isGlobalPhoneNumber(cleaned)) cleaned else null
    }

    private fun resolveContactNumber(name: String): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        return try {
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            )
            val selection = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?"
            val selectionArgs = arrayOf("%$name%")
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val numberIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (cursor.moveToFirst()) cursor.getString(numberIndex) else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
