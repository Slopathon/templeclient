# Temple Client

Source for Temple.nz client 0.4.0, a Fabric utility client for anarchy servers.

The original author (nx09, temple.nz) posted the 0.4.0 source publicly and stopped working on it. This repo is that source plus a working Gradle build. The original release did not include build files.

## Changes from the released source

- License check removed: `LicenseGuard`, `AntiTamper`, `IntegrityGuard` and the startup lockout are gone. The client starts without a temple.nz session.
- Server payload delivery removed. Every module the client used to download from the license server is already in the source, and they all ship in the jar.
- The jar protection tooling (`buildSrc`: string encryption, protected jar checks) has been removed.
- Added `settings.gradle`, `stonecutter.gradle`, `build.gradle`, `gradle.properties` and the Gradle wrapper.

Config library and suggestions still talk to `verify.temple.nz` and need a session file at `%APPDATA%\.temple\session`. Without one they just report that no session is active.

## Supported versions

| Minecraft | Status |
|---|---|
| 26.2 | builds, tests pass |
| 26.3 | does not build: current 26.3 moved `com.mojang.blaze3d` to `com.mojang.renderpearl` |
| 1.21.11 | does not build: the 1.21.11 branch is far behind the 26.x code |

The project uses [Stonecutter](https://stonecutter.codeberg.page/) for multi-version source (`//? if >=26 {` comments). Version settings live in `versions/<mc>/gradle.properties`. Only 26.2 is enabled in `settings.gradle`. To work on another version, add it to the `versions` list there.

## Requirements

- JDK 25 (for example [Temurin 25](https://adoptium.net/temurin/releases/?version=25))
- Internet access on the first build, to download Gradle, Minecraft, Fabric Loader and Fabric API

You do not need to install Gradle. The wrapper (`gradlew` / `gradlew.bat`) downloads the correct version (9.6.1).

## Building

Windows:

```
gradlew.bat build
```

Linux / macOS:

```
chmod +x gradlew
./gradlew build
```

The mod jar ends up in:

```
versions/26.2/build/libs/temple-client-0.4.0+26.2.jar
```

Drop it into your `mods` folder next to Fabric API (0.160.0+26.2 or newer) on Fabric Loader 0.19.5 or newer.

If Gradle picks the wrong Java, point it at JDK 25 by doing one of these:

- Set `JAVA_HOME` to the JDK 25 folder before running the wrapper.
- Add `org.gradle.java.home=C:/path/to/jdk-25` to `gradle.properties`.

## Useful Gradle commands

| Command | What it does |
|---|---|
| `gradlew build` | compile, run tests, build the jar |
| `gradlew build -x test` | build the jar without running tests |
| `gradlew test` | run the unit tests only |
| `gradlew :26.2:runClient` | start a dev Minecraft client with the mod loaded |
| `gradlew clean` | delete build output |
| `gradlew --stop` | stop the Gradle daemon |

The first build takes a few minutes while Loom downloads and prepares Minecraft. Later builds are much faster.

## Source layout

| Path | Contents |
|---|---|
| `src/main` | shared code: modules, settings, config, events |
| `src/client` | client code, mixins, rendering, GUI, assets |
| `src/test` | JUnit tests |
| `src/compileStubs` | Baritone API stubs, compile only |
| `src/shim-1.21.11` | 26.x API shims for the 1.21.11 build |
| `src/smoke`, `src/trailerAgent` | the author's dev test and trailer recording harness, not wired into this build |
| `tools/` | Python scripts that generate cosmetic models and sounds |
| `brand/` | logos |

## License

The original release came with no license file and `fabric.mod.json` says `All-Rights-Reserved`. All credit for the client goes to its original author.
