package com.gym.tracker.ui.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gym.tracker.data.GymRepository
import com.gym.tracker.data.db.EntryWithDate
import com.gym.tracker.data.db.ExerciseEntity
import com.gym.tracker.data.db.Feel
import com.gym.tracker.data.db.SessionEntryEntity
import com.gym.tracker.data.db.SlotOptionExercise
import com.gym.tracker.data.db.TemplateSlotEntity
import com.gym.tracker.domain.Format
import com.gym.tracker.domain.prefillFor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.time.LocalDate

/** Несохранённый ввод карточки слота. */
data class CardDraft(
    val selectedExerciseId: String,
    val sets: String,
    val reps: String,
    val weight: String,
    val feel: Feel?,
    val editing: Boolean,
    val entryId: String?,
    val error: String? = null,
)

/** «В прошлый раз» для выбранного упражнения: null внутри = ещё не выполнялось. */
data class LastInfo(val entry: EntryWithDate?)

data class SlotCardUi(
    val slot: TemplateSlotEntity,
    val options: List<ExerciseEntity>,
    val draft: CardDraft?,
    val last: LastInfo?,
) {
    val selectedExercise: ExerciseEntity? get() = options.firstOrNull { it.id == draft?.selectedExerciseId }
}

data class WorkoutUiState(
    val loading: Boolean = true,
    val templateName: String = "",
    val date: LocalDate = LocalDate.now(),
    val cards: List<SlotCardUi> = emptyList(),
    val doneCount: Int = 0,
)

sealed interface WorkoutDialog {
    data object ConfirmExit : WorkoutDialog
    data object DiscardEmpty : WorkoutDialog
    data class AddOption(val slotId: String) : WorkoutDialog
}

