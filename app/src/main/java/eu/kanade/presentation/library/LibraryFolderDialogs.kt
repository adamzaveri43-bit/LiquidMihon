package eu.kanade.presentation.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.tachiyomi.ui.library.LibraryItem
import eu.kanade.tachiyomi.ui.library.folder.FolderCover
import eu.kanade.tachiyomi.ui.library.folder.LibraryEntry
import eu.kanade.tachiyomi.ui.library.folder.LibraryFolder
import eu.kanade.tachiyomi.ui.library.folder.coverData
import kotlinx.coroutines.delay
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Add
import mihon.icons.materialsymbols.rounded.Delete
import mihon.icons.materialsymbols.rounded.DragHandle
import mihon.icons.materialsymbols.rounded.Edit
import mihon.icons.materialsymbols.rounded.Folder
import mihon.icons.materialsymbols.rounded.Image
import mihon.icons.materialsymbols.rounded.Photo
import mihon.icons.materialsymbols.rounded.SortByAlpha
import mihon.icons.materialsymbols.roundedfilled.Folder
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Icon
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.plus

@Composable
fun AddToFolderDialog(
    folders: List<LibraryFolder>,
    canRemove: Boolean,
    onDismissRequest: () -> Unit,
    onCreate: (String) -> Unit,
    onAdd: (LibraryFolder) -> Unit,
    onRemove: () -> Unit,
) {
    var creating by remember { mutableStateOf(folders.isEmpty()) }
    if (creating) {
        FolderNameDialog(
            title = stringResource(MR.strings.library_folder_new),
            initialName = "",
            confirmText = stringResource(MR.strings.action_create),
            onDismissRequest = onDismissRequest,
            onConfirm = {
                onCreate(it)
                onDismissRequest()
            },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(MR.strings.library_folder_add_to)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                item {
                    DialogRow(
                        icon = { Icon(MaterialSymbols.Rounded.Add, contentDescription = null) },
                        text = stringResource(MR.strings.library_folder_new),
                        onClick = { creating = true },
                    )
                }
                items(folders, key = { it.id }) { folder ->
                    DialogRow(
                        icon = { Icon(MaterialSymbols.RoundedFilled.Folder, contentDescription = null) },
                        text = folder.name,
                        onClick = {
                            onAdd(folder)
                            onDismissRequest()
                        },
                    )
                }
                if (canRemove) {
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        DialogRow(
                            icon = { Icon(MaterialSymbols.Rounded.Delete, contentDescription = null) },
                            text = stringResource(MR.strings.library_folder_remove_from),
                            onClick = {
                                onRemove()
                                onDismissRequest()
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(MR.strings.action_cancel)) }
        },
    )
}

@Composable
fun FolderOptionsDialog(
    folder: LibraryFolder,
    onDismissRequest: () -> Unit,
    onRename: () -> Unit,
    onChangeCover: () -> Unit,
    onReorder: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(folder.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                DialogRow(
                    icon = { Icon(MaterialSymbols.Rounded.Edit, contentDescription = null) },
                    text = stringResource(MR.strings.library_folder_rename),
                    onClick = onRename,
                )
                DialogRow(
                    icon = { Icon(MaterialSymbols.Rounded.Image, contentDescription = null) },
                    text = stringResource(MR.strings.library_folder_change_cover),
                    onClick = onChangeCover,
                )
                DialogRow(
                    icon = { Icon(MaterialSymbols.Rounded.SortByAlpha, contentDescription = null) },
                    text = stringResource(MR.strings.action_reorder),
                    onClick = onReorder,
                )
                DialogRow(
                    icon = { Icon(MaterialSymbols.Rounded.Delete, contentDescription = null) },
                    text = stringResource(MR.strings.library_folder_delete),
                    onClick = onDelete,
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(MR.strings.action_cancel)) }
        },
    )
}

@Composable
fun FolderNameDialog(
    title: String,
    initialName: String,
    confirmText: String,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    val focusRequester = remember { FocusRequester() }
    val valid = name.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(MR.strings.library_folder_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (valid) onConfirm(name) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(name) }) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(MR.strings.action_cancel)) }
        },
    )
    LaunchedEffect(focusRequester) {
        // The text field is only attached once the dialog's window is shown
        delay(100)
        runCatching { focusRequester.requestFocus() }
    }
}

