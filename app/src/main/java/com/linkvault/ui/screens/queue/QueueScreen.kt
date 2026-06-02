package com.linkvault.ui.screens.queue

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linkvault.data.local.entity.DownloadEntity
import com.linkvault.data.local.entity.DownloadStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    onNavigateUp: () -> Unit,
    viewModel: QueueViewModel = hiltViewModel()
) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Download Queue", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::clearCompleted) {
                        Icon(Icons.Default.ClearAll, contentDescription = "Clear Completed")
                    }
                    IconButton(onClick = viewModel::resumeQueue) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Resume Queue")
                    }
                    IconButton(onClick = viewModel::pauseQueue) {
                        Icon(Icons.Default.Pause, contentDescription = "Pause Queue")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (downloads.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Queue is empty", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(downloads, key = { it.id }) { download ->
                    DownloadItem(
                        download = download,
                        onRemove = { viewModel.removeDownload(download) },
                        onRetry = { viewModel.retryDownload(download) }
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadItem(
    download: DownloadEntity,
    onRemove: () -> Unit,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = getStatusIcon(download.status),
                    contentDescription = null,
                    tint = getStatusColor(download.status)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (download.title.isNotEmpty()) download.title else download.url,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = download.status.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = getStatusColor(download.status)
                    )
                }
                if (download.status == DownloadStatus.FAILED) {
                    IconButton(onClick = onRetry) {
                        Icon(Icons.Default.Refresh, contentDescription = "Retry")
                    }
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove")
                }
            }

            if (download.status == DownloadStatus.DOWNLOADING || download.status == DownloadStatus.PREPARING) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { download.progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${download.progress.toInt()}%", style = MaterialTheme.typography.labelSmall)
                    Text(download.speed, style = MaterialTheme.typography.labelSmall)
                    Text(download.eta, style = MaterialTheme.typography.labelSmall)
                }
            }
            
            if (download.error != null) {
                Text(
                    text = download.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun getStatusIcon(status: DownloadStatus): ImageVector {
    return when (status) {
        DownloadStatus.WAITING -> Icons.Default.Schedule
        DownloadStatus.PREPARING -> Icons.Default.SettingsSuggest
        DownloadStatus.DOWNLOADING -> Icons.Default.Download
        DownloadStatus.COMPLETED -> Icons.Default.CheckCircle
        DownloadStatus.FAILED -> Icons.Default.Error
        DownloadStatus.CANCELED -> Icons.Default.Cancel
    }
}

@Composable
fun getStatusColor(status: DownloadStatus) = when (status) {
    DownloadStatus.WAITING -> MaterialTheme.colorScheme.onSurfaceVariant
    DownloadStatus.PREPARING -> MaterialTheme.colorScheme.primary
    DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary
    DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.primary
    DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
    DownloadStatus.CANCELED -> MaterialTheme.colorScheme.error
}
