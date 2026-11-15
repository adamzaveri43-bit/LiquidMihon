package eu.kanade.presentation.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService

/**
 * Whether the device asks apps to go easy right now: battery saver is on, or the phone is warm enough that the
 * system has started throttling (phones like the Galaxy S25 FE get there quickly under sustained GPU load).
 *
 * Decoration such as animated backgrounds should hold still while this is true.
 */
@Composable
fun rememberDeviceConstrained(): State<Boolean> {
    val context = LocalContext.current
    val powerManager = context.getSystemService<PowerManager>()
    return produceState(initialValue = powerManager?.isConstrained() == true, powerManager) {
        if (powerManager == null) return@produceState
        val update = { value = powerManager.isConstrained() }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = update()
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        val thermalListener = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            PowerManager.OnThermalStatusChangedListener { update() }
                .also { powerManager.addThermalStatusListener(ContextCompat.getMainExecutor(context), it) }
        } else {
            null
        }
        update()

        awaitDispose {
            context.unregisterReceiver(receiver)
            if (thermalListener != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                powerManager.removeThermalStatusListener(thermalListener)
            }
        }
    }
}

private fun PowerManager.isConstrained(): Boolean {
    if (isPowerSaveMode) return true
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
        currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE
}
