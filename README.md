# GTNH Metal Improvements

A client-side Forge 1.7.10 optimization companion for GT New Horizons, with particular attention to macOS / Apple Silicon.

## Scope

This mod **does not implement a Metal renderer**. [Angelica](https://github.com/GTNewHorizons/Angelica) already provides an experimental SDL GPU / Metal rendering backend, and is the recommended path for real Metal rendering. This project avoids replacing the rendering pipeline and instead makes small, independent improvements.

The initial version provides:

- **Particle list budgeting**: reduce the number of active vanilla particle effects held and processed by each of the four particle layers (default: 3072 versus vanilla's 4000). Optional distance-based rejection and adaptive caps are off by default. Some effects may be omitted under heavy particle loads.
- **Background FPS limiter**: reduce client CPU/GPU consumption when the window is hidden or unfocused (default ~20 FPS, configurable). This does not change the single-player server tick rate.
- **Optional animated-atlas throttling**: update texture animations less frequently to reduce atlas update work. **Off by default** because it slows animated textures.
- **Low-overhead F3 instrumentation**: render frame average/p95, Java heap consumption and rejected-particle count; optional, off by default.
- **Client-only mixins**: no registration changes to blocks, entities, recipes, tile entities or world data.

All optimizations are configurable in `config/xt9ymetal.cfg`. Most changes have visual or background-power trade-offs, so benchmarking is essential. No FPS gain, absence of all compatibility issues, or true Metal use is guaranteed.

## Install

1. Use Minecraft **1.7.10 Forge / GTNH** with the pack's **UniMixins** already installed (GTNH supplies it).
2. Get the JAR from a **successful GitHub Actions build artifact** in this repository (or build it with Gradle).
3. Copy **only the built mod JAR** into your instance's `mods/` folder. Do not copy source ZIPs or debug/development JARs.
4. Launch and edit `config/xt9ymetal.cfg` if desired. Restart to apply configuration changes.

This is **client-side**: a server installation is unnecessary. A client connecting to an unmodified GTNH server can use it.

## Build

The repository uses the same GTNH convention plugin as other modern GTNH 1.7.10 mods, requiring a compatible Gradle 9.x installation and JDK 25+ on the build machine. Run:

```bash
gradle build
```

GitHub Actions builds and uploads the JAR automatically. The initial bootstrap does not bundle a binary Gradle wrapper; `gradle` must be installed for local builds. The CI workflow installs the pinned Gradle version.

## Performance and compatibility

- On **macOS M-series**, use Angelica and its SDL GPU Metal option if your specific GTNH version supports it. The option is experimental and requires compatible LWJGL3ify.
- Angelica already performs chunk, particle, HUD and draw-call optimizations. This mod does **not** override Angelica's renderer, and injected hooks avoid overwriting target methods. Nevertheless, two mods can still conflict: test in a backup instance before using it in an established world.
- Test one scene with equal resolution, graphics settings and render distance; compare F3 frametime and heap at steady state. Client startup and world loading are different workloads.
- For strict visual parity, set `maxPerLayer=4000` and keep `maxDistanceBlocks=0`, `adaptiveBudget=false`, `animationTickInterval=1`.
- Java heap figures are **not** GPU/Metal memory usage. No driver-level Metal profiling is implemented.

## Reference stack

- https://github.com/GTNewHorizons/Angelica
- https://github.com/GTNewHorizons/ExampleMod1.7.10
- https://github.com/xt9y-org/GTNH-SmallAddons

Version: initial development bootstrap. The project needs a successful CI build and GTNH/Mac hardware validation before a release is labeled stable.
