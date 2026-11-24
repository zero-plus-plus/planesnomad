# Planesnomad themes — guide for designers

The match screen is built from **swappable elements**: panels, pop-ups, badges, buttons,
frames, icons, colors and fonts. A **theme** is a folder that replaces any of them with your
own art. No programming needed: a text file plus pictures (and fonts, if you like).

```
themes/
  default/              the built-in look; theme.properties lists EVERY setting, with comments
  fantasy-example/      an example: leather, wood, gold frames, serif fonts
  my-theme/             yours
    theme.properties
    frames/  textures/  icons/  fonts/      (any folder names you like)
```

## Quick start

1. Copy `themes/fantasy-example` (or `default`) to `themes/my-theme`.
2. In `theme.properties`, change `name = ...`.
3. Start the viewer, open the **Theme** menu and pick your theme.
4. Edit pictures or settings, then press **F5** (Theme → Reload theme files): the screen
   updates without restarting.
5. If something is wrong (misspelled setting, missing picture, bad color, font not
   installed), a message lists it when the theme loads.

A theme only needs the settings it changes. Everything else comes from `default`
(`extends = default`, or another theme's folder name to build on that one).

## Values

| Kind | Example | Notes |
|---|---|---|
| color | `#E3B341`, `#80000000` | `#AARRGGBB`: AA is opacity (`00` clear … `FF` solid). `none` = no color |
| size | `10` | pixels at a 1280×720 window; everything scales with the window |
| picture | `frames/popup.png` | PNG (transparency kept) or JPG, relative to the theme folder |
| font | `fonts/Cinzel.ttf` or `Book Antiqua, Georgia, Serif` | a font file, or installed fonts: the first one found is used |

Comments start with `#` at the start of a line, or after a value with spaces before it
(`color.text = #E8E4D8   # main text`).

## Elements

Every element below can be drawn from a **picture** or from **fill + border + rounding**.
Settings for an element called `popup`:

```
popup.image       = frames/popup.png
popup.mode        = slice          # slice | stretch | cover | tile
popup.slice       = 24,24,24,24    # slice: corner sizes in the picture (top,left,bottom,right)
popup.sliceScale  = 1.0            # slice: corner size on screen (1.0 = picture pixels at 1280x720)
popup.fill        = #15181D        # background; with a picture, none unless you set one
popup.border      = #2C323B        # with a picture, none unless you set one
popup.borderWidth = 2
popup.radius      = 6              # corner rounding; "pill" = round ends
popup.text        = #E8E4D8        # text color on it (where text is drawn on the element)
```

**Modes**

- `slice` (9-slice; the default): the four corners keep their size, the edges stretch in one
  direction and the middle in both. One frame picture fits panels of any size. Make the
  corners (ornaments, rivets, gems) fit inside the `slice` sizes.
- `stretch`: the whole picture stretched to fit (fine for buttons, rings, badges).
- `cover`: fills the area keeping proportions; the overflow is cropped (backgrounds, art).
- `tile`: repeated (textures such as leather, wood, felt). `tileScale` sets the tile size.

| Element | What it is | Picture tips |
|---|---|---|
| `table.you`, `table.enemy` | each half of the table (when a player has no playmat) | `tile` textures or `cover` art |
| `hand` | strip behind each hand | translucent; keep it dark so cards stand out |
| `panel` | left column: player panels | `tile` texture |
| `column` | right column: history bar, game log | `tile` texture |
| `topbar` | top bar: relics, turn, controls | `tile` or `slice` |
| `popup` | hover pop-ups (player info, history) | `slice` frame; also `popup.padding` (inner space), `popup.sideBorder` (border in the player's color, default on without a picture) |
| `inspect` | text panel beside an inspected card | `slice` frame |
| `choice` | panel in the middle of the board showing cards to decide about (scry: top or bottom; surveil) | `slice` frame |
| `phases` | the column of phase icons left of the battlefield (enemy's turn on top, yours below; gold ring = stop) | `tile` texture or plain fill |
| `prompt` | the bar above your hand while you play ("Your turn: play something, or pass [Pass]"), with its buttons | `slice` frame; `prompt.padding` (inner space); the buttons use the `button` elements |
| `stack` | the stack pop-up on the board | `slice` frame |
| `badge.life` | life total on the portrait | `slice` or `stretch`; text via `badge.life.text` |
| `badge.count` | counts on piles and history ("×200") | small `slice` pill |
| `badge.selected` | selection on a pile ("37/200") | |
| `bubble` | an enemy's speech bubble (its barks), at the board's left edge pointing at its portrait; `bubble.text` = text color | `slice` frame (the tail uses `bubble.fill`) |
| `reward.backdrop` | dims the board behind the reward screen (after a won fight) | plain fill with alpha |
| `reward` | the reward screen's panel; `reward.text` = title color | `slice` frame |
| `reward.item`, `reward.item.hover`, `reward.item.claimed` | one reward row (gold, card choice, relic) | `slice` |
| `badge.target` | "TARGET" on a card or portrait that a stack item aims at, and the "→ Grizzly Bears" line on stack cards (arrows and crosshairs use `color.you` / `color.enemy`) | small `slice` pill |
| `tag` | "Ability" / "Trigger" labels on the stack | |
| `button`, `button.hover`, `button.pressed`, `button.disabled` | top bar buttons | four pictures of the same size, `slice` |
| `relic.slot` | circle behind relic pictures | usually just `relic.slot.fill` |
| `relic.frame`, `consumable.frame` | drawn **over** relic / consumable icons | a ring with a transparent middle, `stretch`; `relic.frame.extend = 0.12` (how far past the icon) |
| `consumable.empty` | an empty consumable slot | |

## Colors, fonts, icons

All settings are listed with their built-in values in `themes/default/theme.properties`:

- `color.*`: table, panels, text, accent, your side / the enemy's, life, attack and block outlines, relic glow, …
- `color.history.*`: the discs of the history symbols (attack, death, trigger, …)
- `card.frame.*`: frame colors of cards drawn without a picture
- `font.body`, `font.heading` (names, titles, bold labels), `font.numbers` (life, counts), `font.scale`
- `icon.history.<kind>`: a picture replacing a history symbol (square, transparent background). Kinds: cast, ability, trigger, land, attack, block, death, exile, bounce, tokens, damage, gameEnd
- `icon.collapse`, `icon.expand`: the top bar's arrow buttons
- `icon.reward.gold`, `icon.reward.cards`: pictures for the gold and card rewards (default: a drawn coin, the offered cards fanned); `icon.reward.relic` is drawn behind a relic reward's own icon
- `icon.phase.<phase>`: the phase column's icons. Phases: upkeep, draw, main1, combatBegin, attackers, blockers, firstStrike, damage, combatEnd, main2, end, cleanup (firstStrike and cleanup only appear while they happen). Draw them in white or light colors: they are shown dim, gold (stop) or on the player's color (now)

## Checking a theme without the game window

Screenshots of the whole screen in a theme (a short AI match, both window sizes):

```
java -Djava.awt.headless=true -Dplanesnomad.theme=my-theme ... com.planesnomad.gui.ScreenshotTool out/
```

The viewer can also start in a theme: `-Dplanesnomad.theme=my-theme`.

The phase column's states use the theme colors: `color.you` / `color.enemy` (the phase now),
`color.accent` (stops), `color.textDim` (other phases).

## Not themable yet

- Where things are on the screen (layout, sizes of rows and columns)
- Card pictures themselves (they come from card images), and the frame drawn for cards without a picture (colors only)
- Swing's own menus (right-click menus, menu bar), dialogs (relic confirmation, "how many"), the speed list
  and the check box in the top bar - these will be replaced by themed versions
- Sounds and animations

Ask the programmers if your design needs any of these.

## Licenses

Only use art and fonts you may ship (your own work, or licensed for the game, e.g. SIL
Open Font License fonts). Put a `CREDITS.txt` in the theme folder for anything not made by you.
The pictures in `fantasy-example` were drawn by code (`ExampleThemeAssets`) as placeholders.
