import java.time.LocalDate
import java.time.format.DateTimeFormatter
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension

plugins {
    java
    jacoco
    id("net.ltgt.errorprone") version "4.2.0"
    id("info.solidsoft.pitest") version "1.19.0"
}

group = "SongScribe"
version = "2.0.0"

val buildDate: String = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
val generatedSourcesDir = layout.buildDirectory.dir("generated-sources")

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

sourceSets {
    main {
        java {
            srcDir(generatedSourcesDir)
        }
    }
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("org.swinglabs:swing-layout:1.0.3")
    implementation("net.java.dev.jna:jna:5.18.1")
    implementation("net.java.dev.jna:jna-platform:5.18.1")
    implementation("com.formdev:flatlaf:3.7.1")
    implementation("com.formdev:flatlaf-extras:3.7.1")
    implementation("com.intellij:forms_rt:7.0.3")
    implementation("org.jetbrains:annotations:26.1.0")
    implementation("org.jspecify:jspecify:1.0.0")
    compileOnly("com.uber.nullaway:nullaway-annotations:0.12.11")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("net.engio:mbassador:1.3.2")

    testImplementation("org.mockito:mockito-core:5.21.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.junit.platform:junit-platform-launcher:1.11.4")
    testImplementation("org.junit.platform:junit-platform-console:1.11.4")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("org.assertj:assertj-swing-junit:3.17.1")
    // Logback is runtimeOnly for the app (which codes against the SLF4J API), but
    // log-capture tests reference logback types directly, so it must also be on
    // the test compile classpath.
    testImplementation("ch.qos.logback:logback-classic:1.5.32")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.32")

    errorprone("com.google.guava:guava:33.5.0-jre")
    errorprone("com.google.errorprone:error_prone_core:2.42.0")
    errorprone("com.uber.nullaway:nullaway:0.12.11")
}

// -- Code generation --

val generateVersion by tasks.registering {
    description = "Generates Version.java, which contains constants for the public version (from project.version) and build version (current date). This allows us to display version info in the UI and have it accessible at runtime without hardcoding it in the source code."
    val outDir = generatedSourcesDir.map { it.dir("songscribe") }
    outputs.dir(outDir)
    doLast {
        val dir = outDir.get().asFile
        dir.mkdirs()
        File(dir, "Version.java").writeText(
            """
            // This is an auto generated code. DO NOT MODIFY!
            package songscribe;
            public final class Version {
                public static final String PUBLIC_VERSION = "${project.version}";
                public static final String BUILD_VERSION = "$buildDate";
                private Version() {}
            }
            """.trimIndent()
        )
    }
}

val generateStrings by tasks.registering {
    description = "Generates Strings.java, which contains constants for all user-facing strings in SongScribe. This allows us to keep all strings in a single properties file for easier localization and maintenance, while still having compile-time constants in the codebase."
    inputs.file("src/main/resources/songscribe/strings.properties")
    val outDir = generatedSourcesDir.map { it.dir("songscribe") }
    outputs.dir(outDir)
    doLast {
        groovy.lang.GroovyShell(groovy.lang.Binding(mapOf(
            "basedir" to projectDir.absolutePath,
            "buildDir" to layout.buildDirectory.get().asFile.absolutePath
        ))).evaluate(file("scripts/generate-strings.groovy"))
    }
}

val generateFlatLafKeys by tasks.registering {
    description = "Generates FlatLafKeys.java, which contains constants for all FlatLaf UI defaults keys used in SongScribe. This allows us to avoid hardcoding string literals throughout the codebase and have a single source of truth for these keys."
    inputs.file("src/main/resources/songscribe/FlatLaf.properties")
    val outDir = generatedSourcesDir.map { it.dir("songscribe/ui") }
    outputs.dir(outDir)
    doLast {
        groovy.lang.GroovyShell(groovy.lang.Binding(mapOf(
            "basedir" to projectDir.absolutePath,
            "buildDir" to layout.buildDirectory.get().asFile.absolutePath
        ))).evaluate(file("scripts/generate-flatlaf-keys.groovy"))
    }
}

