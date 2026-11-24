# Encounters

Enemy files, one per enemy, by plane and difficulty:

```
data/encounters/<plane>/<tier>/<enemy>.json
```

- `<plane>`: alara, invasion, kamigawa, lorwyn, masques, mirage, mirrodin, odyssey, onslaught,
  ravnica, shadowmoor, tempest, time-spiral, urzas, zendikar
- `<tier>`: easy, hard, elite, boss

Naming rules (so paths work the same on Windows, Mac and Linux, and in scripts):
- lowercase only
- words joined with `-`, no spaces (e.g. `time-spiral`, `merfolk-wader.json`)
- no apostrophes or other punctuation (`urzas`, not `urza's`)

These files are game data, not code: they live here (not under `src/main/java`) so they can be
edited without touching or rebuilding the code.

## The file format

One JSON object per file. `//` comments are allowed. Everything except `name` and `deck` is
optional. When the game starts (and from the viewer's Dev menu > Encounters > Reload encounter
files) every file is checked; mistakes are listed with the file and the field, e.g.
`lorwyn/easy/merfolk-tidecaller [deck]: no card named "Merfolk Tricker"`.

| Field | What it is | Example |
|---|---|---|
| `version` | file format version (always 1 for now) | `1` |
| `name` | the enemy's name (shown on screen and in the log; must be unique) | `"Merfolk Tidecaller"` |
| `subtitle` | short line under its name | `"Merfolk lords and counterspells"` |
| `notes` | free text for the team, ignored by the game | |
| `images.icon` | its portrait: `card:<card name>` (that card's art) or `file:<path>` (a picture, path from the game folder) | `"card:Lord of Atlantis"` |
| `images.background` | its half of the table, same choices | `"file:data/playmats/fallback-you.jpg"` |
| `life` | starting life (default 20) | `20` |
| `firstPlayer` | `player`, `enemy` or `random` (default) | `"player"` |
| `deck` | its deck: one `"count card name"` per entry, optionally `\|SET` for a printing | `"4 Lord of Atlantis\|4ED"` |
| `effects` | its passive effects: our own effect cards (command zone), shown in its effects bar | `["Undertow Ward"]` |
| `guarantees` | cards it is sure to draw on one of its own turns; shown face up with a "T2" badge | `[{"card": "Lord of Atlantis", "drawOnTurn": 2}]` |
| `start.enemy.hand` | preset opening hand, **taken from the deck**; no mulligan | `["Island", "Counterspell"]` |
| `start.enemy.battlefield` | bonus permanents from the start, **not** from the deck: card names or `token:<Forge token script>` | `["token:u_1_1_merfolk"]` |
| `start.player.hand`, `start.player.battlefield` | the same for the player, for this fight only | |
| `barks` | what it says in a speech bubble by its portrait: `when` (`start`, `lifeBelowHalf`, `playerLifeBelowHalf`), `text`, `once` (default true) | `[{"when": "lifeBelowHalf", "text": "..."}]` |
| `rewards` | for winning: `gold`, `cardChoices` (how many cards offered, pick one), `cardPool` (offered from), `relics` | `{"gold": 25, "cardChoices": 3, ...}` |
| `drops` | cards it drops, to be added to the card rewards (not used yet) | `["Cursecatcher"]` |

The plane and difficulty come from the folder the file is in. Token scripts are the file names in
`forge-engine/resources/tokenscripts` (without `.txt`). A new kind of bark or start rule needs a
small code change once; after that every file can use it.

Example: `lorwyn/easy/merfolk-tidecaller.json`. To fight it: `fight-merfolk.bat` in the repository
folder (after `mvn compile`), or `fight-merfolk.bat <plane>/<tier>/<name>` for another file.
