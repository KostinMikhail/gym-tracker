// Локальная проверка Code.gs с подменёнными SpreadsheetApp / PropertiesService / LockService / ContentService.
// Запуск: node tools/apps-script/Code.test.js   (Node 16+, без зависимостей)
'use strict';
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const assert = require('assert');

function makeSheet() {
  const cells = []; // массив строк (1-я строка = заголовок)
  const formats = {};
  const stats = { setValues: 0, deleteRows: 0 };
  const sheet = {
    cells, formats, stats, frozen: 0,
    getLastRow: () => cells.length,
    setFrozenRows(n) { this.frozen = n; },
    deleteRows(row, n) { stats.deleteRows++; cells.splice(row - 1, n); },
    getRange(row, col, nr, nc) {
      return {
        getValues: () => {
          const out = [];
          for (let r = 0; r < nr; r++) {
            const src = cells[row - 1 + r] || [];
            const line = [];
            for (let c = 0; c < nc; c++) line.push(src[col - 1 + c] === undefined ? '' : src[col - 1 + c]);
            out.push(line);
          }
          return out;
        },
        setValues(values) {
          stats.setValues++;
          assert.strictEqual(values.length, nr, 'число строк в setValues');
          values.forEach((v, r) => {
            assert.strictEqual(v.length, nc, 'число столбцов в setValues');
            const line = cells[row - 1 + r] || (cells[row - 1 + r] = []);
            v.forEach((x, c) => { line[col - 1 + c] = x; });
          });
          return this;
        },
        setFontWeight() { return this; },
        setNumberFormat(f) { for (let r = 0; r < nr; r++) formats[`${row + r}:${col}`] = f; return this; },
      };
    },
  };
  return sheet;
}

function load() {
  const sheets = {};
  const ctx = {
    SpreadsheetApp: {
      getActiveSpreadsheet: () => ({
        getSheetByName: (n) => sheets[n] || null,
        insertSheet: (n) => (sheets[n] = makeSheet()),
      }),
    },
    PropertiesService: { getScriptProperties: () => ({ getProperty: (k) => (k === 'TOKEN' ? 'secret' : null) }) },
    LockService: { getScriptLock: () => ({ tryLock: () => true, releaseLock: () => {} }) },
    ContentService: {
      MimeType: { JSON: 'json' },
      createTextOutput: (s) => ({ text: s, setMimeType() { return this; } }),
    },
  };
  vm.createContext(ctx);
  vm.runInContext(fs.readFileSync(path.join(__dirname, 'Code.gs'), 'utf8'), ctx);
  const raw = (contents) => JSON.parse(ctx.doPost({ postData: { contents } }).text);
  const post = (body) => raw(JSON.stringify(body));
  return { post, raw, sheets };
}

const row = (id, extra = {}) => Object.assign({
  entry_id: id, session_id: 's1', date: '2026-11-20', workout: 'Фулбади А', group: 'Грудь',
  exercise: 'Жим', sets: 3, reps: 12, weight_kg: 45.5, feel: 'Норм', updated: '2026-11-20T15:30:00+03:00',
}, extra);
const ids = (sh) => sh.cells.slice(1).map((r) => r[0]);

// Токен
{
  const { post, sheets } = load();
  assert.deepStrictEqual(post({ token: 'wrong', ops: [{ op: 'upsert', row: row('a') }] }), { ok: false, error: 'unauthorized' });
  assert.deepStrictEqual(post({ ops: [] }), { ok: false, error: 'unauthorized' });
  assert.strictEqual(sheets['Журнал'], undefined, 'при неверном токене ничего не пишется');
}

// ping, заголовок, upsert/append, формат даты
{
  const { post, sheets } = load();
  assert.deepStrictEqual(post({ token: 'secret', ops: [{ op: 'ping' }] }), { ok: true, applied: [], sheet: 'Журнал' });
  const sh = sheets['Журнал'];
  assert.strictEqual(sh.cells[0][2], 'дата');
  assert.strictEqual(sh.frozen, 1);
  const r = post({ token: 'secret', ops: [{ op: 'upsert', row: row('a') }, { op: 'upsert', row: row('b') }] });
  assert.deepStrictEqual(r.applied, ['a', 'b']);
  assert.deepStrictEqual(ids(sh), ['a', 'b']);
  assert.strictEqual(sh.cells[1][2], '2026-11-20');
  assert.strictEqual(sh.cells[1][8], 45.5);
  assert.strictEqual(sh.formats['2:3'], '@', 'дата — текст');
  assert.strictEqual(sh.formats['3:11'], '@', 'обновлено — текст');

  // Повтор — без дублей; правка — та же строка.
  post({ token: 'secret', ops: [{ op: 'upsert', row: row('a', { weight_kg: 50, exercise: 'Жим лёжа' }) }] });
  post({ token: 'secret', ops: [{ op: 'upsert', row: row('a', { weight_kg: 50, exercise: 'Жим лёжа' }) }] });
  assert.deepStrictEqual(ids(sh), ['a', 'b']);
  assert.strictEqual(sh.cells[1][8], 50);
  assert.strictEqual(sh.cells[1][5], 'Жим лёжа');

  // Удаление (идемпотентно), в том числе отсутствующей строки.
  post({ token: 'secret', ops: [{ op: 'upsert', row: row('c') }, { op: 'upsert', row: row('d') }] });
  const del = post({ token: 'secret', ops: [{ op: 'delete', entry_id: 'b' }, { op: 'delete', entry_id: 'c' }, { op: 'delete', entry_id: 'zzz' }] });
  assert.deepStrictEqual(del.applied, ['b', 'c', 'zzz']);
  assert.deepStrictEqual(ids(sh), ['a', 'd']);
  post({ token: 'secret', ops: [{ op: 'delete', entry_id: 'b' }] });
  assert.deepStrictEqual(ids(sh), ['a', 'd']);

  // Смешанный пакет: правка + удаление + добавление + добавить-и-удалить.
  const before = sh.stats.setValues;
  post({
    token: 'secret',
    ops: [
      { op: 'upsert', row: row('d', { reps: 8 }) },
      { op: 'delete', entry_id: 'a' },
      { op: 'upsert', row: row('e') },
      { op: 'upsert', row: row('f') },
      { op: 'delete', entry_id: 'f' },
      { op: 'upsert', row: row('e', { reps: 5 }) },
    ],
  });
  assert.deepStrictEqual(ids(sh), ['d', 'e']);
  assert.strictEqual(sh.cells[1][7], 8);
  assert.strictEqual(sh.cells[2][7], 5);
  assert.ok(sh.stats.setValues - before <= 2, 'запись пакетами, а не по ячейке');

  // Полная выгрузка дважды — без дублей.
  const all = Array.from({ length: 250 }, (_, i) => ({ op: 'upsert', row: row('h' + i) }));
  post({ token: 'secret', ops: all });
  post({ token: 'secret', ops: all });
  assert.strictEqual(sh.cells.length - 1, 252);
  assert.strictEqual(new Set(ids(sh)).size, 252);
}

// Мусор вместо JSON
{
  const { post, raw } = load();
  assert.deepStrictEqual(raw('не json'), { ok: false, error: 'bad_request' });
  assert.deepStrictEqual(post({ token: 'secret', ops: 'не массив' }), { ok: true, applied: [], sheet: 'Журнал' });
}

console.log('Code.gs: все проверки пройдены');
