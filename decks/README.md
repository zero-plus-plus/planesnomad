# Decks for testing

Put Forge deck files (`.dck`) here. They appear in the viewer's **Dev → Match setup** deck lists
(press **Reload decks folder** after adding files). In the game itself, your deck will come from
the run and the enemy's from the encounter; this folder is for testing.

A `.dck` file is plain text:

```
[metadata]
Name=My test deck
[Main]
4 Lightning Bolt|M10
4 Goblin Raider|10E
16 Mountain
```

Each line is `count name`, optionally `|SET` (and `|SET|[collector number]`) for a printing.
Decks made in Forge's own deck editor can be copied here as they are. Another folder can be used
with `-Dplanesnomad.decks=<folder>`.
