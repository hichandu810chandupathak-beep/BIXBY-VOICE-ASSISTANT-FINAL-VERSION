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

/** Handles safe, local device commands before Gemini is contacted. */
class CommandExecutor(private val context: Context) {

    sealed class Result {
        data class Handled(val message: String) : Result()
        data object NotHandled : Result()
    }

    fun executeIfSupported(rawText: String): Result {
        val text = rawText.trim().lowercase()
        if (text.isEmpty()) return Result.NotHandled

        return when {
            isFlashlightCommand(text) -> Result.Handled(setFlashlight(desiredFlashlightState(text)))
            isBluetoothCommand(text) -> Result.Handled(handleBluetooth(text))
            isWifiSettingsCommand(text) -> {
                openSettings(Settings.ACTION_WIFI_SETTINGS)
                Result.Handled("Opening Wi-Fi settings.")
            }
            else -> Result.NotHandled
        }
    }

    private fun isFlashlightCommand(text: String): Boolean =
        (text.contains("flashlight") || text.contains("torch")) &&
            (text.contains("turn on") || text.contains("turn off") || text.contains("switch") || text.contains("toggle"))

    private fun isBluetoothCommand(text: String): Boolean =
        text.contains("bluetooth") &&
            (text.contains("turn on") || text.contains("turn off") || text.contains("switch") || text.contains("toggle"))

    private fun isWifiSettingsCommand(text: String): Boolean =
        (text.contains("wifi") || text.contains("wi-fi")) &&
            (text.contains("settings") || text.contains("open") || text.contains("show"))

    private fun desiredFlashlightState(text: String): Boolean = when {
        text.contains("turn off") -> false
        text.contains("turn on") -> true
        else -> !torchState
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

    private fun handleBluetooth(text: String): String {
        val adapter = BluetoothAdapter.getDefaultAdapter()
            ?: return "Bluetooth is not available on this phone."

        val wantsOn = text.contains("turn on")
        val wantsOff = text.contains("turn off")

        return try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                openSettings(Settings.ACTION_BLUETOOTH_SETTINGS)
                "Opening Bluetooth settings. Android may require you to confirm the change."
            } else if (wantsOn) {
                @Suppress("DEPRECATION")
                val changed = adapter.enable()
                if (changed) "Bluetooth turned on." else {
                    openSettings(Settings.ACTION_BLUETOOTH_SETTINGS)
                    "Opening Bluetooth settings."
                }
            } else if (wantsOff) {
                @Suppress("DEPRECATION")
                val changed = adapter.disable()
                if (changed) "Bluetooth turned off." else {
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

    private fun openSettings(action: String) {
        runCatching {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    companion object {
        @Volatile private var torchState: Boolean = false
    }
}
