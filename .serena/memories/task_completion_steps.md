# SongScribe Task Completion Steps

## After Implementation

### 1. Compilation and Testing
- Compile changes: `./scripts/compile.sh`
- Run tests: `mvn test`
- Fix any compilation errors or test failures
- If debugging a bug, request user to run and provide logs

### 2. Manual Testing
- Run the application: `./scripts/run-debug.sh` (for debugging) or `./scripts/run.sh` (production)
- Test the specific feature or bug fix thoroughly
- Verify UI interactions work as expected
- Check for any warning messages in logs

### 3. Code Review Readiness
- Ensure code follows style conventions
- Check for any TODO/FIXME comments that should be resolved
- Verify no debug code left behind
- All changes aligned with architecture patterns

### 4. Git Commit
- Use `/commit-commands:commit` skill (NOT manual git commands)
- Write clear commit message with type prefix
- Reference issue numbers if applicable
- Example: `fix: resolve note rendering issue in layout2 #456`

### 5. Branch Integration
- Current branch: Check git status
- Main branch for PRs: `develop`
- Push branch if ready for review
- Create PR if applicable

### 6. Final Verification
- Run quick iteration cycle: `./scripts/compile.sh && ./scripts/run-debug.sh`
- Verify no regressions in related features
- Check build output for warnings

## Before Claiming Completion

Verify:
- All tests passing: `mvn test`
- No compilation warnings
- Code compiles cleanly: `./scripts/compile.sh`
- Application runs without fatal errors
- Changes match requested requirements

## Special Cases

### When Adding New Features
- Update relevant message classes if adding new UI events
- Register new actions in Actions class if adding menu/toolbar items
- Add unit tests for logic components
- Update renderer registry if adding new element renderers

### When Fixing Bugs
- Add regression test if possible
- Do NOT use run.sh with debug logging unless specifically requested by user
- Request user to run app and provide logs if needed

### When Refactoring
- Ensure all references are updated
- Run full test suite
- Verify no behavioral changes
- Document if affecting public APIs

### Maven Commands Reference
- Clean build: `mvn clean`
- Compile only: `mvn compile`
- Run tests: `mvn test`
- Full package: `mvn clean package`
- View dependencies: `mvn dependency:tree`
