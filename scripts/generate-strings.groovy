// Reads strings.properties and generates target/generated-sources/songscribe/Strings.java.
// Runs during the generate-sources Maven phase via gmavenplus-plugin.

import java.util.regex.Pattern

def KEY_REGEX = Pattern.compile('[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*')

def propsFile = new File("${project.basedir}/src/main/resources/songscribe/strings.properties")
def outDir = new File("${project.build.directory}/generated-sources/songscribe")
def outFile = new File(outDir, 'Strings.java')

if (outFile.exists() && outFile.lastModified() >= propsFile.lastModified()) {
    println "Strings.java is up to date — skipping generation."
    return
}

outDir.mkdirs()

def props = new Properties()
propsFile.withReader('UTF-8') { props.load(it) }

// Validate keys and build constant -> key mapping
def constantToKey = [:]

props.stringPropertyNames().sort().each { key ->
    if (!KEY_REGEX.matcher(key).matches()) {
        System.err.println("ERROR: Invalid key format in strings.properties: '${key}'")
        System.err.println("       Keys must match: [a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*")
        System.exit(1)
    }

    def constant = key.toUpperCase().replace('.', '_')

    if (constantToKey.containsKey(constant)) {
        System.err.println("ERROR: Key collision in strings.properties:")
        System.err.println("       '${constantToKey[constant]}' and '${key}' both map to '${constant}'")
        System.exit(1)
    }

    constantToKey[constant] = key
}

// Generate Strings.java
outFile.withWriter('UTF-8') { out ->
    out.println '// This is an auto generated code. DO NOT MODIFY!'
    out.println 'package songscribe;'
    out.println ''
    out.println 'import java.text.MessageFormat;'
    out.println 'import java.util.ResourceBundle;'
    out.println ''
    out.println 'public final class Strings {'
    out.println '    private static final ResourceBundle BUNDLE ='
    out.println '        ResourceBundle.getBundle("songscribe.strings");'
    out.println ''

    constantToKey.keySet().sort().each { constant ->
        def key = constantToKey[constant]
        out.println "    public static final String ${constant} = \"${key}\";"
    }

    out.println ''
    out.println '    private Strings() {}'
    out.println ''
    out.println '    public static String get(String key) {'
    out.println '        return BUNDLE.getString(key);'
    out.println '    }'
    out.println ''
    out.println '    public static String get(String key, Object... args) {'
    out.println '        return MessageFormat.format(BUNDLE.getString(key), args);'
    out.println '    }'
    out.println '}'
}

println "Generated Strings.java with ${constantToKey.size()} constants."

// Audit: check that every constant is referenced somewhere in the Java sources.
// Keys with these prefixes are intentionally unreferenced (test fixtures, etc.).
def EXEMPT_PREFIXES = ['test.', 'month.']

def sourceDirs = [
    new File("${project.basedir}/src/main/java"),
    new File("${project.basedir}/src/test/java"),
]

def allSource = new StringBuilder()

sourceDirs.each { dir ->
    dir.eachFileRecurse { file ->
        if (file.name.endsWith('.java')) {
            allSource.append(file.getText('UTF-8'))
        }
    }
}

def sourceText = allSource.toString()
def deadKeys = []

constantToKey.each { constant, key ->
    if (EXEMPT_PREFIXES.any { key.startsWith(it) }) {
        return
    }

    if (!sourceText.contains("Strings.${constant}")) {
        deadKeys << key
    }
}

if (deadKeys) {
    System.err.println("ERROR: ${deadKeys.size()} dead string key(s) found in strings.properties:")
    deadKeys.sort().each { System.err.println("  ${it}") }
    System.err.println("\nRemove these keys or add references to them.")
    System.exit(1)
}
