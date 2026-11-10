# Blackboard — план реализации MVP

Каждый шаг — самостоятельный промпт для реализации. Шаги выполняются по порядку; каждый оставляет проект в собираемом состоянии.

Общие правила для всех шагов (действуют неявно в каждом промпте):

- Спецификация: `specs/writing-block.md`. При расхождении спека важнее этого плана.
- Целевая платформа: Rider 2026.2.3.1 (build 262), Kotlin, JDK 25 — менять версии в `build.gradle.kts` / `gradle.properties` нельзя.
- Пакет `com.blackboard`, структура из раздела 5 спеки.
- Никакого дискового I/O на EDT. Соблюдать threading-правила платформы 262 (coroutines + `Dispatchers.EDT`/`Dispatchers.IO`, read/write actions только там, где их требует API).
- Только нативные компоненты и цвета IDE, без хардкода цветов.
- Шаг считается готовым, когда `./gradlew buildPlugin test` проходит без ошибок.

---

## Шаг 1. Каркас Tool Window

```text
Прочитай specs/writing-block.md (разделы 3.1, 3.2, 5.1, 5.2, 8).

Сделай минимальный каркас плагина:
- ui/BlackboardToolWindowFactory.kt — ToolWindowFactory (DumbAware), регистрирует контент с BlackboardPanel.
- ui/BlackboardPanel.kt — пока JBPanel с BorderLayout: в центре заглушка (JBLabel "Blackboard"), внизу место под статус. Панель реализует Disposable и регистрируется в Disposer контента.
- plugin.xml: <toolWindow id="Blackboard" anchor="right" secondary="false" icon=... factoryClass=...>, заголовок "Blackboard". Иконка — встроенная платформенная (например AllIcons.Toolwindows.Documentation или аналогичная) либо свой SVG 13x13 в resources/icons с вариантом _dark.
- Ресурс-бандл messages/BlackboardBundle.properties + BlackboardBundle.kt для всех пользовательских строк.

Проверка: ./gradlew buildPlugin; кратко опиши, как проверить через ./gradlew runIde, что окно появляется справа и в меню View → Tool Windows.
```

## Шаг 2. Хранилище (BlackboardStorage)

```text
Прочитай specs/writing-block.md (FR-005, раздел 5.4, раздел 6).

Реализуй storage/BlackboardStorage.kt — чистый класс без зависимостей от UI, работающий с java.nio:
- Разрешение пути: <project-root>/.idea/blackboard.md. Если у проекта нет basePath или .idea недоступна для записи — fallback в хранилище данных проекта IDE (project.getProjectDataPath("blackboard") или аналог для 262), файл blackboard.md. Логика выбора пути вынесена так, чтобы её можно было протестировать без Project (передавать Path-ы).
- read(): String? — null, если файла нет; UTF-8; ошибки чтения возвращаются как результат (sealed class / Result), не бросаются наружу.
- write(text): атомарная запись — временный файл в той же папке + Files.move(ATOMIC_MOVE, REPLACE_EXISTING), с fallback без ATOMIC_MOVE. Создаёт родительскую папку при первой записи. Файл не создаётся до первой записи.
- Метаданные для детекции внешних изменений: lastModified + хэш содержимого последней прочитанной/записанной версии.
- Не трогать .gitignore.
- Логирование через com.intellij.openapi.diagnostic.logger.

Тесты (JUnit4, src/test/kotlin/com/blackboard/storage): чтение отсутствующего файла, запись/чтение UTF-8 (кириллица, эмодзи), создание папки, атомарная перезапись, ошибка записи в read-only путь не бросает исключение, fallback-путь.
```

## Шаг 3. Сервис проекта (BlackboardService)

