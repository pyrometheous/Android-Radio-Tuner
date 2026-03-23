package com.drivewave.sdr.ui.navigation

import kotlinx.serialization.Serializable

/** Type-safe navigation destinations (Navigation Compose 2.8+). */
sealed interface Dest {
    @Serializable data object Tuner : Dest
    @Serializable data object Stations : Dest
    @Serializable data object Favorites : Dest
    @Serializable data object Recordings : Dest
    @Serializable data object Settings : Dest
    @Serializable data object Diagnostics : Dest
}
