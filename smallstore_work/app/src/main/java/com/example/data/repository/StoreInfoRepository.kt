package com.example.data.repository

import com.example.data.db.SmallStoreDatabase
import com.example.data.db.StoreInfoDao
import com.example.data.db.toEntity
import com.example.data.db.toModel
import com.example.model.StoreInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class StoreInfoRepository(
    private val storeInfoDao: StoreInfoDao
) {
    constructor(database: SmallStoreDatabase) : this(database.storeInfoDao())

    val storeInfo: Flow<StoreInfo> = storeInfoDao.getStoreInfo().map { entity ->
        entity?.toModel() ?: StoreInfo()
    }

    suspend fun isStoreInfoSaved(): Boolean {
        val entity = storeInfoDao.getStoreInfoSync()
        return entity?.isSaved == true
    }

    suspend fun getStoreInfoSnapshot(): StoreInfo {
        return storeInfoDao.getStoreInfoSync()?.toModel() ?: StoreInfo()
    }

    suspend fun saveStoreInfo(info: StoreInfo, markAsSaved: Boolean = true) {
        storeInfoDao.insertOrUpdate(info.toEntity(isSaved = markAsSaved))
    }
}
