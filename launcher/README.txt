PLANESNOMAD - TEST BUILD
========================

To play: double-click Planesnomad.bat.

- Needs Java 17 or newer (https://adoptium.net, "JDK 17" or later). If it's missing or too old,
  you'll get a message saying so.
- Each time it starts, it checks GitHub for a newer test build and asks before updating.
  Without internet it simply starts the version you have.
- Card pictures are downloaded from Scryfall the first time they're needed (cards may show as
  text frames for a moment).

Updates are safe to accept:
- They come only from the project's GitHub releases page, over https.
- Each download is checked against the size and SHA-256 checksum published with it; a damaged
  or changed download is thrown away and nothing is installed.
- The new version is unpacked next to the old one and only switched to when complete. The
  previous version is kept: if a new build fails to start, you're offered the previous one.

Bug reports: please attach logs\game.log (what the game printed during the last session).

Folder contents:
  Planesnomad.bat   start here
  launcher.jar      checks for updates, then starts the game
  versions\         the installed game (current and previous version)
  current.txt       which version is in use
  logs\             game.log from the last session

To stop update checks, create an empty file named no-update-check.txt in this folder.
