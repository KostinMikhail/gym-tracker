/**
 * Gym → Google Таблица: приёмник журнала тренировок (Web App на Google Apps Script).
 *
 * Как настроить (подробно — в README.md репозитория, раздел «Синхронизация с Google Таблицей»):
 *  1. Откройте свою Google Таблицу → Расширения → Apps Script.
 *  2. Удалите всё в файле Code.gs и вставьте этот файл целиком. Сохраните.
 *  3. Настройки проекта (шестерёнка слева) → «Свойства скрипта» → Добавить свойство:
 *     имя TOKEN, значение — длинная случайная строка (её же вы введёте в приложении).
 *  4. «Развернуть» → «Новое развёртывание» → тип «Веб-приложение»:
 *     «Запуск от имени» — Я, «Кто имеет доступ» — Все. Разрешите доступ к таблице.
 *  5. Скопируйте ссылку веб-приложения (заканчивается на /exec) и вставьте её в приложение
 *     вместе с токеном: Настройки → «Проверить связь» → «Выгрузить всю историю».
 *  После правок скрипта: «Развернуть» → «Управление развёртываниями» → карандаш → «Новая версия» —
 *  ссылка останется прежней.
 *
 * Протокол: POST {"token": "...", "ops": [
 *   {"op": "upsert", "row": {...}} | {"op": "delete", "entry_id": "..."} | {"op": "ping"} ]}
 * Ответ: {"ok": true, "applied": [entry_id...], "sheet": "Журнал"} или {"ok": false, "error": "..."}.
 * Операции идемпотентны: повторная отправка ничего не дублирует (строка ищется по entry_id).
 * Чтения через скрипт нет (doGet не определён) — данные читаются по ссылке на саму таблицу.
 */

var SHEET_NAME = 'Журнал';
var HEADER = ['entry_id', 'session_id', 'дата', 'тренировка', 'группа', 'упражнение',
  'подходы', 'повторы', 'вес_кг', 'ощущение', 'обновлено'];
/** Ключи строки в запросе приложения — в порядке столбцов. */
var KEYS = ['entry_id', 'session_id', 'date', 'workout', 'group', 'exercise',
  'sets', 'reps', 'weight_kg', 'feel', 'updated'];
/** Столбцы, которые хранятся как текст (дата и время не переформатируются под локаль). */
var TEXT_COLUMNS = [3, 11];

function doPost(e) {
  var req;
  try {
    req = JSON.parse(e && e.postData ? e.postData.contents : '');
  } catch (err) {
    return json_({ ok: false, error: 'bad_request' });
  }
  var token = PropertiesService.getScriptProperties().getProperty('TOKEN');
  if (!token || !req || typeof req.token !== 'string' || req.token !== token) {
    return json_({ ok: false, error: 'unauthorized' });
  }
  var ops = Array.isArray(req.ops) ? req.ops : [];
  var lock = LockService.getScriptLock();
  if (!lock.tryLock(30000)) {
    return json_({ ok: false, error: 'busy' });
  }
  try {
    var applied = apply_(sheet_(), ops);
    return json_({ ok: true, applied: applied, sheet: SHEET_NAME });
  } catch (err) {
    return json_({ ok: false, error: String((err && err.message) || err) });
  } finally {
    lock.releaseLock();
  }
}

/** Лист «Журнал»; создаётся с заголовком (жирный, закреплён), если его нет. */
function sheet_() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sh = ss.getSheetByName(SHEET_NAME) || ss.insertSheet(SHEET_NAME);
  if (sh.getLastRow() === 0) {
    sh.getRange(1, 1, 1, HEADER.length).setValues([HEADER]).setFontWeight('bold');
    sh.setFrozenRows(1);
  }
  return sh;
}

/**
 * Применяет операции к листу. Таблица читается один раз (карта entry_id → строка),
 * изменения делаются в памяти и записываются пакетно (setValues), без записи по ячейке.
 * Возвращает entry_id всех применённых upsert/delete (delete отсутствующей строки — тоже «применён»).
 */
function apply_(sh, ops) {
  var width = HEADER.length;
  var last = sh.getLastRow();
  var body = last > 1 ? sh.getRange(2, 1, last - 1, width).getValues() : [];
  var index = {};
  for (var i = 0; i < body.length; i++) {
    if (body[i][0] !== '' && body[i][0] !== null) index[String(body[i][0])] = i;
  }
  var applied = [];
  var updated = {}; // индекс строки → true (изменена)
  var appended = [];
  var removed = []; // индексы строк для удаления
  ops.forEach(function (op) {
    if (!op || typeof op.op !== 'string') return;
    if (op.op === 'upsert' && op.row && op.row.entry_id) {
      var id = String(op.row.entry_id);
      var values = KEYS.map(function (k) {
        var v = op.row[k];
        return v === undefined || v === null ? '' : v;
      });
      if (index.hasOwnProperty(id)) {
        var at = index[id];
        if (at >= 0) {
          body[at] = values;
          updated[at] = true;
        } else {
          appended[-at - 1] = values; // повтор в том же запросе — обновить уже добавленную
        }
      } else {
        appended.push(values);
        index[id] = -appended.length; // отрицательный индекс — новая строка
      }
      applied.push(id);
    } else if (op.op === 'delete' && op.entry_id) {
      var did = String(op.entry_id);
      if (index.hasOwnProperty(did)) {
        var di = index[did];
        if (di >= 0) {
          removed.push(di);
        } else {
          appended[-di - 1] = null; // добавлена и удалена в одном запросе
        }
        delete index[did];
      }
      applied.push(did);
    }
    // ping и неизвестные операции ничего не меняют.
  });

  // 1. Обновления существующих строк — одним setValues на диапазон от первой до последней изменённой.
  var changed = Object.keys(updated).map(Number).filter(function (r) { return removed.indexOf(r) < 0; });
  if (changed.length) {
    var from = Math.min.apply(null, changed);
    var to = Math.max.apply(null, changed);
    var block = body.slice(from, to + 1);
    textFormat_(sh, from + 2, block.length);
    sh.getRange(from + 2, 1, block.length, width).setValues(block);
  }
  // 2. Удаления — снизу вверх (номера строк выше не сдвигаются), подряд идущие — одним deleteRows.
  removed.sort(function (a, b) { return b - a; });
  for (var k = 0; k < removed.length;) {
    var top = removed[k];
    var n = 1;
    while (k + n < removed.length && removed[k + n] === top - n) n++;
    sh.deleteRows(top - n + 1 + 2, n);
    k += n;
  }
  // 3. Новые строки — в конец одним setValues.
  var rows = appended.filter(function (r) { return r; });
  if (rows.length) {
    var start = sh.getLastRow() + 1;
    textFormat_(sh, start, rows.length);
    sh.getRange(start, 1, rows.length, width).setValues(rows);
  }
  return applied;
}

/** Текстовый формат для столбцов даты/времени в диапазоне строк (до записи значений). */
function textFormat_(sh, row, count) {
  TEXT_COLUMNS.forEach(function (c) {
    sh.getRange(row, c, count, 1).setNumberFormat('@');
  });
}

function json_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}