```text
Прочитай specs/writing-block.md (FR-003, FR-004, раздел 5.3, раздел 6, раздел 7).

Реализуй services/BlackboardService.kt — @Service(Service.Level.PROJECT), получает CoroutineScope через конструктор (рекомендованный способ для 262):
- Состояние: текущий текст в памяти, флаг загрузки, ревизия (монотонный счётчик изменений), статус сохранения как StateFlow<SaveStatus> (Loading, Saved, Saving, Failed(error), Conflict).
- load(): асинхронно читает через BlackboardStorage на Dispatchers.IO; до окончания загрузки сохранения запрещены (защита от перезаписи непустого файла пустым текстом).
- onTextChanged(text): обновляет текст и ревизию, запускает debounce 1 с; при непрерывном вводе сохранение не реже чем раз в 5 с.
- Сохранение строго последовательное (Mutex или единственный consumer), в файл всегда пишется последняя ревизия; более старая ревизия никогда не перезаписывает новую.
- При ошибке — статус Failed, текст в памяти сохраняется; метод retrySave().
- flush(): немедленно сохраняет несохранённое; вызывается при dispose сервиса / закрытии проекта (ProjectCloseListener или Disposable) — синхронно на фоне с разумным таймаутом, без блокировки EDT где это возможно.
- Никакого application-level состояния.

Тесты: debounce (много изменений → одна запись), порядок ревизий, отсутствие записи до окончания загрузки, ошибка записи → Failed и retry, flush пишет последнюю версию. Для тестов сервиса допустимо вынести ядро логики в класс, принимающий storage-интерфейс и scope, чтобы тестировать с kotlinx-coroutines-test без поднятия IDE.
```

## Шаг 4. Markdown-редактор в панели

```text
Прочитай specs/writing-block.md (FR-002, FR-007, раздел 5.2, раздел 8).

Замени заглушку в BlackboardPanel на настоящий редактор:
- EditorFactory.createEditor(document, project, fileType, false) с EditorEx; Document — через EditorFactory.createDocument или LightVirtualFile с Markdown-типом, чтобы работала подсветка.
- Тип файла Markdown ищется в рантайме (FileTypeManager по имени/расширению "md"); если плагин Markdown недоступен — PlainTextFileType. Жёсткой зависимости <depends>org.intellij.plugins.markdown</depends> не добавлять (если без неё подсветка невозможна — используй optional depends с отдельным config-file и опиши почему).
- Настройки редактора: soft wraps включены, без line numbers/gutter/folding outline, шрифт и цвета из текущей схемы IDE, undo/redo через стандартный механизм.
- Плейсхолдер "Write something..." через EditorEx.setPlaceholder + setShowPlaceholderWhenFocused(true); в документ ничего не вставлять.
- Пока сервис загружается — редактор read-only или не показан; после загрузки текст устанавливается без генерации "пользовательского" изменения (не триггерит сохранение).
- DocumentListener → service.onTextChanged. Редактор освобождается через EditorFactory.releaseEditor в dispose панели.
- Если Tool Window закрыть и открыть снова — показывается актуальный текст из сервиса, а не из файла.

Проверка: buildPlugin, затем опиши ручную проверку через runIde (ввод, подсветка заголовков/кода, undo, переоткрытие окна).
```

## Шаг 5. Индикатор статуса и повтор сохранения

```text
Прочитай specs/writing-block.md (FR-008, раздел 8 «Accessibility»).

Добавь внизу BlackboardPanel компактную строку статуса:
- Подписка на StateFlow<SaveStatus> сервиса в coroutine scope панели (отменяется при dispose), обновление UI на Dispatchers.EDT.
- Состояния: "Saved" (иконка-галочка + текст), "Saving...", "Unable to save" (иконка ошибки + текст + ссылка "Retry" как ActionLink), "Conflict" (заготовка, используется в шаге 7).
- Состояние не должно передаваться только цветом — всегда текст; accessibleName у элементов.
- Никаких уведомлений об успешном сохранении. При повторяющейся ошибке (например 3 подряд) — один balloon-notification через NotificationGroup (зарегистрировать в plugin.xml) с действием Retry; не спамить.
- Стили: JBUI.Borders, UIUtil/JBColor, мелкий шрифт (JBFont.small()).
```

## Шаг 6. Action, горячая клавиша и фокус

