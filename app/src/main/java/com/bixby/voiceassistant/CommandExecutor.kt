package com.bixby.voiceassistant

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/** Handles local device commands before Gemini/OkHttp. */
class CommandExecutor(private val context: Context) {

    sealed class Result {
        data class Handled(val message: String) : Result()
        data object NotHandled : Result()
    }

    /**
     * Strict offline-first interceptor. Every handled command returns immediately;
     * AssistantAiHandler must not continue into the network path after Handled.
     */
    fun executeIfSupported(rawText: String): Result {
        val command = rawText.trim().lowercase()
        if (command.isEmpty()) return Result.NotHandled

        // HARD STOP: flashlight/torch is always local and never reaches OkHttp.
        if (command.contains("torch") || command.contains("flashlight")) {
            val enabled = when {
                command.contains("turn off") || command.contains("switch off") -> false
                command.contains("turn on") || command.contains("switch on") -> true
                else -> !torchState
            }
            return Result.Handled(setFlashlight(enabled))
        }

        // HARD STOP: Bluetooth is always local/settings and never reaches OkHttp.
        if (command.contains("bluetooth")) {
            return Result.Handled(handleBluetooth(command))
        }

        // HARD STOP: Wi-Fi requests are local settings navigation and never reach OkHttp.
        if (command.contains("wifi") || command.contains("wi-fi")) {
            if (command.contains("settings") || command.contains("open") || command.contains("show") ||
                command.contains("turn on") || command.contains("turn off") ||
                command.contains("switch on") || command.contains("switch off")) {
                openSettings(Settings.ACTION_WIFI_SETTINGS)
                return Result.Handled("Opening Wi-Fi settings.")
            }
        }

        // HARD STOP: explicit app launches are local and never reach OkHttp.
        if (command.startsWith("open ") || command.startsWith("launch ") || command.startsWith("start ")) {
            if (!command.contains("settings") && !command.contains("wifi") && !command.contains("wi-fi")) {
                return Result.Handled(launchApp(command))
            }
        }

        return Result.NotHandled
    }

    private fun setFlashlight(enabled: Boolean): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return "Camera permission is needed to control the flashlight."
        }

        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return "This phone does not have a controllable flashlight."

            cameraManager.setTorchMode(cameraId, enabled)
            torchState = enabled
            if (enabled) "Flashlight turned on." else "Flashlight turned off."
        } catch (_: Exception) {
            "I couldn't control the flashlight right now."
        }
    }

    private fun handleBluetooth(command: String): String {
        val adapter = BluetoothAdapter.getDefaultAdapter()
            ?: return "Bluetooth is not available on this phone."
        val wantsOn = command.contains("turn on") || command.contains("switch on")
        val wantsOff = command.contains("turn off") || command.contains("switch off")

        return try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                openSettings(Settings.ACTION_BLUETOOTH_SETTINGS)
                "Opening Bluetooth settings. Android may require you to confirm the change."
            } else if (wantsOn) {
                @Suppress("DEPRECATION")
                if (adapter.enable()) "Bluetooth turned on." else {
                    openSettings(Settings.ACTION_BLUETOOTH_SETTINGS)
                    "Opening Bluetooth settings."
                }
            } else if (wantsOff) {
                @Suppress("DEPRECATION")
                if (adapter.disable()) "Bluetooth turned off." else {
                    openSettings(Settings.ACTION_BLUETOOTH_SETTINGS)
                    "Opening Bluetooth settings."
                }
            } else {
                openSettings(Settings.ACTION_BLUETOOTH_SETTINGS)
                "Opening Bluetooth settings."
            }
        } catch (_: SecurityException) {
            openSettings(Settings.ACTION_BLUETOOTH_SETTINGS)
            "Opening Bluetooth settings. Android may require you to confirm the change."
        }
    }

    private fun launchApp(command: String): String {
        val requested = command
            .removePrefix("open ")
            .removePrefix("launch ")
            .removePrefix("start ")
            .removePrefix("the ")
            .trim()
        if (requested.isEmpty()) return "Please tell me which app to open."

        val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val matches = context.packageManager.queryIntentActivities(launchIntent, PackageManager.MATCH_ALL)
        val match = matches.firstOrNull { info ->
            info.activityInfo.packageName != context.packageName &&
                info.loadLabel(context.packageManager).toString().trim().lowercase() == requested
        } ?: matches.firstOrNull { info ->
            info.activityInfo.packageName != context.packageName &&
                info.loadLabel(context.packageManager).toString().trim().lowercase().contains(requested)
        }

        val intent = match?.activityInfo?.let { activity ->
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(
                activity.packageName,
                activity.name
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return if (intent != null) {
            context.startActivity(intent)
            "Opening ${match.loadLabel(context.packageManager)}."
        } else {
            "I couldn't find that app."
        }
    }

    private fun openSettings(action: String) {
        runCatching {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    companion object {
        @Volatile private var torchState: Boolean = false
    }
}
