package com.gym.tracker.data.db

import java.util.UUID

/**
 * Начальные данные: шаблоны, слоты и варианты упражнений.
 * Первый вариант в каждом слоте — избранный. По умолчанию 3×12.
 */
object SeedData {

    data class Ex(val name: String, val sets: Int = 3, val reps: Int = 12)
    data class Slot(val name: String, val options: List<Ex>)
    data class Template(val name: String, val slots: List<Slot>)

    private val crunchesFloor = Ex("Скручивания на полу", 3, 40)

    val templates: List<Template> = listOf(
        Template(
            "Фулбади А",
            listOf(
                Slot("Жим ногами", listOf(Ex("Жим ногами в тренажёре"))),
                Slot("Грудь", listOf(Ex("Жим лёжа в тренажёре"), Ex("Жим гантелей лёжа"), Ex("Жим штанги лёжа"))),
                Slot("Спина (вертикальная тяга)", listOf(Ex("Тяга верхнего блока в тренажёре"), Ex("Тяга верхнего блока"))),
                Slot("Задняя поверхность бедра", listOf(Ex("Румынская тяга с гантелями"))),
                Slot("Плечи", listOf(Ex("Жим на плечи в тренажёре"), Ex("Жим гантелей сидя"))),
                Slot("Пресс", listOf(Ex("Скручивание рычажное"), crunchesFloor)),
            ),
        ),
        Template(
            "Фулбади Б",
            listOf(
                Slot("Ноги", listOf(Ex("Гоблет-присед"))),
                Slot("Спина (горизонтальная тяга)", listOf(Ex("Горизонтальная тяга на блоке"))),
                Slot("Грудь (наклонный жим)", listOf(Ex("Наклонный жим гантелей"), Ex("Наклонный жим в тренажёре"), Ex("Жим от груди стоя в тренажёре"))),
                Slot("Сгибание ног", listOf(Ex("Сгибание ног в тренажёре"))),
                Slot("Задние дельты", listOf(Ex("Тяга к лицу на блоке"))),
                Slot("Поясница / ягодицы", listOf(Ex("Гиперэкстензия"), Ex("Ягодичный мостик"))),
                Slot("Кор", listOf(Ex("Pallof press", 3, 10), Ex("Скручивание рычажное"), crunchesFloor)),
            ),
        ),
    )

    data class Bundle(
        val exercises: List<ExerciseEntity>,
        val templates: List<WorkoutTemplateEntity>,
        val slots: List<TemplateSlotEntity>,
        val options: List<SlotOptionEntity>,
    )

    /** Строит строки для вставки. Упражнения с одинаковым именем — одна строка. */
    fun build(now: Long, newId: () -> String = { UUID.randomUUID().toString() }): Bundle {
        val exercisesByName = LinkedHashMap<String, ExerciseEntity>()
        val templateRows = mutableListOf<WorkoutTemplateEntity>()
        val slotRows = mutableListOf<TemplateSlotEntity>()
        val optionRows = mutableListOf<SlotOptionEntity>()

        templates.forEachIndexed { tIndex, t ->
            val templateId = newId()
            templateRows += WorkoutTemplateEntity(templateId, t.name, tIndex, now, now)
            t.slots.forEachIndexed { sIndex, s ->
                val slotId = newId()
                val optionExercises = s.options.map { ex ->
                    exercisesByName.getOrPut(ex.name) {
                        ExerciseEntity(newId(), ex.name, ex.sets, ex.reps, now, now)
                    }
                }
                slotRows += TemplateSlotEntity(slotId, templateId, s.name, sIndex, optionExercises.first().id, now, now)
                optionExercises.forEachIndexed { oIndex, ex ->
                    optionRows += SlotOptionEntity(slotId, ex.id, oIndex, now, now)
                }
            }
        }
        return Bundle(exercisesByName.values.toList(), templateRows, slotRows, optionRows)
    }
}
