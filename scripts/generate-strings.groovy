// Reads strings.properties and generates target/generated-sources/songscribe/Strings.java.
// Runs during the generate-sources Maven phase via gmavenplus-plugin.

import java.util.regex.Pattern

def KEY_REGEX = Pattern.compile('[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*')

def propsFile = new File("${project.basedir}/src/main/resources/songscribe/strings.properties")
def outDir = new File("${project.build.directory}/generated-sources/songscribe")
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
new File(outDir, 'Strings.java').withWriter('UTF-8') { out ->
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
