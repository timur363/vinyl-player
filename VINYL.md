# VinylTune

Personal build of [KittyTune](https://github.com/alan7383/kittytune) by alan7383
(GPL-3.0), with extra features on top.

- Installs next to KittyTune (app id `io.github.timur363.vinyl`).
- Release builds are optimized with R8 (upstream ships unoptimized builds).
- Every push to `main` is built, launched on an emulator as a crash check and
  published under Releases; the in-app updater follows this repository.

Signing key: `signing/vinyl-release.jks` (kept in the repo on purpose so every
build can update the previous one).

## Roadmap

0. Speed and own builds
1. Yandex Music source
2. Vinyl mode: spinning record, needle crackle, spin-up / spin-down, 33 / 45 / 78
3. Scratching the record with a finger
4. Presets (speed + EQ) and per-track settings
