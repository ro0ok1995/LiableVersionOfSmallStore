package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.backup.BackupManager
import com.example.data.backup.BackupPayload
import com.example.data.repository.StoreRepository
import com.example.model.StoreInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class BackupRestoreViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: StoreRepository = StoreRepository.getInstance(application)
) : AndroidViewModel(application) {

    val pendingRestorePayload = MutableStateFlow<BackupPayload?>(null)
    val showRestoreConflictSheet = MutableStateFlow(false)

    fun exportBackup(context: Context, uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val payload = repository.getAllDataForBackup()
                val jsonString = BackupManager.serialize(payload)
                val success = BackupManager.writeToUri(context.contentResolver, uri, jsonString)
                onResult(success)
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false)
            }
        }
    }

    fun prepareRestore(context: Context, uri: Uri, onError: () -> Unit, onSuccessSilent: () -> Unit) {
        viewModelScope.launch {
            try {
                val jsonString = BackupManager.readFromUri(context.contentResolver, uri)
                if (jsonString.isNullOrBlank()) {
                    onError()
                    return@launch
                }
                val payload = BackupManager.deserialize(jsonString, context)
                val currentInfo = repository.getStoreInfoSnapshot()

                val differs = isStoreInfoDifferent(payload.storeInfoAtBackupTime, currentInfo)
                if (differs) {
                    pendingRestorePayload.value = payload
                    showRestoreConflictSheet.value = true
                } else {
                    repository.restoreDataFromBackup(payload, replaceStoreInfo = false)
                    pendingRestorePayload.value = null
                    showRestoreConflictSheet.value = false
                    onSuccessSilent()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onError()
            }
        }
    }

    fun confirmRestore(replaceStoreInfo: Boolean, onComplete: () -> Unit) {
        val payload = pendingRestorePayload.value ?: return
        viewModelScope.launch {
            try {
                repository.restoreDataFromBackup(payload, replaceStoreInfo = replaceStoreInfo)
                onComplete()
            } finally {
                pendingRestorePayload.value = null
                showRestoreConflictSheet.value = false
            }
        }
    }

    fun cancelRestore() {
        pendingRestorePayload.value = null
        showRestoreConflictSheet.value = false
    }

    private fun isStoreInfoDifferent(backup: StoreInfo, current: StoreInfo): Boolean {
        return backup.storeName.trim() != current.storeName.trim() ||
                backup.ownerName.trim() != current.ownerName.trim() ||
                backup.phone.trim() != current.phone.trim() ||
                backup.address.trim() != current.address.trim() ||
                backup.taxNumber.trim() != current.taxNumber.trim() ||
                backup.crNumber.trim() != current.crNumber.trim()
    }
}
