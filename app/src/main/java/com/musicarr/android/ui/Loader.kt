package com.musicarr.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Ready<T>(val data: T) : LoadState<T>
    data class Failed(val message: String) : LoadState<Nothing>
}

/**
 * Load-once-with-retry for a screen's data: returns the current [LoadState]
 * and a `refresh` lambda. Reloads whenever [keys] change.
 */
@Composable
fun <T> rememberLoad(vararg keys: Any?, load: suspend () -> Result<T>): Pair<LoadState<T>, () -> Unit> {
    var tick by remember { mutableIntStateOf(0) }
    var state by remember(keys.toList()) { mutableStateOf<LoadState<T>>(LoadState.Loading) }
    LaunchedEffect(tick, keys.toList()) {
        state = LoadState.Loading
        load().fold(
            onSuccess = { state = LoadState.Ready(it) },
            onFailure = { state = LoadState.Failed(it.message ?: "Something went wrong") },
        )
    }
    return state to { tick++ }
}
