package com.drivewave.sdr.data.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.drivewave.sdr.data.model.StationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StationDao {

    @Query("SELECT * FROM stations ORDER BY frequencyMhz ASC")
    fun observeAll(): Flow<List<StationEntity>>

    @Query("SELECT * FROM stations WHERE isFavorite = 1 ORDER BY favoriteOrderIndex ASC, frequencyMhz ASC")
    fun observeFavorites(): Flow<List<StationEntity>>

    @Query("SELECT * FROM stations WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): StationEntity?

    @Query("SELECT * FROM stations WHERE ABS(frequencyMhz - :freq) < 0.01 AND band = :band LIMIT 1")
    suspend fun getByFrequency(freq: Float, band: String): StationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stations: List<StationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(station: StationEntity)

    @Query("UPDATE stations SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean)

    @Query("UPDATE stations SET favoriteOrderIndex = :index WHERE id = :id")
    suspend fun updateFavoriteOrder(id: String, index: Int)

    @Query("UPDATE stations SET userLabel = :label WHERE id = :id")
    suspend fun setUserLabel(id: String, label: String?)

    @Query("DELETE FROM stations WHERE isFavorite = 0")
    suspend fun deleteNonFavorites()

    @Query("""
        SELECT * FROM stations WHERE
            LOWER(displayName) LIKE '%' || LOWER(:query) || '%' OR
            LOWER(userLabel) LIKE '%' || LOWER(:query) || '%' OR
            LOWER(rds_ps) LIKE '%' || LOWER(:query) || '%' OR
            LOWER(rds_rt) LIKE '%' || LOWER(:query) || '%'
        ORDER BY signalConfidence DESC
        LIMIT 50
    """)
    suspend fun search(query: String): List<StationEntity>
}
