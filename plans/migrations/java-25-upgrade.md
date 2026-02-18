# Java 25 Upgrade Plan

## Overview

Upgrade SongScribe from Java 21 to Java 25 LTS. This is a low-risk upgrade - all features currently used (unnamed variables, pattern matching, switch expressions) are standard in Java 25.

**Estimated Duration:** 30-60 minutes
**Risk Level:** Low
**Breaking Changes:** None expected

## Prerequisites

- Java 25 must be installed on the system
- Verify installation: `/usr/libexec/java_home -v 25`
- Create backup: Current branch is `feature/vertical-layout`

## Files to Update

### Critical Files (Must Update)

1. **pom.xml** - 5 locations
   - Lines 20-21: Maven compiler properties
   - Line 419: Kotlin JVM target
   - Lines 454-455: Maven compiler plugin configuration
   - Change all `21` → `25`

2. **scripts/compile.sh** - Line 2
   - Change `/usr/libexec/java_home -v 21` → `-v 25`
   - Keep fallback to default JDK

3. **README** - Line 13
   - Currently says "Java 8" (outdated!)
   - Update to "Java 25 or higher"

4. **.claude/rules/development.md** - 4 locations
   - Line 13: Update "Java 21" → "Java 25"
   - Line 71: Update requirement statement
   - Line 74: Update example command `-v 21` → `-v 25`
   - Line 87: Update "Java 21+" → "Java 25+"

### Optional Files (Recommended)

5. **.idea/misc.xml** - Line 16
   - Change `languageLevel="JDK_21"` → `"JDK_25"`
   - IntelliJ will auto-sync from POM

6. **.idea/compiler.xml** - Line 12
   - Change `target="21"` → `"25"`

7. **.idea/kotlinc.xml** - Line 7
   - Change `jvmTarget` value from `"21"` → `"25"`

8. **plans/kotlin-migration.md** - Lines 112, 604
   - Historical document, low priority

## Implementation Sequence

### Step 1: Update Build Configuration

Update `pom.xml` in a single atomic change:
- Maven compiler source/target properties (lines 20-21)
- Kotlin JVM target (line 419)
- Maven compiler plugin config (lines 454-455)

**Critical:** All 5 references must be updated together for consistency.

### Step 2: Update Build Scripts

Update `scripts/compile.sh`:
- Change Java version lookup from 21 to 25
- Keep fallback behavior for development flexibility

### Step 3: Update Documentation

Priority order:
1. **README** - User-facing, currently very outdated (Java 8!)
2. **.claude/rules/development.md** - Developer guide with examples
3. **plans/kotlin-migration.md** - Historical reference (optional)

### Step 4: Update IDE Configuration (Optional)

Update IntelliJ IDEA configuration files:
- `.idea/misc.xml`
- `.idea/compiler.xml`
- `.idea/kotlinc.xml`

Note: IntelliJ will auto-detect from POM, but updating these prevents temporary confusion.

## Testing Strategy

### Test 1: Clean Build
```bash
mvn clean
./scripts/compile.sh
```
**Expected:** No compilation errors

### Test 2: Full Package Build
```bash
mvn clean package
```
**Verify:**
- Build SUCCESS
- JAR created in `target/`
- No new warnings

### Test 3: Application Launch
```bash
./scripts/run.sh
```
**Verify:**
- Application starts without errors
- UI renders correctly
- No Java version warnings

### Test 4: Development Mode
```bash
./scripts/run-dev.sh
```
**Verify:**
- Additional logging works
- No runtime exceptions

### Test 5: Feature Validation

Verify Java features still work:
- Pattern matching for instanceof
- Switch expressions with arrows
- Unnamed variables (`_`)
- Text blocks (`"""`)
- Records with methods

## Rollback Strategy

If issues occur:

```bash
# Revert all changes
git checkout HEAD -- pom.xml scripts/compile.sh .idea/ .claude/rules/development.md README

# Clean build
mvn clean

# Verify Java 21 still works
./scripts/compile.sh && ./scripts/run.sh
```

## Success Criteria

✅ Upgrade is complete when:
- All critical files reference Java 25
- `mvn clean package` completes successfully
- Application launches and runs without errors
- No regression in existing functionality
- Documentation accurately reflects Java 25 requirement

## Risks & Mitigations

| Risk | Severity | Mitigation |
|------|----------|------------|
| GMavenPlus plugin incompatibility | Medium | Plugin v4.0.6 should support Java 25; test immediately |
| Developer lacks Java 25 | Medium | Fallback to default JDK; document requirement |
| Preview feature syntax changes | Low | Unnamed variables now standard; no preview features used |
| Kotlin compatibility issues | Low | Kotlin 2.1.0 fully supports Java 25 |

## Post-Upgrade Benefits

With Java 25, SongScribe can now leverage:
- **Unnamed variables** (standard, no preview flag needed)
- **Flexible constructor bodies** (validate before super())
- **Module imports** (simplify Swing/AWT imports)
- **Stream Gatherers** (better music data processing)
- **Scoped Values** (better than ThreadLocal)
- **Structured Concurrency** (parallel resource loading)
- **Generational ZGC/Shenandoah** (smoother UI performance)

## Notes

- The `--enable-preview` flag may no longer be needed after upgrade (unnamed variables are now standard)
- Kotlin 2.1.0 already supports Java 25 JVM target
- No deprecated API usage detected in current codebase
- All Java 21 features are compatible with Java 25

## Critical File Paths

**Must modify:**
- `/Users/aparajita/Developer/projects/SongScribe/pom.xml`
- `/Users/aparajita/Developer/projects/SongScribe/scripts/compile.sh`
- `/Users/aparajita/Developer/projects/SongScribe/README`
- `/Users/aparajita/Developer/projects/SongScribe/.claude/rules/development.md`

**Recommended:**
- `/Users/aparajita/Developer/projects/SongScribe/.idea/misc.xml`
- `/Users/aparajita/Developer/projects/SongScribe/.idea/compiler.xml`
- `/Users/aparajita/Developer/projects/SongScribe/.idea/kotlinc.xml`
