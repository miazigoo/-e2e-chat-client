package com.example.securechatapp.ui.screens.conversation

import android.graphics.Color.parseColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.securechatapp.domain.model.MediaTag

private val SuggestedTagPresets = listOf(
    "Чеки" to "#16A34A",
    "Штрихкоды" to "#2563EB",
    "Фото" to "#D97706",
    "Документы" to "#6D28D9",
    "Работа" to "#DC2626",
)

private val TagColorPalette = listOf(
    "#16A34A",
    "#2563EB",
    "#D97706",
    "#6D28D9",
    "#DC2626",
    "#0F766E",
    "#7C3AED",
    "#475569",
)

@Composable
fun MediaTagChip(
    tag: MediaTag,
    selected: Boolean,
    onClick: (() -> Unit)? = null,
) {
    val accent = tagColorOrDefault(tag.color)
    FilterChip(
        selected = selected,
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        label = {
            Text(tag.name)
        },
        leadingIcon = {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(accent, CircleShape),
            )
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AttachmentTagPickerDialog(
    title: String,
    tags: List<MediaTag>,
    isLoading: Boolean,
    selectedTagIds: Set<Int>,
    onDismiss: () -> Unit,
    onApply: (List<Int>) -> Unit,
    onOpenManageTags: () -> Unit,
) {
    var localSelection by remember(selectedTagIds) { mutableStateOf(selectedTagIds) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = when {
                        isLoading && tags.isEmpty() -> {
                            "Загружаю теги этого чата..."
                        }
                        tags.isEmpty() -> {
                        "В этом чате ещё нет тегов. Создайте их в управлении тегами."
                        }
                        else -> {
                        "Выберите один или несколько тегов для вложений"
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (tags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        tags.forEach { tag ->
                            MediaTagChip(
                                tag = tag,
                                selected = localSelection.contains(tag.tagId),
                                onClick = {
                                    localSelection = if (localSelection.contains(tag.tagId)) {
                                        localSelection - tag.tagId
                                    } else {
                                        localSelection + tag.tagId
                                    }
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onApply(localSelection.toList().sorted())
                },
            ) {
                Text("Применить")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onOpenManageTags) {
                    Text("Управление")
                }
                TextButton(onClick = onDismiss) {
                    Text("Отмена")
                }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConversationMediaTagManagerDialog(
    tags: List<MediaTag>,
    onDismiss: () -> Unit,
    onCreateTag: (name: String, color: String?) -> Unit,
    onUpdateTag: (tagId: Int, name: String, color: String?) -> Unit,
    onDeleteTag: (tagId: Int) -> Unit,
) {
    var draftName by remember { mutableStateOf("") }
    var draftColor by remember { mutableStateOf(TagColorPalette.first()) }
    var editingTag by remember { mutableStateOf<MediaTag?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Теги медиа") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (tags.isEmpty()) {
                    Text(
                        text = "Создайте теги для этого чата. Они помогут быстро фильтровать вложения.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SuggestedTagPresets.forEach { (name, color) ->
                            FilterChip(
                                selected = false,
                                onClick = { onCreateTag(name, color) },
                                label = { Text(name) },
                            )
                        }
                    }
                }

                tags.forEach { tag ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(tagColorOrDefault(tag.color), CircleShape),
                            )

                            Text(
                                text = tag.name,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 10.dp),
                                color = MaterialTheme.colorScheme.onSurface,
                            )

                            TextButton(
                                onClick = { editingTag = tag },
                            ) {
                                Text("Изменить")
                            }

                            TextButton(
                                onClick = { onDeleteTag(tag.tagId) },
                            ) {
                                Text("Удалить")
                            }
                        }
                    }
                }

                Text(
                    text = if (editingTag == null) "Новый тег" else "Редактирование тега",
                    style = MaterialTheme.typography.titleSmall,
                )

                OutlinedTextField(
                    value = editingTag?.name ?: draftName,
                    onValueChange = {
                        if (editingTag == null) {
                            draftName = it
                        } else {
                            editingTag = editingTag?.copy(name = it)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Название") },
                )

                ColorPaletteRow(
                    selectedColor = editingTag?.color ?: draftColor,
                    onSelect = { color ->
                        if (editingTag == null) {
                            draftColor = color
                        } else {
                            editingTag = editingTag?.copy(color = color)
                        }
                    },
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        onClick = {
                            if (editingTag == null) {
                                val normalized = draftName.trim()
                                if (normalized.isNotEmpty()) {
                                    onCreateTag(normalized, draftColor)
                                    draftName = ""
                                }
                            } else {
                                val tag = editingTag ?: return@TextButton
                                val normalized = tag.name.trim()
                                if (normalized.isNotEmpty()) {
                                    onUpdateTag(tag.tagId, normalized, tag.color)
                                    editingTag = null
                                }
                            }
                        },
                    ) {
                        Text(if (editingTag == null) "Создать" else "Сохранить")
                    }

                    if (editingTag != null) {
                        TextButton(
                            onClick = { editingTag = null },
                        ) {
                            Text("Отмена")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPaletteRow(
    selectedColor: String?,
    onSelect: (String) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TagColorPalette.forEach { hex ->
            val selected = selectedColor == hex
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(tagColorOrDefault(hex), CircleShape)
                    .border(
                        width = if (selected) 2.dp else 0.dp,
                        color = MaterialTheme.colorScheme.onSurface,
                        shape = CircleShape,
                    )
                    .clickable { onSelect(hex) },
            )
        }
    }
}

fun tagColorOrDefault(raw: String?): Color {
    return raw?.let {
        runCatching { Color(parseColor(it)) }.getOrNull()
    } ?: Color(0xFF3B82F6)
}
