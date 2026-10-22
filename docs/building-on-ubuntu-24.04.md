# Building on Ubuntu 24.04

Use a full Java 21 JDK and the repository's Gradle wrapper. Building on a headless
server does not require a desktop, Minecraft server installation, or running game.
An internet connection is needed to download the build tools and dependencies.

```bash
sudo apt update
sudo apt install -y git openjdk-21-jdk-headless
export JAVA_HOME="/usr/lib/jvm/java-21-openjdk-$(dpkg --print-architecture)"
export PATH="$JAVA_HOME/bin:$PATH"

git clone --branch main https://github.com/reamnet/ELN1.21.1ForkPatch.git
cd ELN1.21.1ForkPatch
./gradlew --no-daemon --max-workers=2 build
```

The mod jar is `build/libs/ElectricalAge-3.0.37.jar`. The build also runs the
unit tests. The first build downloads Minecraft tooling and takes longer than
subsequent builds. Gradle is configured with a 4 GB heap and the Kotlin compiler
with a 3 GB heap; allow additional memory for the operating system and build tools.

To rebuild this exact release instead of the current `main`:

```bash
git fetch --tags origin
git switch --detach v3.0.37
./gradlew --no-daemon --max-workers=2 build
```

The version is set by `modVersion` in `gradle.properties`. Each update increments
it, so building 3.0.37 keeps an existing `ElectricalAge-3.0.36.jar` beside the new
jar in `build/libs/`. Rebuilding the same version replaces that version's jar.
Use `build` to keep previous jars; `clean` deletes the entire build directory.

For a separately named local build, you can override the version without editing
files:

```bash
./gradlew --no-daemon --max-workers=2 build -PmodVersion=3.0.37-local.1
```

That produces `build/libs/ElectricalAge-3.0.37-local.1.jar` and sets the same version
inside the mod. See [release versions](release-versions.md) for the numbered
updates and their source tags.

To update an existing checkout to the current `main` and compile again:

```bash
git switch main
git pull --ff-only origin main
./gradlew --no-daemon --max-workers=2 build
```

Keep `JAVA_HOME` and `PATH` set as above in each new shell, or add those exports
to your shell profile. If Gradle reports a Java compiler is missing, check
`"$JAVA_HOME/bin/javac" -version`; it must be a JDK, not only a JRE. If the wrapper
is not executable after copying the repository, run `chmod +x gradlew`.

Stop Minecraft before replacing the previous Electrical Age jar in `mods/`.
Use matching jars on the clients and server. This build targets Minecraft 1.21.1,
NeoForge 21.1.249 and Kotlin for Forge 5.12.0 (the NeoForge edition). Kotlin for Forge
is installed separately; it is not bundled into the Electrical Age jar.
