import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Properties
import java.util.regex.Pattern
import net.ltgt.gradle.errorprone.errorprone

plugins {
    java
    id("net.ltgt.errorprone") version "4.2.0"
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
    maven("https://s3.eu-west-1.amazonaws.com/repo.maven.cyberduck.io/releases")
}

val nativeLibs by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

dependencies {
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("org.swinglabs:swing-layout:1.0.3")
    implementation("com.github.Dansoftowner:jSystemThemeDetector:3.6")
    implementation("com.github.oshi:oshi-core:6.10.0")
    implementation("net.java.dev.jna:jna:5.18.1")
    implementation("net.java.dev.jna:jna-platform:5.18.1")
    implementation("org.rococoa:rococoa-core:0.10.0")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.32")
    implementation("com.formdev:flatlaf:3.7.1")
    implementation("com.formdev:flatlaf-extras:3.7.1")
    implementation("com.intellij:forms_rt:7.0.3")
    implementation("org.jetbrains:annotations:26.1.0")
    implementation("org.jspecify:jspecify:1.0.0")
    compileOnly("com.uber.nullaway:nullaway-annotations:0.12.11")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("net.engio:mbassador:1.3.2")

    nativeLibs("org.rococoa:librococoa:0.10.0@dylib")

    testImplementation("org.mockito:mockito-core:5.21.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.junit.platform:junit-platform-launcher:1.11.4")
    testImplementation("org.junit.platform:junit-platform-console:1.11.4")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("org.assertj:assertj-swing-junit:3.17.1")

    errorprone("com.google.guava:guava:33.5.0-jre")
    errorprone("com.google.errorprone:error_prone_core:2.42.0")
    errorprone("com.uber.nullaway:nullaway:0.12.11")
}

// -- Code generation --

val generateVersion by tasks.registering {
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
    inputs.file("src/main/resources/songscribe/strings.properties")
    val outDir = generatedSourcesDir.map { it.dir("songscribe") }
    outputs.dir(outDir)
    doLast {
        val keyRegex = Pattern.compile("[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*")
        val propsFile = file("src/main/resources/songscribe/strings.properties")
        val props = Properties().apply { propsFile.reader(Charsets.UTF_8).use { load(it) } }

        val constantToKey = sortedMapOf<String, String>()
        for (key in props.stringPropertyNames().sorted()) {
            if (!keyRegex.matcher(key).matches()) {
                error("Invalid key format in strings.properties: '$key'\nKeys must match: [a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*")
            }
            val constant = key.uppercase().replace('.', '_')
            if (constantToKey.containsKey(constant)) {
                error("Key collision in strings.properties: '${constantToKey[constant]}' and '$key' both map to '$constant'")
            }
            constantToKey[constant] = key
        }

        val dir = outDir.get().asFile
        dir.mkdirs()
        File(dir, "Strings.java").bufferedWriter(Charsets.UTF_8).use { out ->
            out.appendLine("// This is an auto generated code. DO NOT MODIFY!")
            out.appendLine("package songscribe;")
            out.appendLine()
            out.appendLine("import java.text.MessageFormat;")
            out.appendLine("import java.util.ResourceBundle;")
            out.appendLine()
            out.appendLine("public final class Strings {")
            out.appendLine("    private static final ResourceBundle BUNDLE =")
            out.appendLine("        ResourceBundle.getBundle(\"songscribe.strings\");")
            out.appendLine()
            for ((constant, key) in constantToKey) {
                out.appendLine("    public static final String $constant = \"$key\";")
            }
            out.appendLine()
            out.appendLine("    private Strings() {}")
            out.appendLine()
            out.appendLine("    public static String get(String key) {")
            out.appendLine("        return BUNDLE.getString(key);")
            out.appendLine("    }")
            out.appendLine()
            out.appendLine("    public static String get(String key, Object... args) {")
            out.appendLine("        return MessageFormat.format(BUNDLE.getString(key), args);")
            out.appendLine("    }")
            out.appendLine("}")
        }

        println("Generated Strings.java with ${constantToKey.size} constants.")

        val exemptPrefixes = listOf("test.", "month.")
        val sourceDirs = listOf(file("src/main/java"), file("src/test/java"))
        val allSource = buildString {
            for (d in sourceDirs) {
                d.walkTopDown().filter { it.name.endsWith(".java") }.forEach { append(it.readText(Charsets.UTF_8)) }
            }
        }
        val deadKeys = constantToKey.filter { (constant, key) ->
            exemptPrefixes.none { key.startsWith(it) } && !allSource.contains("Strings.$constant")
        }.values.sorted()

        if (deadKeys.isNotEmpty()) {
            error("${deadKeys.size} dead string key(s) found in strings.properties:\n${deadKeys.joinToString("\n") { "  $it" }}\n\nRemove these keys or add references to them.")
        }
    }
}

