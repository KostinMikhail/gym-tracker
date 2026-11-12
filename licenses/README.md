# Шрифты

Все шрифты в `app/src/main/res/font/` взяты из репозитория Google Fonts
(https://github.com/google/fonts, каталог `ofl/`) и распространяются по лицензии
SIL Open Font License 1.1 — тексты лицензий лежат рядом (`*-OFL.txt`).

| Файл | Шрифт |
|---|---|
| ibm_plex_mono_*.ttf | IBM Plex Mono |
| pixelify_sans.ttf | Pixelify Sans (variable) |
| jetbrains_mono.ttf | JetBrains Mono (variable) |
| unbounded.ttf | Unbounded (variable) |
| exo2.ttf | Exo 2 (variable) |
| russo_one.ttf | Russo One |
| jura.ttf | Jura (variable) |
| rubik_mono_one.ttf | Rubik Mono One |
| oswald.ttf | Oswald (variable) |

Изменение: в `pixelify_sans.ttf` в таблицу cmap добавлены кириллические «О» (U+041E) и «П» (U+041F),
указывающие на существующие глифы латинской O и греческой Π (в исходном шрифте эти две буквы отсутствовали).
У шрифта нет зарезервированного имени (Reserved Font Name), поэтому OFL это допускает.