sealed interface WorkoutNav {
    data class Summary(val sessionId: String) : WorkoutNav
    data object Home : WorkoutNav
}

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutViewModel(
    private val repo: GymRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    val sessionId: String = checkNotNull(savedState.get<String>(ARG_SESSION_ID))

    private val drafts = MutableStateFlow(restoreDrafts())
    private val lastCache = MutableStateFlow<Map<String, LastInfo>>(emptyMap())
    private val draftMutex = Mutex()

    val dialog = MutableStateFlow<WorkoutDialog?>(null)
    val nav = MutableStateFlow<WorkoutNav?>(null)

    private val session = repo.observeSession(sessionId).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val templateName = MutableStateFlow("")

    private val slots = session.filterNotNull().map { it.templateId }
        .flatMapLatest { repo.observeSlots(it) }
    private val options = session.filterNotNull().map { it.templateId }
        .flatMapLatest { repo.observeOptions(it) }
    private val entries = repo.observeEntriesForSession(sessionId)

    val state: StateFlow<WorkoutUiState> = combine(
        combine(slots, options, entries, ::Triple),
        drafts,
        lastCache,
        session,
        templateName,
    ) { (slotList, optionList, _), draftMap, cache, sess, name ->
        val bySlot = optionList.groupBy({ it.slotId }, { it.exercise })
        val cards = slotList.map { slot ->
            val draft = draftMap[slot.id]
            SlotCardUi(
                slot = slot,
                options = bySlot[slot.id].orEmpty(),
                draft = draft,
                last = draft?.let { cache[it.selectedExerciseId] },
            )
        }
        WorkoutUiState(
            loading = sess == null,
            templateName = name,
            date = sess?.date ?: LocalDate.now(),
            cards = cards,
            doneCount = draftMap.values.count { it.entryId != null },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutUiState())

    init {
        viewModelScope.launch {
            val s = session.filterNotNull().first()
            templateName.value = repo.getTemplate(s.templateId)?.name.orEmpty()
        }
        // Создаём черновики для новых слотов (избранное + предзаполнение).
        viewModelScope.launch {
            combine(slots, options, entries, ::Triple).collect { (slotList, optionList, entryList) ->
                ensureDrafts(slotList, optionList, entryList)
            }
        }
    }

    private suspend fun ensureDrafts(
        slotList: List<TemplateSlotEntity>,
        optionList: List<SlotOptionExercise>,
        entryList: List<SessionEntryEntity>,
    ) = draftMutex.withLock {
        val bySlot = optionList.groupBy({ it.slotId }, { it.exercise })
        val entryBySlot = entryList.filter { it.slotId != null }.associateBy { it.slotId }
        val current = drafts.value
        val updated = current.toMutableMap()
        for (slot in slotList) {
            val existing = current[slot.id]
            if (existing != null) {
                loadLast(existing.selectedExerciseId)
                continue
            }
            val entry = entryBySlot[slot.id]
            val draft = if (entry != null) {
                draftFromEntry(entry)
            } else {
                val opts = bySlot[slot.id].orEmpty()
                val exercise = opts.firstOrNull { it.id == slot.favoriteExerciseId } ?: opts.firstOrNull() ?: continue
                prefilledDraft(exercise, feel = null, entryId = null)
            }
            loadLast(draft.selectedExerciseId)
            updated[slot.id] = draft
        }
        if (updated != current) setDrafts(updated)
    }

    private fun draftFromEntry(entry: SessionEntryEntity) = CardDraft(
        selectedExerciseId = entry.exerciseId,
        sets = entry.sets.toString(),
        reps = entry.reps.toString(),
        weight = if (entry.weightKg == 0.0) "" else Format.number(entry.weightKg),
        feel = entry.feel,
        editing = false,
        entryId = entry.id,
    )

    private suspend fun prefilledDraft(exercise: ExerciseEntity, feel: Feel?, entryId: String?): CardDraft {
        val last = loadLast(exercise.id)
        val p = prefillFor(exercise, last.entry?.entry)
        return CardDraft(
            selectedExerciseId = exercise.id,
            sets = p.sets.toString(),
            reps = p.reps.toString(),
            weight = if (p.weightKg == 0.0) "" else Format.number(p.weightKg),
            feel = feel,
            editing = true,
            entryId = entryId,
        )
    }

    private suspend fun loadLast(exerciseId: String): LastInfo {
        lastCache.value[exerciseId]?.let { return it }
        val info = LastInfo(repo.lastEntry(exerciseId, excludeSessionId = sessionId))
        lastCache.update { it + (exerciseId to info) }
        return info
    }

    private fun setDrafts(map: Map<String, CardDraft>) {
        drafts.value = map
        savedState[KEY_DRAFTS] = encodeDrafts(map)
    }

    private fun updateDraft(slotId: String, block: (CardDraft) -> CardDraft) {
        val d = drafts.value[slotId] ?: return
        setDrafts(drafts.value + (slotId to block(d)))
    }

    // ---- Действия UI ----

    fun selectExercise(slotId: String, exerciseId: String) {
        viewModelScope.launch {
            draftMutex.withLock {
                val d = drafts.value[slotId] ?: return@withLock
                if (d.selectedExerciseId == exerciseId) return@withLock
                val exercise = options.first().firstOrNull { it.slotId == slotId && it.exercise.id == exerciseId }?.exercise
                    ?: return@withLock
                // Другое упражнение — заново предзаполняем подходы/повторы/вес; самочувствие сохраняем.
                setDrafts(drafts.value + (slotId to prefilledDraft(exercise, d.feel, d.entryId)))
            }
        }
    }

    /** Звёздочка: делает упражнение избранным в слоте (на будущие тренировки) и выбирает его сейчас. */
    fun setFavorite(slotId: String, exerciseId: String) {
        viewModelScope.launch {
            repo.setFavorite(slotId, exerciseId)
            if (drafts.value[slotId]?.editing == true) selectExercise(slotId, exerciseId)
        }
    }

    fun onSetsChange(slotId: String, v: String) = updateDraft(slotId) { it.copy(sets = v, error = null) }
    fun onRepsChange(slotId: String, v: String) = updateDraft(slotId) { it.copy(reps = v, error = null) }
    fun onWeightChange(slotId: String, v: String) = updateDraft(slotId) { it.copy(weight = v, error = null) }
    fun onFeel(slotId: String, feel: Feel) = updateDraft(slotId) { it.copy(feel = feel, error = null) }
    fun reopen(slotId: String) = updateDraft(slotId) { it.copy(editing = true) }

    /** Отмена правок выполненной карточки: вернуть сохранённые значения. */
    fun reopenCancel(slotId: String) {
        val entryId = drafts.value[slotId]?.entryId ?: return
        viewModelScope.launch {
            val e = repo.getEntry(entryId) ?: return@launch
            loadLast(e.exerciseId)
            updateDraft(slotId) { draftFromEntry(e) }
        }
    }

    fun done(slotId: String) {
        val d = drafts.value[slotId] ?: return
        val sets = d.sets.trim().toIntOrNull()
        val reps = d.reps.trim().toIntOrNull()
        val weight = Format.parseWeight(d.weight)
        val error = when {
            sets == null || sets < 1 || reps == null || reps < 1 -> "Укажите подходы и повторы (не меньше 1)"
            weight == null -> "Неверный вес"
            d.feel == null -> "Выберите самочувствие: Легко, Норм или Тяжело"
            else -> null
        }
        if (error != null) {
            updateDraft(slotId) { it.copy(error = error) }
            return
        }
        viewModelScope.launch {
            val entryId = repo.saveEntry(
                entryId = d.entryId,
                sessionId = sessionId,
                slotId = slotId,
                exerciseId = d.selectedExerciseId,
                sets = sets!!,
                reps = reps!!,
                weightKg = weight!!,
                feel = d.feel!!,
            )
            updateDraft(slotId) { it.copy(entryId = entryId, editing = false, error = null) }
        }
    }

    fun changeDate(date: LocalDate) {
        viewModelScope.launch { repo.updateSessionDate(sessionId, date) }
    }

    fun requestAddOption(slotId: String) {
        dialog.value = WorkoutDialog.AddOption(slotId)
    }

    fun addOption(slotId: String, name: String, sets: Int, reps: Int) {
        viewModelScope.launch {
            val exerciseId = repo.addOptionToSlot(slotId, name, sets, reps)
            dialog.value = null
            // Дождаться, пока новый вариант появится в списке, и выбрать его.
            options.first { list -> list.any { it.slotId == slotId && it.exercise.id == exerciseId } }
            if (drafts.value[slotId]?.editing != false) selectExercise(slotId, exerciseId)
        }
    }

    fun requestExit() {
        dialog.value = WorkoutDialog.ConfirmExit
    }

    fun dismissDialog() {
        dialog.value = null
    }

    fun finish() {
        viewModelScope.launch {
            when (repo.finishSession(sessionId)) {
                GymRepository.FinishResult.FINISHED -> {
                    dialog.value = null
                    nav.value = WorkoutNav.Summary(sessionId)
                }
                GymRepository.FinishResult.EMPTY -> dialog.value = WorkoutDialog.DiscardEmpty
            }
        }
    }

    /** Пустая тренировка: удалить. */
    fun discardEmpty() {
        viewModelScope.launch {
            repo.discardIfEmpty(sessionId)
            dialog.value = null
            nav.value = WorkoutNav.Home
        }
    }

    /** Пустая тренировка: оставить в истории. */
    fun keepEmpty() {
        viewModelScope.launch {
            repo.markFinished(sessionId)
            dialog.value = null
            nav.value = WorkoutNav.Home
        }
    }

    fun onNavHandled() {
        nav.value = null
    }

    private fun restoreDrafts(): Map<String, CardDraft> =
        savedState.get<String>(KEY_DRAFTS)?.let { runCatching { decodeDrafts(it) }.getOrNull() } ?: emptyMap()

    companion object {
        const val ARG_SESSION_ID = "sessionId"
        private const val KEY_DRAFTS = "drafts"

        fun encodeDrafts(map: Map<String, CardDraft>): String {
            val root = JSONObject()
            map.forEach { (slotId, d) ->
                root.put(slotId, JSONObject().apply {
                    put("ex", d.selectedExerciseId)
                    put("sets", d.sets)
                    put("reps", d.reps)
                    put("w", d.weight)
                    put("feel", d.feel?.name ?: "")
                    put("editing", d.editing)
                    put("entry", d.entryId ?: "")
                })
            }
            return root.toString()
        }

        fun decodeDrafts(json: String): Map<String, CardDraft> {
            val root = JSONObject(json)
            return root.keys().asSequence().associateWith { slotId ->
                val o = root.getJSONObject(slotId)
                CardDraft(
                    selectedExerciseId = o.getString("ex"),
                    sets = o.getString("sets"),
                    reps = o.getString("reps"),
                    weight = o.getString("w"),
                    feel = o.getString("feel").takeIf { it.isNotEmpty() }?.let(Feel::valueOf),
                    editing = o.getBoolean("editing"),
                    entryId = o.getString("entry").takeIf { it.isNotEmpty() },
                )
            }
        }
    }
}
