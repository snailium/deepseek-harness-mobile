plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
}

/**
 * The architecture seam, enforced rather than documented.
 *
 * The rule this module exists for: **the presentation layer must not reach the data layer.**
 * `ui/theme` and `ui/components` are where this fork's visual divergence lives, and the whole
 * point of keeping them separate is that an upstream merge can rebase them without having to
 * decide, hunk by hunk, which half of a change is "functional" and which is "visual".
 *
 * That boundary decays the moment it is only a convention. It did decay: by the time the fork was
 * split for upstream, `SessionStore.kt` alone carried four functional changes interleaved with
 * cosmetic ones, and the only way to separate them was to read ninety hunks and judge each one.
 * A rule nobody can break mechanically is worth more than a rule everybody agrees to.
 *
 * `ui/theme` and `ui/components` are therefore forbidden from importing `data/`, `SessionStore`,
 * `ui/media`, or the Hilt/DI surface. A composable that needs session state takes it as a
 * parameter — that is what makes it previewable and testable, and it is also what makes it
 * *unable* to absorb a functional change.
 *
 * Deliberately still allowed:
 * - `com.labteto.dshmobile.R` — string and font resources are presentation, not data.
 * - `core.*` DTOs and the Markdown parser — pure value types and pure functions, no session.
 */
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // `compileOnly` throughout: a lint check runs inside AGP's lint process, which already has
    // lint-api and Kotlin on its classpath. Bundling them would produce a jar that fights the
    // host's copies at load time.
    compileOnly("com.android.tools.lint:lint-api:31.7.3")
    compileOnly("com.android.tools.lint:lint-checks:31.7.3")
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.0.21")

    testImplementation("com.android.tools.lint:lint:31.7.3")
    testImplementation("com.android.tools.lint:lint-tests:31.7.3")
    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.10.2")
}

tasks.withType<Test>().configureEach {
    // `LintDetectorTest` extends the JUnit3 `TestCase`, so the vintage engine is what actually
    // discovers it; plain `useJUnit()` finds the class and then reports "No tests found in …".
    useJUnitPlatform {
        includeEngines("junit-vintage")
    }
}
