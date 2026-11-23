package eu.kanade.presentation.browse.components

import android.util.DisplayMetrics
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import eu.kanade.presentation.util.rememberResourceBitmapPainter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.coil.InstalledExtensionIcon
import eu.kanade.tachiyomi.data.coil.InstalledSourceIcon
import eu.kanade.tachiyomi.extension.model.Extension
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Architecture
import mihon.icons.materialsymbols.rounded.Chair
import mihon.icons.materialsymbols.rounded.Checkroom
import mihon.icons.materialsymbols.rounded.DirectionsCar
import mihon.icons.materialsymbols.rounded.Flight
import mihon.icons.materialsymbols.rounded.LibraryAdd
import mihon.icons.materialsymbols.rounded.LocalLibrary
import mihon.icons.materialsymbols.rounded.Memory
import mihon.icons.materialsymbols.rounded.Newspaper
import mihon.icons.materialsymbols.rounded.Palette
import mihon.icons.materialsymbols.roundedfilled.Dangerous
import mihon.icons.materialsymbols.roundedfilled.Warning
import mihon.source.archive.BuiltinSourceIcon
import mihon.source.archive.BuiltinSources
import tachiyomi.domain.source.model.Source
import tachiyomi.source.local.isLocal

private val defaultModifier = Modifier
    .height(40.dp)
    .aspectRatio(1f)

@Composable
fun SourceIcon(
    source: Source,
    modifier: Modifier = Modifier,
) {
    when {
        source.isStub -> {
            Image(
                imageVector = MaterialSymbols.RoundedFilled.Warning,
                contentDescription = null,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.error),
                modifier = modifier.then(defaultModifier),
            )
        }
        BuiltinSources.iconOf(source.id) != null -> {
            BuiltinIcon(BuiltinSources.iconOf(source.id)!!, modifier.then(defaultModifier))
        }
        source.isLocal() -> {
            Image(
                painter = painterResource(R.mipmap.ic_local_source),
                contentDescription = null,
                modifier = modifier.then(defaultModifier),
            )
        }
        else -> {
            val defaultIcon = painterResource(R.mipmap.ic_default_source)
            AsyncImage(
                model = InstalledSourceIcon(source.id),
                contentDescription = null,
                placeholder = defaultIcon,
                error = defaultIcon,
                modifier = modifier.then(defaultModifier),
            )
        }
    }
}

@Composable
fun ExtensionIcon(
    extension: Extension,
    modifier: Modifier = Modifier,
    density: Int = DisplayMetrics.DENSITY_DEFAULT,
) {
    when (extension) {
        is Extension.Available -> {
            AsyncImage(
                model = extension.iconUrl,
                contentDescription = null,
                placeholder = ColorPainter(Color(0x1F888888)),
                error = rememberResourceBitmapPainter(id = R.drawable.cover_error),
                modifier = modifier
                    .clip(MaterialTheme.shapes.extraSmall),
            )
        }
        is Extension.Loaded -> {
            val defaultIcon = painterResource(R.mipmap.ic_default_source)
            AsyncImage(
                model = InstalledExtensionIcon(extension.pkgName, extension.versionCode, density),
                contentDescription = null,
                placeholder = defaultIcon,
                error = defaultIcon,
                modifier = modifier,
            )
        }
        is Extension.NotLoaded -> Image(
            imageVector = MaterialSymbols.RoundedFilled.Dangerous,
            contentDescription = null,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.error),
            modifier = modifier.then(defaultModifier),
        )
    }
}

/** A built-in source's tile: its own symbol on a colored rounded square. */
@Composable
private fun BuiltinIcon(icon: BuiltinSourceIcon, modifier: Modifier) {
    val (vector, color) = when (icon) {
        BuiltinSourceIcon.STYLE -> MaterialSymbols.Rounded.Checkroom to Color(0xFF3A3A44)
        BuiltinSourceIcon.CARS -> MaterialSymbols.Rounded.DirectionsCar to Color(0xFFC62828)
        BuiltinSourceIcon.DESIGN -> MaterialSymbols.Rounded.Architecture to Color(0xFF00897B)
        BuiltinSourceIcon.ART -> MaterialSymbols.Rounded.Palette to Color(0xFF8E24AA)
        BuiltinSourceIcon.TECH -> MaterialSymbols.Rounded.Memory to Color(0xFF1565C0)
        BuiltinSourceIcon.TRAVEL -> MaterialSymbols.Rounded.Flight to Color(0xFF0288D1)
        BuiltinSourceIcon.INTERIORS -> MaterialSymbols.Rounded.Chair to Color(0xFF8D6E63)
        BuiltinSourceIcon.CUSTOM -> MaterialSymbols.Rounded.LibraryAdd to Color(0xFF546E7A)
        BuiltinSourceIcon.ARCHIVE_MAGAZINES -> MaterialSymbols.Rounded.Newspaper to Color(0xFF5D4037)
        BuiltinSourceIcon.ARCHIVE_BOOKS -> MaterialSymbols.Rounded.LocalLibrary to Color(0xFF6D4C41)
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            imageVector = vector,
            contentDescription = null,
            colorFilter = ColorFilter.tint(Color.White),
            modifier = Modifier.fillMaxSize(0.62f),
        )
    }
}
