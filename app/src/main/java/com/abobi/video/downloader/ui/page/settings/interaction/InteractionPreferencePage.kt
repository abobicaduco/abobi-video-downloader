package com.abobi.video.downloader.ui.page.settings.interaction

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import com.abobi.video.downloader.R
import com.abobi.video.downloader.ui.component.BackButton
import com.abobi.video.downloader.ui.component.LargeTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractionPreferencePage(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
        LargeTopAppBar(
            title = {
                Text(
                    text = stringResource(
                        id = R.string.interface_and_interaction
                    )
                )
            }, scrollBehavior = scrollBehavior, navigationIcon = {
                BackButton(onClick = onBack)
            }
        )
    }) {
        LazyColumn(modifier = Modifier.padding(it)) {}
    }
}
