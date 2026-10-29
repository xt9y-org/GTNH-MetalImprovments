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

GitHub Actions builds and uploads the JAR automatically. The Gradle wrapper is included; use `./gradlew build` locally with Java 25, or download the GitHub Release.

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


## Benchmarking and Apple Silicon (v0.3.0)

This release adds low-overhead rolling frame interval and CPU render tick averages/P95/P99, direct-buffer estimates, GC totals, and Angelica frame-pacer wait samples to the optional F3 diagnostics. **CPU tick time and frame interval are not GPU execution time**. A rolling frame interval includes scheduling, pacing and other application work. The in-game debug overlay can itself affect performance; turn it off during benchmarks.

**Repeatable benchmark:** in your GTNH world, set FPS to Unlimited and disable VSync, wait for chunks to settle, and type `/metalbench start 60`. Keep the camera still for 60 seconds. The mod records wall frame intervals and CPU render tick times and writes a CSV to `logs/xt9ymetal-bench-*.csv`. Repeat at the same position on OpenGL and SDL GPU Metal, with matching mods and settings. `/metalbench status` and `/metalbench stop` are also available. Each benchmark writes a separate CSV. During benchmarks avoid opening F3/JourneyMap overlays or moving the camera.

**Opt-in SDL GPU experiments:** in `config/xt9ymetal-boot.properties` you can add `sdlFramesInFlight=1`, `2`, or `3` (default `0`, meaning leave Angelica's own default of 2 untouched). Optionally set `disablePresenterThread=true`. These pass through Angelica's **documented startup properties**, without redirecting graphics calls or changing render passes. Fewer frames in flight may reduce latency but reduce throughput; more may improve throughput but increase memory and input lag. Neither option is enabled by default; measure on your own GPU. Remove settings to restore Angelica defaults.

**Tile memory:** Apple tile-based GPUs benefit from fewer render-pass breaks and correct load/store actions. SDL GPU's attachment lifetimes and render-pass structure belong to Angelica; changing them blindly in our mod risks corrupting depth, overlays, HUD and post-processing. Relevant Angelica counters include `renderPassGeneration`, `copyPassesThisFrame`, `inPassClearsThisFrame` and `uploadFlushSubmitsThisFrame`. Optimizing these requires backend profiling and source-level validation upstream. Reference: https://developer.apple.com/documentation/apple-silicon/porting-your-metal-code-to-apple-silicon


v0.3.0: reproducible benchmark command, rolling CPU/wall-frame P95/P99, memory diagnostics and opt-in Angelica Metal startup tuning. No changes to Metal render-pass storage or attachment lifetimes.


## One-command profiling (v0.4.0)

Open a world and enter **`/metalbench`**. No restart, JVM arguments, FPS cap change, or configuration edit is required. The command warms the scene for 5 seconds, then collects 60 seconds of wall-frame intervals, CPU render ticks, Angelica's CPU wait/presentation values, heap/direct buffer and GC changes. On SDL GPU backends, an optional read-only mixin samples render-pass counts and break reasons **before Angelica resets those counters**, and transfer/clear/submit events. It saves one `logs/xt9ymetal-bench-YYYYMMDD-HHmmss.csv`. Use `/metalbench status` or `/metalbench stop` as needed, and `/metalbench start 90` for a longer capture.

The command **does not switch the renderer**. Angelica selects OpenGL or Metal during initialization, and changing it safely requires restarting Minecraft. One capture profiles whichever backend is currently running. The report notes VSync/framerate-cap conditions and camera movement. The capture is observational: no graphics state or attachment load/store operations are changed. Render-pass instrumentation only runs when recording on Angelica's SDL GPU backend, and is gracefully skipped when not available. CPU wait samples are **not** GPU execution times.

For tile-based Apple GPUs, the diagnostic pass-end breakdown can identify excess render-target switching, unnecessary clears, copy transitions, upload-flushes, and readbacks. Only after observing those counters can a targeted change in Angelica safely eliminate redundant pass boundaries and memory traffic. No speculative memoryless attachment changes are enabled.


v0.4.0 release: one-command /metalbench (five-second warmup, 60-second capture, CSV with CPU and frame timings, memory, GC and Angelica SDL GPU pass/clear/copy/submit counters).

## v0.4.1 benchmark improvements

`/metalbench` temporarily overrides Angelica's FPS cap and disables the effective VSync mode when possible; the original VSync mode is restored after the benchmark, including `/metalbench stop`. It does not write Minecraft video options or change Angelica's backend. The CSV also includes time-series samples of heap, direct buffers, GC count and GC duration (once per second) to help associate stalls with Java memory pressure. If a driver caps the frame rate independently, the report still reflects the observed FPS. The existing Metal backend cannot switch to OpenGL without restarting the game.

Release v0.4.1: uncapped one-command capture with temporary Angelica VSync override/restoration, plus a per-second GC and JVM memory timeline.

## v0.4.2: automatic foreground-only benchmarking

`/metalbench` automatically pauses whenever Minecraft is unfocused, invisible or minimized and resumes when the window returns to the foreground. These periods are excluded from the benchmark timer and FPS/P99 statistics, so the existing background FPS limiter cannot contaminate the result. The CSV records the number and duration of focus pauses. No settings changes or extra commands are needed; Minecraft needs 60 seconds of foreground time to complete its default capture.

The prior v0.4.1 recording had about 24 seconds near 16 FPS followed by 36 seconds near 419 FPS, consistent with background throttling. Three Java GC collections totaled 205 ms around seconds 24–28. Metal had approximately two render passes per frame and no extra copy passes, so no speculative tile-memory attachment change has been enabled.
