# CLAUDE.md — постоянные правила проекта Gym

Личное Android-приложение для записи тренировок. Один пользователь — владелец. Приложение много раз обновляется поверх его реальных данных.

## Обязательные правила для каждой задачи

1. **Сохраняй существующий код и весь функционал.** Меняй только то, что требует задача (или существующий код, от которого новая функция действительно зависит). Никаких посторонних рефакторингов, переименований, переформатирования и обновлений зависимостей.
2. **Любое изменение схемы Room** → `version + 1` в `GymDatabase`, новая `Migration` в `GymDatabase.MIGRATIONS`, тест миграции в `MigrationTest` (данные, вставленные в старую версию, на месте после миграции). Новый JSON схемы в `app/schemas/` коммитится. **Никогда** `fallbackToDestructiveMigration` и другие деструктивные миграции. Данные пользователя должны переживать каждое обновление. `applicationId` (`com.gym.tracker`) и ключ подписи не меняются никогда.
3. Весь текст интерфейса — **на русском** (новые строки — в `res/values/strings.xml`); **только килограммы**. Оформление — только через систему тем (см. «Темы»): никаких захардкоженных цветов/форм в экранах, все 7 тем должны выглядеть корректно; системная светлая/тёмная настройка не используется.
4. Перед завершением любой задачи: сборка, все тесты и lint (`gradlew.bat clean assembleRelease testDebugUnitTest lint`), проверка критериев приёмки по одному, повторное чтение всего diff в поисках регрессий и забытого отладочного кода; всё найденное — исправить.
5. Коммит с понятным сообщением и push в `main`, чтобы CI выпустил обновление — только когда всё проходит. Проверить, что CI зелёный и релиз появился (`gh run watch`).
6. Финальный отчёт владельцу на русском: что изменилось, какие файлы, как проверено, что нужно сделать владельцу.

Также: держи раздел «Архитектура» ниже в актуальном состоянии.

## Сборка и окружение (Windows)