@Composable
fun FolderCoverDialog(
    folder: LibraryFolder,
    items: List<LibraryItem>,
    onDismissRequest: () -> Unit,
    onSelect: (FolderCover) -> Unit,
    onPickImage: () -> Unit,
) {
    val selectedMangaId = (folder.cover as? FolderCover.Manga)?.mangaId
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(MR.strings.library_folder_change_cover)) },
        text = {
            Column {
                DialogRow(
                    icon = { Icon(MaterialSymbols.Rounded.Folder, contentDescription = null) },
                    text = stringResource(MR.strings.library_folder_cover_auto),
                    selected = folder.cover == FolderCover.Auto,
                    onClick = {
                        onSelect(FolderCover.Auto)
                        onDismissRequest()
                    },
                )
                DialogRow(
                    icon = { Icon(MaterialSymbols.Rounded.Photo, contentDescription = null) },
                    text = stringResource(MR.strings.library_folder_cover_image),
                    selected = folder.cover is FolderCover.Image,
                    onClick = {
                        onPickImage()
                        onDismissRequest()
                    },
                )
                if (items.isNotEmpty()) {
                    Text(
                        text = stringResource(MR.strings.library_folder_cover_entry),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(items, key = { it.id }) { item ->
                            val selected = item.id == selectedMangaId
                            MangaCover.Book(
                                data = item.coverData(),
                                modifier = Modifier
                                    .width(72.dp)
                                    .then(
                                        if (selected) {
                                            Modifier.border(
                                                width = 3.dp,
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = MaterialTheme.shapes.extraSmall,
                                            )
                                        } else {
                                            Modifier
                                        },
                                    ),
                                onClick = {
                                    onSelect(FolderCover.Manga(item.id))
                                    onDismissRequest()
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(MR.strings.action_cancel)) }
        },
    )
}

@Composable
fun DeleteFolderDialog(
    folder: LibraryFolder,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(MR.strings.library_folder_delete)) },
        text = { Text(stringResource(MR.strings.library_folder_delete_confirm, folder.name)) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismissRequest()
            }) {
                Text(stringResource(MR.strings.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(MR.strings.action_cancel)) }
        },
    )
}

/**
 * Full screen list to arrange [entries] by dragging them.
 */
@Composable
fun LibraryReorderDialog(
    title: String,
    hint: String,
    entries: List<LibraryEntry>,
    onDismissRequest: () -> Unit,
    onSave: (List<String>) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val ordered = remember(entries) { entries.toMutableStateList() }
        val lazyListState = rememberLazyListState()
        Scaffold(
            topBar = {
                AppBar(
                    title = title,
                    navigateUp = onDismissRequest,
                    actions = {
                        TextButton(onClick = {
                            onSave(ordered.map { it.key })
                            onDismissRequest()
                        }) {
                            Text(stringResource(MR.strings.action_save))
                        }
                    },
                    scrollBehavior = it,
                )
            },
        ) { paddingValues ->
            val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
                ordered.add(to.index, ordered.removeAt(from.index))
            }
            Column(modifier = Modifier.padding(top = paddingValues.calculateTopPadding())) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                )
                ReorderList(
                    ordered = ordered,
                    lazyListState = lazyListState,
                    reorderableState = reorderableState,
                    bottomPadding = paddingValues.calculateBottomPadding(),
                )
            }
        }
    }
}

@Composable
private fun ReorderList(
    ordered: List<LibraryEntry>,
    lazyListState: LazyListState,
    reorderableState: ReorderableLazyListState,
    bottomPadding: Dp,
) {
    LazyColumn(
        state = lazyListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(MaterialTheme.padding.medium) + PaddingValues(bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        items(ordered, key = { it.key }) { entry ->
            ReorderableItem(reorderableState, key = entry.key) {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .longPressDraggableHandle(),
                ) {
                    Row(
                        modifier = Modifier.padding(MaterialTheme.padding.small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = MaterialSymbols.Rounded.DragHandle,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(horizontal = MaterialTheme.padding.small)
                                .draggableHandle(),
                        )
                        MangaCover.Book(
                            data = when (entry) {
                                is LibraryEntry.Folder -> entry.coverData
                                is LibraryEntry.Manga -> entry.item.coverData()
                            },
                            modifier = Modifier.height(56.dp),
                        )
                        Spacer(modifier = Modifier.width(MaterialTheme.padding.medium))
                        if (entry is LibraryEntry.Folder) {
                            Icon(
                                imageVector = MaterialSymbols.RoundedFilled.Folder,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(end = MaterialTheme.padding.small)
                                    .size(18.dp),
                            )
                        }
                        Text(
                            text = when (entry) {
                                is LibraryEntry.Folder -> entry.folder.name
                                is LibraryEntry.Manga -> entry.item.libraryManga.manga.title
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogRow(
    icon: @Composable () -> Unit,
    text: String,
    onClick: () -> Unit,
    selected: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        icon()
        Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
    }
}
