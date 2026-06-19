package com.abobi.video.downloader.ui.page.settings.network

import android.content.res.Configuration
import android.webkit.CookieManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.FileCopy
import androidx.compose.material.icons.outlined.GeneratingTokens
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.abobi.video.downloader.R
import com.abobi.video.downloader.database.objects.CookieProfile
import com.abobi.video.downloader.ui.common.HapticFeedback.slightHapticFeedback
import com.abobi.video.downloader.ui.common.booleanState
import com.abobi.video.downloader.ui.component.BackButton
import com.abobi.video.downloader.ui.component.ConfirmButton
import com.abobi.video.downloader.ui.component.DismissButton
import com.abobi.video.downloader.ui.component.HelpDialog
import com.abobi.video.downloader.ui.component.HorizontalDivider
import com.abobi.video.downloader.ui.component.LargeTopAppBar
import com.abobi.video.downloader.ui.component.PasteFromClipBoardButton
import com.abobi.video.downloader.ui.component.PreferenceItemVariant
import com.abobi.video.downloader.ui.component.SealDialog
import com.abobi.video.downloader.ui.component.TextButtonWithIcon
import com.abobi.video.downloader.ui.theme.SealTheme
import com.abobi.video.downloader.ui.theme.generateLabelColor
import com.abobi.video.downloader.util.CookieHelper
import com.abobi.video.downloader.util.DownloadUtil
import com.abobi.video.downloader.util.DownloadUtil.toCookiesFileContent
import com.abobi.video.downloader.util.FileUtil.getCookiesFile
import com.abobi.video.downloader.util.USER_AGENT
import com.abobi.video.downloader.util.PreferenceUtil.updateBoolean
import com.abobi.video.downloader.util.matchUrlFromClipboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookieProfilePage(
    cookiesViewModel: CookiesViewModel = viewModel(),
    navigateToCookieGeneratorPage: () -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        rememberTopAppBarState(),
        canScroll = { true })
    val cookies = cookiesViewModel.cookiesFlow.collectAsState(emptyList()).value
    val scope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val state by cookiesViewModel.stateFlow.collectAsStateWithLifecycle()
    var showClearCookieDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    val view = LocalView.current
    val cookiesDir = remember { CookieHelper.getCookiesDirectory().absolutePath }
    var masterCookieStats by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var siteStatsRevision by remember { mutableStateOf(0) }

    var cookieList by remember {
        mutableStateOf(listOf<Cookie>())
    }

    var shouldUpdateCookies by remember {
        mutableStateOf(false)
    }

    var exportSiteUrl by remember { mutableStateOf<String?>(null) }
    var profileActionTarget by remember { mutableStateOf<CookieProfile?>(null) }

    fun refreshCookieStats() {
        scope.launch(Dispatchers.IO) {
            DownloadUtil.getCookieListFromDatabase().getOrNull()?.let { cookieList = it }
            CookieHelper.refreshAllCookiesFiles().onSuccess { result ->
                masterCookieStats = result.cookieCount to result.masterFilePath
            }
            siteStatsRevision++
        }
    }

    DisposableEffect(shouldUpdateCookies) {
        if (shouldUpdateCookies) {
            refreshCookieStats()
        }
        onDispose {
            shouldUpdateCookies = false
        }
    }

    LaunchedEffect(Unit) {
        refreshCookieStats()
    }


    val exportLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            uri?.let {
                scope.launch(Dispatchers.IO) {
                    val content = exportSiteUrl?.let { profileUrl ->
                        CookieHelper.getSiteCookieFile(profileUrl)?.readText()
                    } ?: cookieList.toCookiesFileContent()
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(content.toByteArray())
                    }
                }
            }
            exportSiteUrl = null
        }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(title = {
                Text(
                    modifier = Modifier,
                    text = stringResource(id = R.string.cookies),
                )
            }, navigationIcon = {
                BackButton {
                    onNavigateBack()
                }
            }, actions = {
                var expanded by remember { mutableStateOf(false) }
                IconButton(onClick = { showHelpDialog = true }) {
                    Icon(
                        imageVector = Icons.Outlined.HelpOutline,
                        contentDescription = stringResource(R.string.how_does_it_work)
                    )
                }
                IconButton(onClick = { expanded = true }) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        contentDescription = stringResource(
                            R.string.show_more_actions
                        )
                    )
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    var userAgent by USER_AGENT.booleanState
                    fun toggleUserAgent(boolean: Boolean = !userAgent) {
                        expanded = false
                        userAgent = boolean
                        USER_AGENT.updateBoolean(boolean)
                    }
                    DropdownMenuItem(
                        modifier = Modifier.toggleable(
                            value = userAgent,
                            onValueChange = ::toggleUserAgent
                        ),
                        leadingIcon = {
                            Checkbox(
                                checked = userAgent,
                                onCheckedChange = null,
                                modifier = Modifier.clearAndSetSemantics { })
                        },
                        text = { Text(stringResource(id = R.string.ua_header)) },
                        onClick = ::toggleUserAgent,
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Outlined.FileCopy, null) },
                        text = {
                            Text(stringResource(id = R.string.export_to_file))
                        },
                        enabled = cookieList.isNotEmpty(),
                        onClick = {
                            expanded = false
                            exportLauncher.launch("cookies_exported${System.currentTimeMillis()}.txt")
                        })
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Outlined.DeleteForever, null) },
                        text = {
                            Text(stringResource(id = R.string.clear_all_cookies))
                        },
                        onClick = {
                            expanded = false
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            showClearCookieDialog = true
                        })

                }
            }, scrollBehavior = scrollBehavior)
        },
    )
    { paddingValues ->
        LazyColumn(modifier = Modifier.padding(paddingValues)) {
            item {
                Text(
                    text = stringResource(R.string.cookies_storage_path, cookiesDir),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (CookieHelper.hasCookiesForDomain("tiktok")) {
                    Text(
                        text = stringResource(R.string.tiktok_cookies_active),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            itemsIndexed(cookies, key = { _, item -> "${item.id}-$siteStatsRevision" }) { _, item ->
                val stats = remember(item.url, siteStatsRevision) {
                    CookieHelper.getSiteCookieStats(item.url)
                }
                PreferenceItemVariant(
                    modifier = Modifier.padding(vertical = 4.dp),
                    title = item.url,
                    description = stats?.let {
                        stringResource(R.string.cookies_profile_stats, it.first, it.second)
                    },
                    onClick = { cookiesViewModel.showEditCookieDialog(item) },
                    onClickLabel = stringResource(
                        id = R.string.edit
                    ), onLongClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        profileActionTarget = item
                    }, onLongClickLabel = stringResource(R.string.show_more_actions)
                )
            }

            item {
                PreferenceItemVariant(
                    title = stringResource(id = R.string.login_tiktok_cookies),
                    icon = Icons.Outlined.Cookie,
                ) {
                    cookiesViewModel.openQuickLogin(CookieHelper.TIKTOK_LOGIN_URL)
                    navigateToCookieGeneratorPage()
                }
            }
            item {
                PreferenceItemVariant(
                    title = stringResource(id = R.string.login_instagram_cookies),
                    icon = Icons.Outlined.Cookie,
                ) {
                    cookiesViewModel.openQuickLogin(CookieHelper.INSTAGRAM_LOGIN_URL)
                    navigateToCookieGeneratorPage()
                }
            }
            item {
                PreferenceItemVariant(
                    title = stringResource(id = R.string.generate_new_cookies),
                    icon = Icons.Outlined.Add
                ) { cookiesViewModel.showEditCookieDialog() }
            }
            item {
                HorizontalDivider()
                val cookiesCount = masterCookieStats?.first ?: cookieList.size
                val siteCount = cookieList.distinctBy { it.domain }.size
                val masterPath = masterCookieStats?.second ?: context.getCookiesFile().absolutePath
                Text(
                    text = stringResource(R.string.cookies_in_database, cookiesCount, siteCount),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = masterPath,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

    }
    if (state.showEditDialog) {
        CookieGeneratorDialog(
            cookiesViewModel = cookiesViewModel,
            navigateToCookieGeneratorPage = navigateToCookieGeneratorPage
        ) {
            cookiesViewModel.hideDialog()
            shouldUpdateCookies = true
        }
    }

    if (state.showDeleteDialog) {
        DeleteCookieDialog(cookiesViewModel) { cookiesViewModel.hideDialog() }
    }

    if (showHelpDialog) {
        HelpDialog(text = stringResource(id = R.string.cookies_usage_msg), onDismissRequest = {
            showHelpDialog = false
        })
    }
    if (showClearCookieDialog) {
        ClearCookiesDialog(onDismissRequest = { showClearCookieDialog = false }) {
            view.slightHapticFeedback()
            scope.launch(Dispatchers.IO) {
                CookieManager.getInstance().removeAllCookies(null)
            }.invokeOnCompletion {
                shouldUpdateCookies = true
            }
        }
    }
    profileActionTarget?.let { profile ->
        val profileStats = CookieHelper.getSiteCookieStats(profile.url)
        AlertDialog(
            onDismissRequest = { profileActionTarget = null },
            title = { Text(CookieHelper.siteLabelFromUrl(profile.url)) },
            text = {
                profileStats?.let {
                    Text(stringResource(R.string.cookies_profile_stats, it.first, it.second))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    exportSiteUrl = profile.url
                    val siteLabel = CookieHelper.siteLabelFromUrl(profile.url)
                    exportLauncher.launch("${siteLabel}_cookies.txt")
                    profileActionTarget = null
                }) {
                    Text(stringResource(R.string.export_site_cookies))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        cookiesViewModel.showDeleteCookieDialog(profile)
                        profileActionTarget = null
                    }) {
                        Text(stringResource(R.string.remove))
                    }
                    TextButton(onClick = { profileActionTarget = null }) {
                        Text(stringResource(androidx.appcompat.R.string.abc_action_mode_done))
                    }
                }
            },
        )
    }
}

@OptIn(
    ExperimentalMaterial3Api::class
)
@Composable
fun CookieGeneratorDialog(
    cookiesViewModel: CookiesViewModel = viewModel(),
    navigateToCookieGeneratorPage: () -> Unit = {},
    onDismissRequest: () -> Unit
) {

    val state by cookiesViewModel.stateFlow.collectAsStateWithLifecycle()
    val profile = state.editingCookieProfile
    val url = profile.url

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            CookieManager.getInstance().flush()
        }
    }
    AlertDialog(onDismissRequest = onDismissRequest, icon = {
        Icon(Icons.Outlined.Cookie, null)
    }, title = { Text(stringResource(R.string.cookies)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                value = url, label = { Text("URL") },
                onValueChange = { cookiesViewModel.updateUrl(it) }, trailingIcon = {
                    PasteFromClipBoardButton {
                        cookiesViewModel.updateUrl(
                            matchUrlFromClipboard(it)
                        )
                    }
                }, maxLines = 1,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )

            TextButtonWithIcon(
                onClick = { navigateToCookieGeneratorPage() },
                icon = Icons.Outlined.GeneratingTokens,
                text = stringResource(id = R.string.generate_new_cookies)
            )

        }
    }, dismissButton = {
        DismissButton {
            onDismissRequest()
        }
    }, confirmButton = {
        ConfirmButton(enabled = url.isNotEmpty()) {
            cookiesViewModel.updateCookieProfile()
            onDismissRequest()
        }
    })

}

