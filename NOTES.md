# SQLite challenge notes

These are my notes on `sample.db` and the quirks I hit. They are for learning.
All offsets are counted from byte 0 of the file.

## 1. Setup quirks on this Mac

- **`sample.db` is a binary file.** IntelliJ cannot show it as a table without
  the paid Database Tools plugin. The IDE turns that plugin off when there is no
  license. The log line is `Marking plugins 'disabled': [com.intellij.modules.ultimate]`.
- **Free ways to see the data:**
  - `sqlite3 sample.db .dump` shows plain SQL text.
  - `xxd sample.db` shows the bytes.
  - The folder `.idea-view/` has both as text files. It is ignored by git
    (`.git/info/exclude`). Make them again with:
    ```bash
    sqlite3 sample.db .dump > .idea-view/sample.db.sql && xxd sample.db > .idea-view/sample.db.hex.txt
    ```
- **Java version.** `pom.xml` targets Java 25. JDK 25 is not installed here.
  `~/.zshrc` sets Java 8 as the default. Plain `./your_program.sh` failed with
  `Unrecognized option: --enable-preview`.
- **Fix.** `your_program.sh` now picks JDK 25 if it exists. If not, it uses
  JDK 21 and compiles for 21. This only affects local runs.
- **`java_home -v 25` is misleading.** It returns JDK 21 when 25 is missing. The
  script checks the real version string to avoid this.
- **A cached build can hide a compile error.** An old jar made one run look
  fine. Delete `/tmp/codecrafters-build-sqlite-java` to force a clean build.
- **`.dbinfo` is a command, not a file.** `./your_program.sh sample.db .dbinfo`
  gives the program two arguments: the database file and the command name.
- **Output rule.** The test reads stdout. Debug output (the byte dump) goes to
  stderr so it does not break the test.

## 2. Numbers: bytes, hex and other bases

- A **bit** is 0 or 1. A **byte** is 8 bits and holds 0 to 255.
- **Hex** has 16 digits: `0-9` and `a-f` (`a`=10 ... `f`=15). Two hex digits
  write one byte. `00` is the smallest byte. `ff` is the largest (255).
- Each place is worth 16 to a power. The place on the right is 16^0 = 1. The
  next is 16^1 = 16, then 16^2 = 256, then 16^3 = 4096.
- Examples:
  - `345` hex = 3x256 + 4x16 + 5 = **837**
  - `fff` hex = 4095, which is `1000` hex minus 1
  - `ffff` hex = 65535
  - `0ec3` hex = 14x256 + 12x16 + 3 = 3779
- `0x` is a label that means "this number is hex". It is not part of the value.
- Other labels (Java, C, Python):

  | Label | Base | Example | Decimal |
  |---|---|---|---|
  | none | 10 | `345` | 345 |
  | `0x` | 16 | `0x345` | 837 |
  | `0b` | 2 | `0b1011` | 11 |
  | `0o` | 8 | `0o17` | 15 (Python, JavaScript, Rust only) |

- **zsh quirk.** zsh does not understand `0o`. It prints
  `bad math expression: operator expected at 'o345'`. Write `base#number`
  instead:
  ```bash
  echo $((16#345)) $((8#345)) $((2#1011))   # 837 229 11
  echo $((0x345))                            # 837
  ```
- **Text is numbers too.** ASCII gives each letter a number. `53` = `S`,
  `51` = `Q`, `4c` = `L`, `69` = `i`, `74` = `t`, `65` = `e`, `20` = space,
  `00` = end of text.
- **Big-endian.** SQLite writes the big end of a number first. `10 00` is
  0x1000 = 4096, not 16. `00 00 00 05` is 5.

## 3. How to read the hex dump

```
00000010: 10 00 01 01 00 40 20 20 00 00 00 05 00 00 00 04
```

- The left part (`00000010:`) is the **position** of the first byte on that
  line. It is not data.
- Each line has 16 bytes. The next line adds `0x10`.
- A page is 4096 bytes (`0x1000`). The first digits tell you the page:
  `0000-0fff` page 1, `1000-1fff` page 2, `2000-2fff` page 3, `3000-3fff` page 4.
- **Skip lines of all zeros.** They are empty space inside a page.
- Look for readable letters. For example `4f 72 61 6e 67 65` is `Orange`.

## 4. The file header (bytes 0 to 99)

