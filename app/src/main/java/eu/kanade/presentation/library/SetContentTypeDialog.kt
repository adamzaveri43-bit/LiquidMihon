package eu.kanade.presentation.library

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import eu.kanade.presentation.library.components.itemLabel
import tachiyomi.domain.library.model.LibraryContentType
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.RadioItem
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Lets the user file selected entries under Manga, Books or Magazines by hand.
 *
 * @param initial the type to preselect, or null to preselect "Automatic"
 * @param onConfirm called with the picked type, or null to go back to the automatic type
 */
@Composable
fun SetContentTypeDialog(
    initial: LibraryContentType?,
    onDismissRequest: () -> Unit,
    onConfirm: (LibraryContentType?) -> Unit,
) {
    var selection by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                    onConfirm(selection)
                },
            ) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        title = { Text(text = stringResource(MR.strings.content_type_dialog_title)) },
        text = {
            Column {
                RadioItem(
                    label = stringResource(MR.strings.content_type_automatic),
                    selected = selection == null,
                    onClick = { selection = null },
                )
                Text(
                    text = stringResource(MR.strings.content_type_automatic_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LibraryContentType.entries.forEach { type ->
                    RadioItem(
                        label = stringResource(type.itemLabel),
                        selected = selection == type,
                        onClick = { selection = type },
                    )
                }
            }
        },
    )
}
