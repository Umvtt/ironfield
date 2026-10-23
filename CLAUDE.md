# Ironfield: notes for Claude

Ironfield is a top-down, War Thunder-style browser game, built as one self-contained HTML file.

## Layout
- `web/index.html`: the whole game: HTML, CSS and JS in one file. This is what players load. Keep it a single file with no build step.
- `android/`: a small Java WebView app (no AndroidX). It loads the game from GitHub Pages, falls back to the bundled copy offline, and checks GitHub Releases (tags `apk-N`) for a newer APK.
- `.github/workflows/publish-game.yml`: deploys `web/` to GitHub Pages on every push that touches `web/**`.
- `.github/workflows/build-apk.yml`: builds and signs the APK and publishes a release. Runs on pushes to `android/**` or by hand. Needs the secrets `ANDROID_KEYSTORE_B64` and `ANDROID_KEYSTORE_PASSWORD`.

## Game systems (all in web/index.html)
- **Damage model:** War Thunder-style. There are no hit points. Crew and modules (engine, transmission, fuel, ammo rack, breech, turret drive) get hit. Shells check armor along their path, then apply spall and filler. The ammo types are AP, APCBC, APHE, HVAP, APDS, APFSDS, HEAT, HESH and HE.
- **Vehicles:** `ROSTER` is built with `V(nation, class, tier, id, name, stats)` for ground vehicles and `A(...)` for aircraft. Nations are usa, ger, ussr, uk and fra. Ground classes are light, medium, heavy, td, spg and aa; air classes are fighter and bomber.
- **Aircraft:** planes sit in the same `tanks` array with `t.air` set. Flight is `updPlane`, AI is `planeAI`, weapons are bullets, rockets and bombs (`updAirShells`), and damage is `planeHit` / `planePost`. Tank guns only hit planes with `alt < .45`. AA flak uses a proximity fuze (`T.fz`).
- **Waves:** handled by `startWave` and `pickEnemy` / `pickPlane` / `pickAA`. Wrecks clear when a wave is won. Milestone checkpoints are at waves 5, 10, 20 and so on. Between waves the squad votes to continue.
- **Co-op:** PeerJS star topology, with the host authoritative for up to 14 players. Peers live in `net.peers` and the host's view of all players is `humans()`. Snapshots (`sendSnapshot`) are per-guest JSON strings over the `raw` serialization. Every network input is clamped (`num`, `cleanName`, `cleanText`); never put remote text into innerHTML. The map size scales with player count (`mapDims`).
- **Saves:** localStorage key `ironfield-v4`, holding `{active, slots[3], coop, name, lastCode}`. Co-op has its own profile that starts at 0 RP.
- **Rules:** co-op has no nuke and no admin unlocks, and you can't leave mid-battle. French vehicles explode randomly when the host's setting is on (on by default).

## Working on it
- After edits, check the script parses: extract the `<script>` block and run `node --check` on it.
- Test in a headless browser (Playwright) on desktop and on a phone viewport (390×844 and 844×390), and check for page errors.
- Balance changes should be checked with quick simulations, not guesses.
- The owner speaks English (Turkish, lives in Turkey), plays on desktop and phone, and plays co-op with friends.
