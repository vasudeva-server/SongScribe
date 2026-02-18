# SongScribe Development Commands

## Building and Compilation

### Quick Compile
```bash
./scripts/compile.sh
```
Compiles Java and Kotlin sources without full Maven build.

### Full Build
```bash
mvn clean package
```
Full Maven build with dependency packaging and jpackage generation.

## Running

### Development Mode (with debug logging)
```bash
./scripts/run-debug.sh
```

### Production Mode
```bash
./scripts/run.sh
```

### From JAR
```bash
java -jar target/SongScribe-*.jar
```

### Quick Iteration
```bash
./scripts/compile.sh && ./scripts/run-debug.sh
```

## Testing

### Run all tests
```bash
mvn test
```

### Run specific test class
```bash
mvn test -Dtest=ClassName
```

## Code Inspection

### View dependency tree
```bash
mvn dependency:tree
```

### Verify Java version
```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25 2>/dev/null || /usr/libexec/java_home)
java -version
```

## Git Operations

Always use the `/commit-commands:commit` skill to create commits rather than manual git commands.

### Check status
```bash
git status
```

### View recent commits
```bash
git log --oneline -10
```

## Environment Setup

Set Java 25 as default for the session:
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25 2>/dev/null || /usr/libexec/java_home)
```
