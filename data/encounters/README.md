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
edited without touching or rebuilding the code. The file format is described once the loader exists.