@Composable
fun DeleteCookieDialog(
    cookiesViewModel: CookiesViewModel = viewModel(),
    onDismissRequest: () -> Unit = {}
) {
    val state by cookiesViewModel.stateFlow.collectAsState()
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.remove)) },
        text = {
            Text(
                stringResource(R.string.remove_cookie_profile_desc).format(state.editingCookieProfile.url),
                style = LocalTextStyle.current.copy(lineBreak = LineBreak.Paragraph)
            )
        },
        dismissButton = {
            DismissButton {
                onDismissRequest()
            }
        }, confirmButton = {
            ConfirmButton {
                cookiesViewModel.deleteCookieProfile()
                onDismissRequest()
            }
        }, icon = { Icon(Icons.Outlined.Delete, null) })
}

@Composable
fun ClearCookiesDialog(
    onDismissRequest: () -> Unit = {}, onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.clear_all_cookies)) },
        text = {
            Text(
                stringResource(R.string.clear_all_cookies_desc),
                style = MaterialTheme.typography.bodyLarge
            )
        },
        dismissButton = {
            DismissButton {
                onDismissRequest()
            }
        }, confirmButton = {
            ConfirmButton {
                onConfirm()
                onDismissRequest()
            }
        }, icon = { Icon(Icons.Outlined.DeleteForever, null) })
}

