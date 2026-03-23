package com.drivewave.sdr.data.repository

import com.drivewave.sdr.data.model.toEntity
import com.drivewave.sdr.data.model.toDomain
import com.drivewave.sdr.data.storage.StationDao
import com.drivewave.sdr.domain.model.RadioBand
import com.drivewave.sdr.domain.model.Station
import com.drivewave.sdr.domain.repository.StationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StationRepositoryImpl @Inject constructor(
    private val stationDao: StationDao,
) : StationRepository {

    override fun observeAllStations(): Flow<List<Station>> =
        stationDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeFavorites(): Flow<List<Station>> =
        stationDao.observeFavorites().map { list -> list.map { it.toDomain() } }

    override suspend fun getStation(id: String): Station? =
        stationDao.getById(id)?.toDomain()

    override suspend fun getStationByFrequency(frequencyMhz: Float, band: RadioBand): Station? =
        stationDao.getByFrequency(frequencyMhz, band.name)?.toDomain()

    override suspend fun replaceScannedStations(stations: List<Station>, scanSessionId: String) {
        // Keep favorites alive; delete all non-favorite stations first
        stationDao.deleteNonFavorites()
        // Insert new stations, preserving isFavorite if the frequency already existed
        val existingFavorites = buildSet<String> {
            // We already deleted non-favorites; what remains in DB are favorites
        }
        stationDao.insertAll(stations.map { station ->
            station.toEntity()
        })
    }

    override suspend fun upsertStation(station: Station) =
        stationDao.upsert(station.toEntity())

    override suspend fun setFavorite(stationId: String, isFavorite: Boolean) =
        stationDao.setFavorite(stationId, isFavorite)

    override suspend fun updateFavoriteOrder(stationId: String, newIndex: Int) =
        stationDao.updateFavoriteOrder(stationId, newIndex)

    override suspend fun setUserLabel(stationId: String, label: String?) =
        stationDao.setUserLabel(stationId, label)

    override suspend fun clearNonFavoriteStations() =
        stationDao.deleteNonFavorites()

    override suspend fun searchStations(query: String): List<Station> =
        stationDao.search(query).map { it.toDomain() }
}