```text
Прочитай specs/writing-block.md (FR-001, FR-006).

- actions/ToggleBlackboardAction.kt (DumbAwareAction): если окно скрыто — activate и фокус в редактор; если открыто и в фокусе — hide и вернуть фокус в предыдущий редактор; если открыто без фокуса — перевести фокус в редактор.
- Регистрация в plugin.xml: id "Blackboard.Toggle", текст из бандла, добавить в группу ViewMenu / Tool Windows; keyboard-shortcut Alt+B для keymap $default. Перед этим проверь в SDK, не занят ли Alt+B в Rider-кеймапах (Visual Studio, ReSharper, Rider по умолчанию); если занят — не перебивать молча: оставь шорткат только там, где он свободен, и опиши решение в README.
- Позиция каретки и скролл запоминаются в сервисе (в памяти) и восстанавливаются при открытии через action.
- При открытии кликом по stripe фокус ведёт себя стандартно (не насильно в редактор).
- Возврат фокуса при скрытии: запомнить последний FileEditor/компонент перед активацией, вернуть через IdeFocusManager, если он ещё валиден.
```

## Шаг 7. Внешние изменения файла и конфликты

```text
Прочитай specs/writing-block.md (раздел 6, пункт 7 и абзац про внешние изменения).

- Перед каждой записью сервис сверяет текущие lastModified/хэш файла с последней известной версией.
- Подписка на VFS (BulkFileListener через messageBus проекта) или проверка при активации окна — для обнаружения внешних изменений blackboard.md.
- Нет несохранённых локальных правок → тихо перезагрузить текст из файла (редактор обновляется без триггера сохранения, каретка по возможности сохраняется).
- Есть несохранённые правки и файл изменён извне → статус Conflict, автосохранение приостановлено. В строке статуса: "File changed on disk" + ссылки "Keep mine" (перезаписать файл текстом из памяти) и "Load from disk" (заменить текст; это действие должно быть отменяемым через undo либо запрашивать подтверждение).
- Удаление файла извне при наличии текста в памяти — не считается конфликтом данных: при следующем сохранении файл создаётся заново.
- Тесты на логику конфликта на уровне ядра сервиса.
```

## Шаг 8. Жизненный цикл и надёжность

```text
Прочитай specs/writing-block.md (FR-003 acceptance criteria, раздел 6, раздел 7).

Проведи ревизию и доведи надёжность:
- Закрытие проекта и выход из IDE: несохранённое гарантированно сбрасывается (проверь порядок dispose сервиса, панели и редактора; flush не должен зависеть от того, жива ли панель).
- Несколько открытых проектов: заметки не смешиваются (интеграционный тест на BasePlatformTestCase / HeavyPlatformTestCase с двумя путями или юнит-тест на разрешение путей).
- Файл ~1 МБ: открытие и ввод без лагов; загрузка/сохранение только на IO.
- Гонки: быстрое закрытие окна сразу после ввода, закрытие проекта во время записи, retry во время сохранения.
- Пройди по тредингу: нет I/O на EDT, нет устаревших API (Plugin Verifier 262 не должен ругаться на deprecated/internal).
- Прогони ./gradlew verifyPlugin и исправь замечания.
```

## Шаг 9. Полировка, README и приёмка

```text
Прочитай specs/writing-block.md целиком и сверь реализацию с каждым FR и разделом 8.

- Узкая панель (~300 px), светлая/тёмная темы, масштабирование шрифта IDE — поправь отступы и переносы.
- Убедись, что все строки в бандле, иконки корректны в обеих темах.
- Обнови README: возможности, горячая клавиша и как её поменять, где хранится файл и что он не добавляется в .gitignore, сборка и установка.
- Составь в specs/acceptance-checklist.md ручной чек-лист по acceptance criteria FR-001…FR-008 (включая перезапуск Rider) и отметь, что проверено автоматически, а что нужно проверить вручную через runIde.
- Финал: ./gradlew buildPlugin test verifyPlugin.
```
