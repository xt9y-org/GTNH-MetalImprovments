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


## Automatic Angelica Metal selection on macOS (experimental)

On RFB-enabled GTNH launchers, a RetroFuturaBootstrap plugin ordered before Angelica requests Angelica's **SDL GPU** backend with **metal** as the driver via the existing Angelica startup system properties. This is macOS-only; no Metal API is injected into OpenGL and no rendering backend is duplicated. Angelica must already include its SDL GPU backend, and the instance must have sufficiently recent LWJGL3ify plus the SDL native libraries (Angelica checks requirements).

- Default on macOS: `autoEnableMetal=true`. To disable, create `config/xt9ymetal-boot.properties` with `autoEnableMetal=false` and restart.
- Explicit launcher JVM properties `-Dangelica.sdlgpu.enable` and `-Dangelica.sdlgpu.driver` **take priority** over the mod.
- A marker `config/xt9ymetal-metal-starting` is created before the first attempted Metal startup. The first rendered frame checks the actual active backend and removes it. If the renderer fell back to OpenGL, the mod disables further automatic attempts. If launch crashes before that point, the next run requests OpenGL and creates `config/xt9ymetal-metal-disabled`. To retry after fixing dependencies, delete that **disabled** marker.
- **Limitations:** In legacy launchers without RetroFuturaBootstrap, the older Forge coremod fallback may still run too late for Angelica. Verify the actual active renderer in Angelica's settings. This does not guarantee an FPS gain or Metal activation. Failure *after* the first frame is not detected by the recovery marker.
- For the most predictable fallback behavior, configure SDL GPU + Metal directly in Angelica's UI and let Angelica's own startup guard manage it. Forcing the system property overrides Angelica's normal startup guard; the marker above is a separate recovery mechanism.

## Publish a release

GitHub Actions `Release tagged build` publishes a **single installable JAR** for version tags (e.g. `v0.2.0`). Go to **Actions → Publish GTNH Metal Improvements release → Run workflow**, supply a new version tag, and the workflow compiles and creates the GitHub Release. It also accepts pushed `v*` tags. Builds use Java 25/Gradle 9.4, but the mod targets Minecraft 1.7.10 / Java 8-compatible bytecode.

Release process: an explicit main-branch commit titled `release: vX.Y.Z` may also publish a prerelease; other pushes only build artifacts.

Release v0.2.0 includes the experimental macOS Metal autoselector and active-backend diagnostics.


### v0.2.1 startup diagnostics

The RFB plugin uses `loadBefore=angelica` to request the backend before Angelica reads its settings, on client-side macOS only. When `showF3Statistics=true`, F3 now shows the bootstrap request/result as well as the actual renderer. On a graceful fallback to OpenGL, automatic retry is disabled using `config/xt9ymetal-metal-disabled` (delete to retry). **This does not prove the device supports Metal; check the on-screen backend and console logs.**

Version v0.2.1: early RFB initialization before Angelica, verified backend activation, and startup recovery-marker improvements.

Version v0.2.2: reproducible tagged release build with early Metal selection and verified fallback reporting.
