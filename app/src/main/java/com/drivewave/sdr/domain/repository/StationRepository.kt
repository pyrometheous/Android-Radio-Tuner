package com.drivewave.sdr.domain.repository

import com.drivewave.sdr.domain.model.RadioBand
import com.drivewave.sdr.domain.model.Station
import kotlinx.coroutines.flow.Flow

/** Contract for station persistence and retrieval. */
interface StationRepository {
    /** Live stream of all stations from the most recent scan session. */
    fun observeAllStations(): Flow<List<Station>>

    /** Live stream of favorite stations, ordered by favoriteOrderIndex. */
    fun observeFavorites(): Flow<List<Station>>

    /** Get a single station by ID. */
    suspend fun getStation(id: String): Station?

    /** Get a station by exact frequency and band. */
    suspend fun getStationByFrequency(frequencyMhz: Float, band: RadioBand): Station?

    /**
     * Replace all non-favorite stations in the database with the new scan
     * results, preserving favorite state where frequencies match.
     */
    suspend fun replaceScannedStations(stations: List<Station>, scanSessionId: String)

    /** Insert or update a single station. */
    suspend fun upsertStation(station: Station)

    /** Toggle favorite state for a station. */
    suspend fun setFavorite(stationId: String, isFavorite: Boolean)

    /** Update the favorite order for preset reordering. */
    suspend fun updateFavoriteOrder(stationId: String, newIndex: Int)

    /** Set a user-defined label for a station. */
    suspend fun setUserLabel(stationId: String, label: String?)

    /** Delete all non-favorite stations (used before a fresh scan). */
    suspend fun clearNonFavoriteStations()

    /** Full text search across name, radioText, and userLabel. */
    suspend fun searchStations(query: String): List<Station>
}