@Composable
fun CookiesQuickSettingsDialog(
    onDismissRequest: () -> Unit = {},
    onConfirm: () -> Unit = {},
    cookieProfiles: List<CookieProfile> = emptyList(),
    onCookieProfileClicked: (CookieProfile) -> Unit = {},
) {
    SealDialog(onDismissRequest = onDismissRequest, confirmButton = {
        ConfirmButton(text = stringResource(id = androidx.appcompat.R.string.abc_action_mode_done)) {
            onDismissRequest()
            onConfirm()
        }
    }, icon = { Icon(imageVector = Icons.Outlined.Cookie, contentDescription = null) },
        title = {
            Text(
                text = stringResource(id = R.string.cookies),
                textAlign = TextAlign.Center
            )
        }, text = {
            Column {
                Text(
                    text = stringResource(id = R.string.refresh_cookies_desc),
                    modifier = Modifier.padding(horizontal = 24.dp),
//                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(Modifier.padding(horizontal = 24.dp))
                Spacer(modifier = Modifier.height(4.dp))
                LazyColumn() {
                    items(items = cookieProfiles) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onCookieProfileClicked(it) }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .size(16.dp)
                                    .background(
                                        color = it.url
                                            .hashCode()
                                            .generateLabelColor(), shape = CircleShape
                                    )
                                    .clearAndSetSemantics { }
                            ) {}
                            Text(
                                text = it.url
//                                , style = MaterialTheme.typography.labelLarge
                                , modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        })
}

@Preview
@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CookiesQuickSettingsDialogPreview() {
    SealTheme {
        CookiesQuickSettingsDialog(
            cookieProfiles = mutableListOf<CookieProfile>().apply {
                repeat(4) {
                    add(
                        CookieProfile(
                            id = it,
                            url = "https://www.example$it.com",
                            content = ""
                        )
                    )
                }
            },
        )
    }
}