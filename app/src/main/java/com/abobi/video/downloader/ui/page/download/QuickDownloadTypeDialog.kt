package com.abobi.video.downloader.ui.page.download

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.abobi.video.downloader.R
import com.abobi.video.downloader.ui.component.SegmentedButtonValues
import com.abobi.video.downloader.ui.component.SingleChoiceSegmentedButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickDownloadTypeDialog(
    showDialog: Boolean,
    onDismissRequest: () -> Unit,
    onDownloadConfirm: (extractAudio: Boolean) -> Unit,
) {
    if (!showDialog) return

    var extractAudio by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = stringResource(R.string.quick_download_type_title))
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.quick_download_type_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 16.dp),
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SingleChoiceSegmentedButton(
                        text = stringResource(R.string.video),
                        selected = !extractAudio,
                        position = SegmentedButtonValues.START,
                    ) {
                        extractAudio = false
                    }
                    SingleChoiceSegmentedButton(
                        text = stringResource(R.string.audio),
                        selected = extractAudio,
                        position = SegmentedButtonValues.END,
                    ) {
                        extractAudio = true
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDownloadConfirm(extractAudio) }) {
                Text(stringResource(R.string.download))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