val generateFlatLafKeys by tasks.registering {
    inputs.file("src/main/resources/songscribe/FlatLaf.properties")
    val outDir = generatedSourcesDir.map { it.dir("songscribe/ui") }
    outputs.dir(outDir)
    doLast {
        val prefix = "SongScribe."
        val propsFile = file("src/main/resources/songscribe/FlatLaf.properties")
        val keyPattern = Pattern.compile("^\\s*(${Pattern.quote(prefix)}[\\w.]+)\\s*=")
        val keys = linkedSetOf<String>()

        propsFile.forEachLine(Charsets.UTF_8) { line ->
            val matcher = keyPattern.matcher(line)
            if (matcher.find()) keys.add(matcher.group(1))
        }

        val constantToKey = linkedMapOf<String, String>()
        for (key in keys) {
            val constant = key.substring(prefix.length).uppercase().replace('.', '_')
            if (constantToKey.containsKey(constant)) {
                error("Key collision in FlatLaf.properties: '${constantToKey[constant]}' and '$key' both map to '$constant'")
            }
            constantToKey[constant] = key
        }

        val dir = outDir.get().asFile
        dir.mkdirs()
        File(dir, "FlatLafKeys.java").bufferedWriter(Charsets.UTF_8).use { out ->
            out.appendLine("// This is an auto generated code. DO NOT MODIFY!")
            out.appendLine("package songscribe.ui;")
            out.appendLine()
            out.appendLine("/**")
            out.appendLine(" * Constants for custom SongScribe.* keys defined in FlatLaf.properties.")
            out.appendLine(" * Generated from {@code src/main/resources/songscribe/FlatLaf.properties}.")
            out.appendLine(" */")
            out.appendLine("public final class FlatLafKeys {")
            out.appendLine()
            out.appendLine("    private FlatLafKeys() {}")
            out.appendLine()
            for ((constant, key) in constantToKey.toSortedMap()) {
                out.appendLine("    public static final String $constant = \"$key\";")
            }
            out.appendLine("}")
        }

        println("Generated FlatLafKeys.java with ${constantToKey.size} constants.")

        val sourceDirs = listOf(file("src/main/java"), file("src/test/java"))
        val allSource = buildString {
            for (d in sourceDirs) {
                d.walkTopDown().filter { it.name.endsWith(".java") }.forEach { append(it.readText(Charsets.UTF_8)) }
            }
        }
        val deadKeys = constantToKey.filter { (constant, key) ->
            !allSource.contains("FlatLafKeys.$constant") && !allSource.contains("\"$key\"")
        }.values.sorted()

        if (deadKeys.isNotEmpty()) {
            error("${deadKeys.size} dead FlatLaf key(s) found in FlatLaf.properties:\n${deadKeys.joinToString("\n") { "  $it" }}\n\nRemove these keys or add references to them.")
        }
    }
}

// -- Native libs --

val copyNativeLibs by tasks.registering(Copy::class) {
    from(nativeLibs)
    into(layout.buildDirectory.dir("native"))
    rename { "librococoa.dylib" }
}

tasks.named("compileJava") {
    dependsOn(generateVersion, generateStrings, generateFlatLafKeys, copyNativeLibs)
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

tasks.named<Test>("test") {
    useJUnitPlatform()
    jvmArgs(addOpensArgs)
    jvmArgs("--enable-native-access=ALL-UNNAMED", "-XX:+EnableDynamicAgentLoading", "-Xshare:off")
    exclude("**/e2e/**")
}

// -- Classpath printing for scripts --

tasks.register("printClasspath") {
    doLast {
        print(sourceSets["main"].runtimeClasspath.asPath)
    }
}

tasks.register("printTestClasspath") {
    doLast {
        print(sourceSets["test"].runtimeClasspath.asPath)
    }
}
