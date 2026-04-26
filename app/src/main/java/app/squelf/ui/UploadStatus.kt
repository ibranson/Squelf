package app.squelf.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.util.UUID

@Composable
fun rememberUploadStatus(uploadId: UUID?): String? {
    if (uploadId == null) return null
    val context = LocalContext.current
    val flow = remember(uploadId) {
        WorkManager.getInstance(context).getWorkInfoByIdFlow(uploadId)
    }
    val info by flow.collectAsStateWithLifecycle(initialValue = null)
    return when (info?.state) {
        WorkInfo.State.ENQUEUED -> "Queued"
        WorkInfo.State.RUNNING -> "Uploading…"
        WorkInfo.State.SUCCEEDED -> "Uploaded"
        WorkInfo.State.FAILED -> "Upload failed"
        WorkInfo.State.CANCELLED -> "Upload cancelled"
        WorkInfo.State.BLOCKED -> "Waiting for network"
        null -> null
    }
}
