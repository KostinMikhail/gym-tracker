package com.gym.tracker.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/** Самочувствие после упражнения. Хранится в БД по имени (EASY/NORMAL/HARD) — не переименовывать. */
enum class Feel(val label: String) {
    EASY("Легко"),
    NORMAL("Норм"),
    HARD("Тяжело"),
}

@Entity(
    tableName = "exercises",
    indices = [Index(value = ["name"], unique = true)],
)
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val defaultSets: Int,
    val defaultReps: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "workout_templates")
data class WorkoutTemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "template_slots",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["favoriteExerciseId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("templateId"), Index("favoriteExerciseId")],
)
data class TemplateSlotEntity(
    @PrimaryKey val id: String,
    val templateId: String,
    val name: String,
    val sortOrder: Int,
    val favoriteExerciseId: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "slot_options",
    primaryKeys = ["slotId", "exerciseId"],
    foreignKeys = [
        ForeignKey(
            entity = TemplateSlotEntity::class,
            parentColumns = ["id"],
            childColumns = ["slotId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("exerciseId")],
)
data class SlotOptionEntity(
    val slotId: String,
    val exerciseId: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "workout_sessions",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("templateId"), Index("date")],
)
data class WorkoutSessionEntity(
    @PrimaryKey val id: String,
    val templateId: String,
    val date: LocalDate,
    val finishedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "session_entries",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TemplateSlotEntity::class,
            parentColumns = ["id"],
            childColumns = ["slotId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("sessionId"), Index("slotId"), Index("exerciseId")],
)
data class SessionEntryEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val slotId: String?,
    val exerciseId: String,
    val sets: Int,
    val reps: Int,
    val weightKg: Double,
    val feel: Feel,
    val createdAt: Long,
    val updatedAt: Long,
)