tasks.named("compileJava") {
    dependsOn(generateVersion, generateStrings, generateFlatLafKeys)
}

// -- Compiler configuration --

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf(
        "-XDcompilePolicy=simple",
        "--should-stop=ifError=FLOW",
    ))
    options.errorprone {
        disableAllChecks = true
        error("NullAway")
        option("NullAway:OnlyNullMarked", "true")
        option("NullAway:JSpecifyMode", "true")
        disableWarningsInGeneratedCode = true
    }
}

// -- JAR configuration --

tasks.named<Jar>("jar") {
    archiveFileName = "SongScribe.jar"
    manifest {
        attributes["Main-Class"] = "songscribe.SongScribe"
        attributes["Implementation-Build"] = "${project.version}.$buildDate"
    }
    doFirst {
        manifest.attributes["Class-Path"] = configurations.runtimeClasspath.get()
            .files.joinToString(" ") { "libs/${it.name}" }
    }
}

// -- Test configuration --

val addOpensArgs = listOf(
    "--add-opens", "java.desktop/javax.swing=ALL-UNNAMED",
    "--add-opens", "java.desktop/javax.swing.plaf.basic=ALL-UNNAMED",
    "--add-opens", "java.desktop/java.awt=ALL-UNNAMED",
    "--add-opens", "java.base/java.lang=ALL-UNNAMED",
    "--add-opens", "java.base/java.util=ALL-UNNAMED",
)

fun Test.applyCommonTestConfig() {
    useJUnitPlatform()
    // Always re-run when invoked — test.sh is interactive, stale-output skipping is not useful here.
    outputs.upToDateWhen { false }
    jvmArgs(addOpensArgs)
    jvmArgs("--enable-native-access=ALL-UNNAMED", "-XX:+EnableDynamicAgentLoading", "-Xshare:off")

    if (System.getProperty("os.name", "").startsWith("Mac")) {
        jvmArgs(
            "-Dapple.laf.useScreenMenuBar=true",
            "-Dapple.awt.application.appearance=system",
        )
    }

    // Isolate preferences from the real per-user prefs.json so tests that reset
    // or overwrite prefs (RecentDocumentsManagerTest, PrefsTest) never clobber it.
    systemProperty(
        "songscribe.prefsDir",
        layout.buildDirectory.dir("test-prefs").get().asFile.absolutePath
    )
    systemProperty("e2e.failFast", if (project.hasProperty("noFailFast")) "false" else "true")

    if (project.hasProperty("e2eDebug")) {
        systemProperty("e2e.debug", "true")
    }

    System.getenv("EXTRA_JVM_ARGS")?.takeIf { it.isNotBlank() }?.split(" ")?.let { jvmArgs(it) }
    // Gradle auto-enables JaCoCo on all Test tasks; disable it here so that coverage.sh
    // is the sole agent injector (double agents cause SIGABRT via conflicting ASM transforms).
    configure<JacocoTaskExtension> { isEnabled = false }
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
    addTestListener(object : TestListener {
        override fun beforeSuite(suite: TestDescriptor) {}
        override fun afterSuite(suite: TestDescriptor, result: TestResult) {
            if (suite.parent == null) {
                val summary = buildString {
                    append("\nResults: ${result.successfulTestCount} passed")

                    if (result.failedTestCount > 0) {
                        append(", ${result.failedTestCount} FAILED")
                    }

                    if (result.skippedTestCount > 0) {
                        append(", ${result.skippedTestCount} skipped")
                    }
                }

                println(summary)
            }
        }
        override fun beforeTest(testDescriptor: TestDescriptor) {}
        override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {}
    })
}

tasks.named<Test>("test") {
    applyCommonTestConfig()
    exclude("**/e2e/**")
    // Unit tests run headlessly — suppress Dock icon and foreground activation.
    if (System.getProperty("os.name", "").startsWith("Mac")) {
        jvmArgs("-Dapple.awt.UIElement=true")
    }
}