- JDK 17: `C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot` (`JAVA_HOME`), Android SDK: `%LOCALAPPDATA%\Android\Sdk` (`ANDROID_HOME`).
- Gradle wrapper 9.9, AGP 9.5 (встроенная поддержка Kotlin — плагин `kotlin-android` не применяется), Kotlin 2.4, KSP, Room 2.8 (Room Gradle Plugin, схемы в `app/schemas`).
- Release-подпись локально: git-игнорируемый `keystore.properties` в корне; в CI — секреты `GYM_KEYSTORE_BASE64`, `GYM_KEYSTORE_PASSWORD`, `GYM_KEY_ALIAS`, `GYM_KEY_PASSWORD`. Сам keystore лежит вне репозитория (`%USERPROFILE%\gym-keystore\`). Keystore и пароли **никогда** не коммитить.
- Версия: `versionCode` = `GITHUB_RUN_NUMBER`, `versionName` = `1.0.<run>`; локально 1.
- CI: `.github/workflows/release.yml` (push в main → тесты + lint → подписанный APK → Release `v1.0.<run>` с `gym-1.0.<run>.apk`, проверка отпечатка сертификата), `.github/workflows/pr.yml` (PR → сборка + тесты + lint).
- Release собирается с R8 (`isMinifyEnabled = true`), keep-правила в `app/proguard-rules.pro`.
- В Bash-инструменте gradle-вывод виден лучше, чем в PowerShell: `cd /c/Git/gym && ./gradlew ...`.

## Архитектура

Single-activity, Jetpack Compose + Material 3, Navigation Compose (строковые маршруты), MVVM (ViewModel + StateFlow), ручной DI.

```
com.gym.tracker
├── GymApplication        — ручной DI: GymDatabase + GymRepository + один DataStore «settings» (ThemePreferences, TimerPreferences) + TimerController (lazy)
├── MainActivity          — читает тему до первого кадра, системные панели по теме, GymTheme(themeId) { GymApp() };
│                           EXTRA_OPEN_TIMER из уведомления таймера → вкладка «Таймер». Только портретная ориентация.
├── data
│   ├── GymRepository     — единственная точка доступа UI к данным (транзакции, бизнес-правила записи)
│   ├── ThemePreferences  — выбранная тема в DataStore (Preferences), не в Room
│   ├── TimerPreferences  — последние настройки таймера (раунды/раунд/отдых) и флаг «разрешение уведомлений спрашивали»
│   └── db
│       ├── Entities.kt   — сущности + enum Feel
│       ├── Projections.kt— POJO для JOIN-запросов
│       ├── Daos.kt       — ExerciseDao, TemplateDao, SessionDao
│       ├── Converters.kt — LocalDate ↔ "yyyy-MM-dd", Feel ↔ имя
│       ├── SeedData.kt   — начальные шаблоны/слоты/упражнения (чистая функция build)
│       └── GymDatabase.kt— Room DB (version 1), MIGRATIONS, SeedCallback (onCreate)
├── domain
│   ├── WorkoutLogic.kt   — prefillFor, roundWeight, normalizeName, suggestTemplateId
│   ├── Format.kt         — даты dd.MM / dd.MM.yyyy, вес («без веса» для 0), «3×12 · 45 кг · Норм»
│   └── timer/            — IntervalTimer (чистая машина состояний таймера, часы внедряются), TimerConfig, TimerFormat
├── timer
│   ├── TimerController   — один таймер на процесс: StateFlow<TimerSnapshot?> (null = настройки) + поток сигналов; цикл в MainScope
│   ├── TimerService      — foreground-сервис (specialUse): уведомление с Пауза/Продолжить, wake lock, звук; стоп при сбросе/после «Готово»
│   └── TimerSoundPlayer  — SoundPool (res/raw/timer_*.wav), фокус GAIN_TRANSIENT_MAY_DUCK, вибрация
└── ui
    ├── GymApp.kt         — NavHost, Routes, нижняя навигация (Тренировка / История / Упражнения / Таймер)
    ├── ViewModels.kt     — gymViewModel { repo, savedStateHandle -> VM }
    ├── theme/            — система тем: GymThemeId, GymTokens (+LocalGymTokens), Themes.kt (7 тем), Fonts.kt,
    │                       Effects.kt (свечение, жёсткая тень, пунктир, уголки, дизеринг), Decor.kt (фон, декор шапки, ScreenTitle)
    ├── components/       — NumberStepper, FeelSelector, ExerciseDialog, ConfirmDialog…; Themed.kt: GymCard, GymDoneButton, StepperButton, ThemeButton
    ├── home/             — HomeScreen/HomeContent/HomeViewModel: кнопка темы + дата, «Начать тренировку», выбор шаблона, последние тренировки;
    │                       ThemePicker.kt — нижний лист «Тема оформления»
    ├── workout/          — WorkoutScreen/WorkoutViewModel: карточки слотов, черновики в SavedStateHandle
    ├── history/          — HistoryScreen (по датам / по упражнениям), SessionDetailScreen (+ итоги), ExerciseHistoryScreen (график Canvas + таблица + рекорд)
    ├── exercises/        — ExercisesScreen: список, редактирование названия/подходов/повторов
    └── timer/            — TimerScreen (настройки / ход / пауза / «Готово»), TimerViewModel, TabularText (цифры в ячейках фикс. ширины)
```

### Сущности (все id — String UUID; у пользовательских данных `createdAt`/`updatedAt` в epoch millis — задел под будущую синхронизацию)

- `exercises` — id, name (уникальное), defaultSets, defaultReps. Одинаковое название = одна строка.
- `workout_templates` — id, name, sortOrder.
- `template_slots` — id, templateId → templates (CASCADE), name, sortOrder, favoriteExerciseId → exercises (SET NULL). Избранное одно на слот (одна колонка).
- `slot_options` — PK (slotId, exerciseId), sortOrder.
- `workout_sessions` — id, templateId → templates (RESTRICT), date (LocalDate), finishedAt (nullable).
- `session_entries` — id, sessionId → sessions (CASCADE), slotId → slots (SET NULL, nullable), exerciseId → exercises (RESTRICT), sets, reps, weightKg (шаг 0,5; 0 = без веса), feel (EASY/NORMAL/HARD).

### Навигация

`home`, `history`, `exercises`, `timer` (вкладки) → `workout/{sessionId}`, `session/{sessionId}?summary={bool}`, `exercise/{exerciseId}`.
Тренировка создаётся при старте (строка в `workout_sessions`), каждая «Готово» сразу пишет `session_entries`. «Продолжить тренировку» нет. «Завершить» → `finishedAt` и экран итогов; пустую тренировку предлагается удалить.

### Ключевые правила поведения

- «В прошлый раз» и вес для предзаполнения — последняя запись упражнения в любой *другой* тренировке (по дате тренировки, затем по времени создания).
- Смена упражнения в карточке заново предзаполняет подходы/повторы (из упражнения) и вес (из последней записи).
- Звёздочка делает вариант избранным для слота (сохраняется сразу, влияет на будущие тренировки) и выбирает его.
- Добавление варианта в слот переиспользует существующее упражнение с тем же названием (без учёта регистра/пробелов).
- Редактор шаблонов/слотов пока не реализован (отдельная задача).
- Таймер: подготовка 5 с → раунд (1 гонг) → «10 секунд» (3 стука + предупреждение, только если раунд > 10 с) → отдых (2 гонга; при отдыхе 0 — сразу 1 гонг) → … → 3 гонга и «Готово». Время — только по дедлайнам SystemClock.elapsedRealtime (следующая фаза от дедлайна предыдущей, без дрейфа). Пока таймер идёт/на паузе: нижней панели нет, экран не гаснет; Back — сразу остановить таймер и выйти (без вопроса). Фаза и номер раунда — одной строкой наверху, полосы прогресса нет. «Сбросить всё» — обычным нажатием. Смерть процесса = таймер сброшен (настройки в DataStore). Звуки синтезирует `tools/gen_timer_sounds.py` (без записей): 3 варианта гонга (`BELL` = ring / heavy / electric), `--options DIR` — все варианты для выбора на слух.
- Экран тренировки: шапка уезжает при прокрутке вниз (enterAlways) и возвращается при прокрутке вверх; полоса «Фулбади Б · N / M» закреплена; нижняя панель навигации видна и на тренировке и прячется при прокрутке вниз (только там). Вкладка «Тренировка» на этом экране — текущая.

### Темы

- 7 тем (`GymThemeId`, порядок = порядок в списке): Классическая (по умолчанию, исходный вид), Дизеринг 1-bit, Дизеринг Game Boy, Неон синтвейв, Неон кислота, Киберпанк жёлтый, Киберпанк HUD.
- Тема = Material `ColorScheme` + `Typography` + `Shapes` + `GymTokens` (через `LocalGymTokens`): цвета самочувствия, роли карточек (`CardRole`), формы, свечение, жёсткие тени, декор, стиль кнопки «Готово», нижней панели и т.д. Экраны общие для всех тем — новые экраны строить на `GymCard`, `GymDoneButton`, `ScreenTitle`, `HeaderDecor`, токенах.
- В Классической все необязательные токены = null → поведение и вид как у Material по умолчанию (как было до тем).
- `storageKey` тем сохраняется в DataStore — существующие значения не менять; неизвестное значение → Классическая.
- Шрифты — в `res/font` (офлайн), лицензии OFL — в `licenses/`. В Pixelify Sans добавлены cmap-записи для «О» и «П».
- Контраст WCAG для всех тем проверяет `ThemeContrastTest` — при изменении цветов тест должен проходить.
- Экраны прозрачные (`containerColor = Transparent`), фон и декор рисует `GymBackground` один раз на весь экран.
- Таймер: `GymTokens.timer` — `TimerPhaseStyle` для работы / предупреждения / отдыха (фон экрана, цифры, кнопки, рамка, уголки, полоса дизеринга, пульсация) и шрифт цифр. Предупреждение обязано менять фон или крупную рамку и отличаться по яркости — проверяет `TimerThemeTest`.

### Тесты (`app/src/test`, JVM + Robolectric, запускаются `testDebugUnitTest`)

- `SeedDataTest`, `WorkoutLogicTest` — чистая логика.
- `data/RepositoryTest` — DAO и репозиторий на in-memory Room с сидом.
- `data/ThemePreferencesTest` — сохранение темы и откат к Классической; `ThemeContrastTest` — контраст всех тем; `ProgressTest` — счётчик «N / M» и дата шапки.
- `IntervalTimerTest` (последовательность, 10 с, отдых 0, 1 раунд, пауза, «Раунд заново», без дрейфа за 60 мин), `TimerControllerTest` (виртуальное время), `TimerThemeTest` (контраст и различимость состояний таймера), `data/TimerPreferencesTest`.
- `data/MigrationTest` — `MigrationTestHelper` по схемам из `app/schemas` (подключены как debug-assets). Путь к тестовой БД абсолютный — иначе Room на Windows падает.