| Position | Bytes in `sample.db` | Meaning | Value |
|---|---|---|---|
| 0-15 | `53 51 4c ... 33 00` | The text `SQLite format 3` and a zero byte | |
| 16-17 | `10 00` | Page size | 4096 |
| 18 | `01` | Write format version | 1 |
| 19 | `01` | Read format version | 1 |
| 20 | `00` | Unused bytes at the end of each page | 0 |
| 21-23 | `40 20 20` | Fixed values SQLite always writes | 64, 32, 32 |
| 24-27 | `00 00 00 05` | Change counter | 5 |
| 28-31 | `00 00 00 04` | Number of pages | 4 |
| 32-35 | `00 00 00 00` | First free-list page | none |
| 36-39 | `00 00 00 00` | Number of free-list pages | 0 |
| 40-43 | `00 00 00 02` | Schema cookie (goes up when tables change) | 2 |
| 44-47 | `00 00 00 04` | Schema format number | 4 |
| 48-51 | `00 00 00 00` | Default page cache size | not set |
| 52-55 | `00 00 00 00` | Largest root page (auto-vacuum) | not used |
| 56-59 | `00 00 00 01` | Text encoding | 1 = UTF-8 |
| 60-63 | `00 00 00 00` | User version | not set |
| 64-67 | `00 00 00 00` | Incremental vacuum mode | off |
| 68-71 | `00 00 00 00` | Application ID | not set |
| 72-91 | all zeros | Reserved | |
| 92-95 | `00 00 00 05` | Version-valid-for (matches the change counter) | 5 |
| 96-99 | `00 2e 4b 90` | SQLite version that wrote the file | 3034000 = 3.34.0 |

## 5. Pages and B-tree pages

- The file is 16,384 bytes. That is 4 pages of 4,096 bytes.
- **Page 1 holds the file header first** (bytes 0-99). Its B-tree page header
  starts at byte 100. Every other page has its B-tree header at its first byte.
- **B-tree page header (leaf table page = 8 bytes):**

  | Offset in header | Size | Meaning | Page 1 of `sample.db` |
  |---|---|---|---|
  | 0 | 1 | Page type. `0d` = leaf table page (holds rows) | `0d` |
  | 1-2 | 2 | First free block | `00 00` (none) |
  | 3-4 | 2 | **Number of cells (rows)** | `00 03` = 3 |
  | 5-6 | 2 | Where the cell data starts on the page | `0e c3` = 0x0ec3 |
  | 7 | 1 | Wasted bytes | `00` |

- Right after the header comes the **cell pointer list**. Each pointer is 2
  bytes and says where one cell starts. Page 1 has `0f 8f`, `0f 3d`, `0e c3`.
- **Cells grow backward from the end of the page.** Pointers grow forward from
  the header. The gap in the middle is free space, filled with zeros. This is
  why `Granny Smith` (row 1) is the last row in page 2.

## 6. What is on each page of `sample.db`

| Page | Starts at | Table | Rows | Header bytes |
|---|---|---|---|---|
| 1 | `0x0000` | `sqlite_schema` (the list of tables) | 3 | `0d 00 00 00 03 0e c3` |
| 2 | `0x1000` | `apples` | 4 | `0d 00 00 00 04 0f a1` |
| 3 | `0x2000` | `sqlite_sequence` | 2 | `0d 00 00 00 02 0f e7` |
| 4 | `0x3000` | `oranges` | 6 | `0d 00 00 00 06 0f 18` |

- `sqlite_sequence` exists because both tables use `autoincrement`.
- A table's root page is stored in its schema row: `apples` = 2,
  `sqlite_sequence` = 3, `oranges` = 4.

## 7. Reading one cell

**An apples row** at `0x1fa1`:

```
1a 04 | 04 00 2d 19 | 47 6f 6c 64 65 6e 20 44 65 6c 69 63 69 6f 75 73 | 59 65 6c 6c 6f 77
```

- `1a` is the row size (26 bytes). `04` is the row ID.
- `04 00 2d 19` is the record header. `04` is the header size (4 bytes).
  `00` is the `id` column (stored as the row ID, so it has no data bytes).
  `2d` = 45, and text length is (45 - 13) / 2 = 16 bytes.
  `19` = 25, and text length is (25 - 13) / 2 = 6 bytes.
- The data is `Golden Delicious` (16 bytes) and `Yellow` (6 bytes).

**A schema row** at `0x0ec3` (page 1, row 3):

- `78` is the size (120 bytes). `03` is the row ID.
- The record header lists the columns: `table`, `oranges`, `oranges`, a 1-byte
  number, and the SQL text.
- The number is `04`. It is the root page of `oranges`. The SQL text is
  `CREATE TABLE oranges ( ... )`.

## 8. Stage: print the number of tables

- Each table has one row in `sqlite_schema`. So the number of tables is the
  number of cells on page 1.
- The cell count is at **bytes 103 and 104** of the file (header at 100, plus 3).
- In `sample.db` the bytes are `00 03`. Read as big-endian that is **3**.
- The 3 tables are `apples`, `oranges` and `sqlite_sequence`. Check with:
  ```bash
  sqlite3 sample.db "SELECT name FROM sqlite_schema;"
  ```
- Expected output of `./your_program.sh sample.db .dbinfo`:
  ```
  database page size: 4096
  number of tables: 3
  ```
- Do not read the cells yet. Later stages do that.

## 9. Useful commands

```bash
./your_program.sh sample.db .dbinfo       # run the program
sqlite3 sample.db .dump                   # all data as SQL text
sqlite3 sample.db "PRAGMA page_size"      # 4096, to check your program
xxd sample.db | less                      # the raw bytes
echo $((0xffff))                          # hex to decimal in zsh
```