val e2eTest by tasks.registering(Test::class) {
    description = "Runs e2e tests"
    group = "verification"
    applyCommonTestConfig()
    include("**/e2e/**")
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
}

// -- PIT mutation testing --

val defaultPitestTargets = listOf(
    "songscribe.converter.*",
    "songscribe.dom.*",
    "songscribe.export.*",
    "songscribe.font.*",
    "songscribe.io.*",
    "songscribe.layout.*",
    "songscribe.midi.*",
    "songscribe.prefs.*",
    "songscribe.smufl.*",
    "songscribe.util.*",
)

// Allow mutation-test.sh to narrow targets at runtime:
//   ./gradlew pitest -PpitestTarget=songscribe.util.* -PpitestTargetTests=songscribe.util.*
val pitestTarget: String? by project
val pitestTargetTests: String? by project

pitest {
    pitestVersion.set("1.24.0")
    junit5PluginVersion.set("1.2.3")
    targetClasses.set(pitestTarget?.split(",") ?: defaultPitestTargets)
    targetTests.set(pitestTargetTests?.split(",") ?: listOf("songscribe.*"))
    excludedClasses.set(listOf("songscribe.Strings", "songscribe.Version", "songscribe.ui.FlatLafKeys"))
    // e2e tests are excluded because they require a live UI.
    // GraphicUtilsClampTest: clampToScreen queries live screen devices even in headless mode.
    excludedTestClasses.set(listOf(
        "songscribe.e2e.*",
        "songscribe.util.GraphicUtilsClampTest",
    ))
    outputFormats.set(setOf("HTML", "XML"))
    timestampedReports.set(false)
    threads.set(Runtime.getRuntime().availableProcessors())
    // Child minion JVMs that run the tests need the same flags as the test task.
    // PIT requires single-token form (--add-opens=m/p=ALL-UNNAMED) for its --jvmArgs list.
    jvmArgs.addAll(listOf(
        "-Djava.awt.headless=true",
        "--add-opens=java.desktop/javax.swing=ALL-UNNAMED",
        "--add-opens=java.desktop/javax.swing.plaf.basic=ALL-UNNAMED",
        "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--enable-native-access=ALL-UNNAMED",
        "-XX:+EnableDynamicAgentLoading",
        "-Xshare:off",
        "-Dapple.laf.useScreenMenuBar=true",
        "-Dapple.awt.application.appearance=system",
    ))
}

// -- Classpath printing for scripts --

tasks.register("printClasspath") {
    doLast {
        print(sourceSets["main"].runtimeClasspath.asPath)
    }
}

// -- JaCoCo coverage --

jacoco {
    toolVersion = "0.8.14"
}

tasks.named<JacocoReport>("jacocoTestReport") {
    // coverage.sh injects the agent manually and appends all runs to *.exec files here.
    executionData.setFrom(fileTree(layout.buildDirectory.dir("jacoco")) {
        include("*.exec")
    })
    reports {
        xml.required = true
        html.required = true
        csv.required = false
    }
    classDirectories.setFrom(
        fileTree(layout.buildDirectory.dir("classes/java/main")) {
            exclude(
                "songscribe/Strings.class",
                "songscribe/**/*Test.class"
            )
        }
    )
}

tasks.register("printJacocoAgentPath") {
    description = "Print Jacoco agent path"
    doLast {
        val wrapperJar = configurations["jacocoAgent"].singleFile
        val extractDir = layout.buildDirectory.dir("jacoco").get().asFile
        extractDir.mkdirs()
        val agentJar = File(extractDir, "${wrapperJar.nameWithoutExtension}-agent.jar")

        if (!agentJar.exists()) {
            zipTree(wrapperJar).matching { include("jacocoagent.jar") }.singleFile.copyTo(agentJar)
        }

        print(agentJar.absolutePath)
    }
}
