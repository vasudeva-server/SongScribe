// Reads FlatLaf.properties and generates target/generated-sources/songscribe/ui/FlatLafKeys.java.
// Only keys with the "SongScribe." prefix are included.
// Runs during the generate-sources Maven phase via gmavenplus-plugin.

import java.util.regex.Pattern

def PREFIX = 'SongScribe.'
def PROPS_FILE = new File("${project.basedir}/src/main/resources/songscribe/FlatLaf.properties")
def OUT_DIR = new File("${project.build.directory}/generated-sources/songscribe/ui")
OUT_DIR.mkdirs()

// Parse keys manually — java.util.Properties doesn't handle FlatLaf's [dark] prefix syntax
def KEY_PATTERN = Pattern.compile('^\\s*(' + Pattern.quote(PREFIX) + '[\\w.]+)\\s*=')
def keys = [] as LinkedHashSet<String>

PROPS_FILE.eachLine('UTF-8') { line ->
    def matcher = KEY_PATTERN.matcher(line)

    if (matcher.find()) {
        keys.add(matcher.group(1))
    }
}

// Build constant name -> full key mapping
def constantToKey = new LinkedHashMap<String, String>()

keys.each { key ->
    // Strip "SongScribe." prefix, uppercase, dots to underscores
    def suffix = key.substring(PREFIX.length())
    def constant = suffix.toUpperCase().replace('.', '_')

    if (constantToKey.containsKey(constant)) {
        System.err.println("ERROR: Key collision in FlatLaf.properties:")
        System.err.println("       '${constantToKey[constant]}' and '${key}' both map to '${constant}'")
        System.exit(1)
    }

    constantToKey[constant] = key
}

// Generate FlatLafKeys.java
new File(OUT_DIR, 'FlatLafKeys.java').withWriter('UTF-8') { out ->
    out.println '// This is an auto generated code. DO NOT MODIFY!'
    out.println 'package songscribe.ui;'
    out.println ''
    out.println '/**'
    out.println ' * Constants for custom SongScribe.* keys defined in FlatLaf.properties.'
    out.println ' * Generated from {@code src/main/resources/songscribe/FlatLaf.properties}.'
    out.println ' */'
    out.println 'public final class FlatLafKeys {'
    out.println ''
    out.println '    private FlatLafKeys() {}'
    out.println ''

    constantToKey.toSorted().each { constant, key ->
        out.println "    public static final String ${constant} = \"${key}\";"
    }

    out.println '}'
}

println "Generated FlatLafKeys.java with ${constantToKey.size()} constants."

// Audit: check that every constant is referenced somewhere in the Java sources.
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
    // Check for FlatLafKeys.CONSTANT or the raw key string in UIManager calls
    if (!sourceText.contains("FlatLafKeys.${constant}") && !sourceText.contains("\"${key}\"")) {
        deadKeys << key
    }
}

if (deadKeys) {
    System.err.println("ERROR: ${deadKeys.size()} dead FlatLaf key(s) found in FlatLaf.properties:")
    deadKeys.sort().each { System.err.println("  ${it}") }
    System.err.println("\nRemove these keys or add references to them.")
    System.exit(1)
}
