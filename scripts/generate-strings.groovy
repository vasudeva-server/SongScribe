// Reads strings.properties and generates build/generated-sources/songscribe/Strings.java.
// Bindings: basedir (project root), buildDir (build output directory).

import java.util.regex.Pattern

def RG = System.getenv('RG') ?: '/opt/homebrew/bin/rg'

def KEY_REGEX = Pattern.compile('[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*')

def propsFile = new File("${basedir}/src/main/resources/songscribe/strings.properties")
def outDir = new File("${buildDir}/generated-sources/songscribe")
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
        throw new RuntimeException("Invalid key format in strings.properties: '${key}'\nKeys must match: [a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*")
    }

    def constant = key.toUpperCase().replace('.', '_')

    if (constantToKey.containsKey(constant)) {
        throw new RuntimeException("Key collision in strings.properties: '${constantToKey[constant]}' and '${key}' both map to '${constant}'")
    }

    constantToKey[constant] = key
}

// Generate Strings.java
def constants = constantToKey.keySet().sort().collect { constant ->
    "    public static final String ${constant} = \"${constantToKey[constant]}\";"
}.join('\n')

def template = new File("${basedir}/scripts/templates/Strings.java.template")
outFile.withWriter('UTF-8') { out ->
    out.write(template.getText('UTF-8').replace('{{CONSTANTS}}', constants))
}

println "Generated Strings.java with ${constantToKey.size()} constants."

// Audit: check that every constant is referenced somewhere in the Java sources.
// Keys with these prefixes are intentionally unreferenced (test fixtures, etc.).
def EXEMPT_PREFIXES = ['test.', 'month.']

def rgProc = [RG, '--no-filename', '--no-line-number', '-o',
              'Strings\\.[A-Z][A-Z0-9_]+',
              "${basedir}/src"]
             .execute()
def rgOut = new StringBuilder()
def rgErr = new StringBuilder()
rgProc.waitForProcessOutput(rgOut, rgErr)
def referenced = rgOut.toString().readLines().toSet()

def deadKeys = constantToKey.findAll { constant, key ->
    !EXEMPT_PREFIXES.any { key.startsWith(it) } && !referenced.contains("Strings.${constant}".toString())
}.values().sort()

if (deadKeys) {
    throw new RuntimeException("${deadKeys.size()} dead string key(s) found in strings.properties:\n${deadKeys.collect { "  $it" }.join('\n')}\n\nRemove these keys or add references to them.")
}
