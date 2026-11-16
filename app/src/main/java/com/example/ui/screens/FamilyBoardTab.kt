package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.data.model.FamilyEventEntity
import com.example.data.model.FamilyMemberEntity
import com.example.data.model.FamilyNoteEntity
import com.example.data.model.ShoppingItemEntity
import com.example.ui.theme.AvatarColors
import com.example.ui.theme.NoteColors
import com.example.util.parseHexColor

@Composable
fun FamilyBoardTab(
    activeMember: FamilyMemberEntity,
    members: List<FamilyMemberEntity>,
    shoppingItems: List<ShoppingItemEntity>,
    events: List<FamilyEventEntity>,
    notes: List<FamilyNoteEntity>,
    onAddShoppingItem: (name: String, quantity: String, category: String) -> Unit,
    onToggleShoppingItem: (ShoppingItemEntity) -> Unit,
    onDeleteShoppingItem: (String) -> Unit,
    onClearCheckedShopping: () -> Unit,
    onAddEvent: (title: String, date: String, time: String, desc: String, memberId: String, colorHex: String) -> Unit,
    onDeleteEvent: (String) -> Unit,
    onSaveNote: (id: String?, title: String, content: String, colorHex: String, isPinned: Boolean) -> Unit,
    onDeleteNote: (String) -> Unit
) {
    var boardSection by remember { mutableIntStateOf(0) }
    var showAddShopping by remember { mutableStateOf(false) }
    var showAddEvent by remember { mutableStateOf(false) }
    var showAddNote by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = boardSection == 0,
                onClick = { boardSection = 0 },
                label = { Text("Compras (${shoppingItems.count { !it.isChecked }})") },
                leadingIcon = { Icon(Icons.Filled.ShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("board_tab_shopping")
            )
            FilterChip(
                selected = boardSection == 1,
                onClick = { boardSection = 1 },
                label = { Text("Eventos (${events.size})") },
                leadingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("board_tab_events")
            )
            FilterChip(
                selected = boardSection == 2,
                onClick = { boardSection = 2 },
                label = { Text("Avisos (${notes.size})") },
                leadingIcon = { Icon(Icons.Filled.StickyNote2, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("board_tab_notes")
            )
        }

        when (boardSection) {
            0 -> ShoppingBoardSection(
                items = shoppingItems,
                onAddClick = { showAddShopping = true },
                onToggle = onToggleShoppingItem,
                onDelete = onDeleteShoppingItem,
                onClearChecked = onClearCheckedShopping
            )
            1 -> EventsBoardSection(
                events = events,
                members = members,
                onAddClick = { showAddEvent = true },
                onDelete = onDeleteEvent
            )
            2 -> NotesBoardSection(
                notes = notes,
                members = members,
                onAddClick = { showAddNote = true },
                onTogglePin = { note ->
                    onSaveNote(note.id, note.title, note.content, note.colorHex, !note.isPinned)
                },
                onDelete = onDeleteNote
            )
        }
    }

    if (showAddShopping) {
        var name by remember { mutableStateOf("") }
        var qty by remember { mutableStateOf("1 u.") }
        var cat by remember { mutableStateOf("Despensa") }
        AlertDialog(
            onDismissRequest = { showAddShopping = false },
            title = { Text("Agregar a la lista") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Producto") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("shopping_name_input")
                    )
                    OutlinedTextField(
                        value = qty,
                        onValueChange = { qty = it },
                        label = { Text("Cantidad") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = cat,
                        onValueChange = { cat = it },
                        label = { Text("Categoría") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddShoppingItem(name, qty, cat)
                        showAddShopping = false
                    },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.testTag("shopping_save_button")
                ) { Text("Agregar") }
            },
            dismissButton = {
                TextButton(onClick = { showAddShopping = false }) { Text("Cancelar") }
            }
        )
    }

    if (showAddEvent) {
        var title by remember { mutableStateOf("") }
        var date by remember { mutableStateOf("") }
        var time by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        var assignedId by remember { mutableStateOf(activeMember.id) }
        var colorHex by remember { mutableStateOf(AvatarColors.first()) }
        AlertDialog(
            onDismissRequest = { showAddEvent = false },
            title = { Text("Nuevo evento familiar") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Título del evento") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("event_title_input")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = date,
                            onValueChange = { date = it },
                            label = { Text("Fecha (ej. Vie 20)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = time,
                            onValueChange = { time = it },
                            label = { Text("Hora") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text("Lugar o detalle") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Responsable", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(members) { m ->
                            FilterChip(
                                selected = assignedId == m.id,
                                onClick = { assignedId = m.id; colorHex = m.avatarColorHex },
                                label = { Text("${m.avatarEmoji} ${m.name}") }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddEvent(title, date, time, desc, assignedId, colorHex)
                        showAddEvent = false
                    },
                    enabled = title.isNotBlank(),
                    modifier = Modifier.testTag("event_save_button")
                ) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { showAddEvent = false }) { Text("Cancelar") }
            }
        )
    }

    if (showAddNote) {
        var title by remember { mutableStateOf("") }
        var content by remember { mutableStateOf("") }
        var colorHex by remember { mutableStateOf(NoteColors.first()) }
        AlertDialog(
            onDismissRequest = { showAddNote = false },
            title = { Text("Nuevo aviso o nota") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Título") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("note_title_input")
                    )
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("Contenido del aviso") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth().testTag("note_content_input")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NoteColors.forEach { hex ->
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(parseHexColor(hex))
                                    .border(
                                        width = if (colorHex == hex) 2.5.dp else 1.dp,
                                        color = if (colorHex == hex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        shape = CircleShape
                                    )
                                    .clickable { colorHex = hex }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveNote(null, title, content, colorHex, true)
                        showAddNote = false
                    },
                    enabled = title.isNotBlank() && content.isNotBlank(),
                    modifier = Modifier.testTag("note_save_button")
                ) { Text("Publicar") }
            },
            dismissButton = {
                TextButton(onClick = { showAddNote = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun ShoppingBoardSection(
    items: List<ShoppingItemEntity>,
    onAddClick: () -> Unit,
    onToggle: (ShoppingItemEntity) -> Unit,
    onDelete: (String) -> Unit,
    onClearChecked: () -> Unit
) {
    val hasChecked = items.any { it.isChecked }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onAddClick,
                modifier = Modifier.weight(1f).testTag("add_shopping_button")
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Añadir producto")
            }
            if (hasChecked) {
                OutlinedButton(
                    onClick = onClearChecked,
                    modifier = Modifier.testTag("clear_checked_shopping_button")
                ) {
                    Icon(Icons.Filled.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Limpiar")
                }
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items, key = { it.id }) { item ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (item.isChecked) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(item) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = item.isChecked,
                            onCheckedChange = { onToggle(item) },
                            modifier = Modifier.testTag("shopping_check_${item.id}")
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None
                                ),
                                color = if (item.isChecked) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${item.quantity} · ${item.category}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onDelete(item.id) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Eliminar producto",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventsBoardSection(
    events: List<FamilyEventEntity>,
    members: List<FamilyMemberEntity>,
    onAddClick: () -> Unit,
    onDelete: (String) -> Unit
) {
    val membersMap = remember(members) { members.associateBy { it.id } }
    Column(modifier = Modifier.fillMaxSize()) {
        Button(
            onClick = onAddClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .testTag("add_event_button")
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Nuevo evento familiar")
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(events, key = { it.id }) { event ->
                val accent = parseHexColor(event.colorHex)
                val responsible = membersMap[event.assignedMemberId]
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(5.dp)
                                .height(54.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(accent)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(event.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${event.dateLabel} · ${event.timeLabel}",
                                style = MaterialTheme.typography.labelMedium,
                                color = accent
                            )
                            if (event.description.isNotBlank()) {
                                Text(
                                    event.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (responsible != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Responsable: ${responsible.avatarEmoji} ${responsible.name}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = { onDelete(event.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Eliminar evento")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotesBoardSection(
    notes: List<FamilyNoteEntity>,
    members: List<FamilyMemberEntity>,
    onAddClick: () -> Unit,
    onTogglePin: (FamilyNoteEntity) -> Unit,
    onDelete: (String) -> Unit
) {
    val membersMap = remember(members) { members.associateBy { it.id } }
    Column(modifier = Modifier.fillMaxSize()) {
        Button(
            onClick = onAddClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .testTag("add_note_button")
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Publicar aviso importante")
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(notes, key = { it.id }) { note ->
                val bg = parseHexColor(note.colorHex, Color(0xFFFFF4CC))
                val author = membersMap[note.authorId]
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = bg,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = note.title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF1A221E),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onTogglePin(note) }) {
                                Icon(
                                    Icons.Filled.PushPin,
                                    contentDescription = if (note.isPinned) "Desfijar" else "Fijar",
                                    tint = if (note.isPinned) Color(0xFF1A73E8) else Color(0xFF6B7280)
                                )
                            }
                            IconButton(onClick = { onDelete(note.id) }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Eliminar aviso",
                                    tint = Color(0xFF4B5563)
                                )
                            }
                        }
                        Text(
                            text = note.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF26302B)
                        )
                        if (author != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Por ${author.avatarEmoji} ${author.name}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF4B5563)
                            )
                        }
                    }
                }
            }
        }
    }
}
