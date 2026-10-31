package com.kharcha.tracker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Invokes [onResume] when this screen is (re-)entered, and when the app
 * returns to the foreground.
 *
 * Two triggers cover the two cases:
 * - The [DisposableEffect] re-setup runs every time the composable is
 *   (re-)created, which is what happens when the user comes back to this
 *   screen within the app (e.g. from the group-detail screen).
 * - The lifecycle observer fires on `ON_RESUME`, which covers the app
 *   being foregrounded while the composition is kept alive.
 *
 * The first invocation (initial entry) is expected to be a no-op: the
 * dashboard and detail view models guard [refresh] with `firstLoadComplete`
 * so it cannot race the initial load.
 */
@Composable
internal fun ReloadOnResume(onResume: () -> Unit) {
    val lifecycle: Lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onResume()
            }
        }
        lifecycle.addObserver(observer)
        // Fires on the initial composition and on every (re-)entry.
        onResume()
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }
}
