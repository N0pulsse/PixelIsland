package com.example.pixelisland

import com.example.pixelisland.model.IslandEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** In-process bus between the notification listener, the demo buttons and the overlay UI. */
object IslandRepository {
    private val _events = MutableStateFlow<List<IslandEvent>>(emptyList())

    /** Sorted by priority (desc); within equal priority the newest event comes first. */
    val events: StateFlow<List<IslandEvent>> = _events.asStateFlow()

    private val _collapse = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val collapseRequests: SharedFlow<Unit> = _collapse.asSharedFlow()

    fun post(event: IslandEvent) = _events.update { list ->
        (listOf(event) + list.filterNot { it.key == event.key }).sortedByDescending { it.priority }
    }

    fun remove(key: String) = _events.update { list -> list.filterNot { it.key == key } }

    fun removeWhere(predicate: (IslandEvent) -> Boolean) =
        _events.update { list -> list.filterNot(predicate) }

    fun requestCollapse() {
        _collapse.tryEmit(Unit)
    }
}
