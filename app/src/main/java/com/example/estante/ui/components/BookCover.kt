package com.example.estante.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File

/**
 * Capa de um livro: usa a imagem salva localmente, a URL remota
 * (catálogo do Gutenberg) ou um placeholder com o título.
 */
@Composable
fun BookCover(
    title: String,
    coverPath: String? = null,
    coverUrl: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coverFile = remember(coverPath) { coverPath?.let(::File) }

    val model = when {
        coverFile != null && coverFile.exists() -> coverFile
        coverUrl != null -> coverUrl
        else -> null
    }

    if (model != null) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(model)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier.background(
                Brush.verticalGradient(
                    listOf(Color(0xFF3E6B54), Color(0xFF2D6A4F))
                )
            ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = title,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }
        }
    }
}
