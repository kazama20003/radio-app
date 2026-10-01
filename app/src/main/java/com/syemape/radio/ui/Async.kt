package com.syemape.radio.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState

/**
 * Carga datos del backend al entrar en composición (y cuando cambien [keys]).
 * Devuelve null mientras carga, luego un Result con el valor o el error.
 */
@Composable
fun <T> rememberAsync(vararg keys: Any?, loader: suspend () -> T): State<Result<T>?> {
    return produceState<Result<T>?>(null, *keys) {
        value = runCatching { loader() }
    }
}
