// Reads FlatLaf.properties and generates build/generated-sources/songscribe/ui/FlatLafKeys.java.
// Only keys with the "SongScribe." prefix are included.
// Bindings: basedir (project root), buildDir (build output directory).

import java.util.regex.Pattern

def RG = System.getenv('RG') ?: '/opt/homebrew/bin/rg'

def PREFIX = 'SongScribe.'
def PROPS_FILE = new File("${basedir}/src/main/resources/songscribe/FlatLaf.properties")
def OUT_DIR = new File("${buildDir}/generated-sources/songscribe/ui")
def OUT_FILE = new File(OUT_DIR, 'FlatLafKeys.java')

if (OUT_FILE.exists() && OUT_FILE.lastModified() >= PROPS_FILE.lastModified()) {
    println "FlatLafKeys.java is up to date — skipping generation."
    return
}

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
        throw new RuntimeException("Key collision in FlatLaf.properties: '${constantToKey[constant]}' and '${key}' both map to '${constant}'")
    }

    constantToKey[constant] = key
}

// Generate FlatLafKeys.java
def constants = constantToKey.toSorted().collect { constant, key ->
    "    public static final String ${constant} = \"${key}\";"
}.join('\n')

def template = new File("${basedir}/scripts/templates/FlatLafKeys.java.template")
OUT_FILE.withWriter('UTF-8') { out ->
    out.write(template.getText('UTF-8').replace('{{CONSTANTS}}', constants))
}

println "Generated FlatLafKeys.java with ${constantToKey.size()} constants."

// Audit: check that every constant is referenced somewhere in the Java sources.
def rgConstantsProc = [RG, '--no-filename', '--no-line-number', '-o',
                       'FlatLafKeys\\.[A-Z][A-Z0-9_]+',
                       "${basedir}/src"]
                      .execute()
def rgConstantsOut = new StringBuilder()
rgConstantsProc.waitForProcessOutput(rgConstantsOut, new StringBuilder())
def referencedConstants = rgConstantsOut.toString().readLines().toSet()

def rgRawKeysProc = [RG, '--no-filename', '--no-line-number', '-o',
                     'SongScribe\\.[\\w.]+',
                     "${basedir}/src"]
                    .execute()
def rgRawKeysOut = new StringBuilder()
rgRawKeysProc.waitForProcessOutput(rgRawKeysOut, new StringBuilder())
def referencedRawKeys = rgRawKeysOut.toString().readLines().toSet()

def deadKeys = constantToKey.findAll { constant, key ->
    !referencedConstants.contains("FlatLafKeys.${constant}".toString()) && !referencedRawKeys.contains(key.toString())
}.values().sort()

if (deadKeys) {
    throw new RuntimeException("${deadKeys.size()} dead FlatLaf key(s) found in FlatLaf.properties:\n${deadKeys.collect { "  $it" }.join('\n')}\n\nRemove these keys or add references to them.")
}
