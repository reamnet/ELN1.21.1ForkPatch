# Agents

If you learn things about making the Gradle build work in the sandbox, document it under the Sandbox section.

# Various Notes

* This is a Minecraft Forge 1.7.10 mod called Electrical Age (Eln). It uses an updated GTNH Gradle build system.
* We are aiming to move to 100% Kotlin. Where possible, use Kotlin for new code.
* Java source files belong in the `src/main/java` directory. Kotlin source files belong in the `src/main/kotlin` directory.
* Do not update the Gradle configuration or wrapper without asking first. They are from an upstream repository.
* If you create documentation, please add it do the `docs` directory.
* Do not move the `id` or `subId` of existing registered items or blocks unless explicitly asked. Those values are world-save sensitive and changing unrelated ones breaks existing saves.
* **i18n (internationalization) workflow**:
  - Wrap all user-facing strings with `tr()` and item/block names with `TR_NAME()`.
  - Avoid dynamically constructing strings; keep them as complete phrases so translators can work with them.
  - Use positional parameters (`%1$`, `%2$`, …) for runtime values instead of string concatenation or direct variable embedding — this allows translators to reorder arguments for their language.
  - After adding new items or translatable strings, run `./gradlew generateLangFiles` to update the language files. Do not manually edit language files unless fixing an existing translation.
* The javac deprecation/unchecked warnings reported from `build/rfg/minecraft-src/java` were already investigated. They come from generated/decompiled patched Minecraft/Forge sources, not Electrical Age source. Running `compilePatchedMcJava` with `-Xlint:deprecation` and `-Xlint:unchecked` shows details such as deprecated Forge progress APIs and raw collection usage in Minecraft classes. There is not a meaningful durable fix in this repository because `build/rfg/minecraft-src` is generated from an external source; suppress, isolate, or leave diagnostic lint opt-in rather than trying to patch those generated files.

# Sandbox

* On Windows, run the 1.21.1 build with a full JDK 21 in JAVA_HOME. A launcher-provided JRE can start Gradle but NeoForm's Minecraft recompilation fails because javax.tools.JavaCompiler is absent. Gradle's provisioned JDK under its user-home jdks directory works. `generateLangFiles` regenerates translation keys using the existing Java/Kotlin source parser.

* When `./gradlew` fails inside the sandbox claiming it “Could not determine a usable wildcard IP”, rerun with `GRADLE_USER_HOME=$PWD/.gradle` and, if needed, request escalation—the build otherwise succeeds once it can write its cache and bind to an IP.

* Managed cloud machines may provide only a Java 21 JRE. Use a full JDK 21 for NeoForm recompilation. In proxy-only environments Java also needs explicit `http.proxyHost`/`https.proxyHost` and port properties, and a truststore containing the environment CA. Keep TLS verification enabled and keep local tool installations/caches outside tracked repository files.

* In NeoForge's layered JUnit runtime, Kotlin `assertFailsWith` can hit a `KClassImpl` / `ClassBasedDeclarationContainer` classloader conflict while checking an otherwise correct exception. Use JUnit's equivalent `Assert.assertThrows(ExceptionType::class.java) { ... }` for exception assertions in that runtime; the expected exception and behavior must still be checked.

* Native sound checks on a cloud machine with no audio device can use `ALSOFT_DRIVERS=null`. OpenAL Soft then initializes the `No Output` device, so real sound loading/play events can be checked without a speaker. Keep this scoped to automated headless runs; omit it for normal audible gameplay.

* For simultaneous native multiplayer checks, prepare the server run first and launch its Java process separately while Gradle runs the client. The generated `serverLegacyClasspath.txt` alone omits the development Minecraft artifact: include `build/moddev/artifacts/neoforge-<version>.jar`, supply `fml.modFolders`, and install the Kotlin language/runtime components and any required client-matching mods (such as CC: Tweaked) in the isolated server's mods directory. Normal Gradle `runServer` supplies its runtime dependencies automatically. Bind the test server to localhost and keep these files under ignored `run/` paths.
