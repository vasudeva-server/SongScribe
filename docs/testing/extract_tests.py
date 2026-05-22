import re
import sys

# Read the matrix.md file
with open('/Users/aparajita/Developer/projects/SongScribe/docs/testing/matrix.md', 'r') as f:
    content = f.read()

# Find all table rows - look for markdown table format
# Tables have | separators
lines = content.split('\n')

in_table = False
tests = set()

for line in lines:
    # Check if this is a table separator or data row
    if '|' in line:
        parts = line.split('|')
        if len(parts) >= 5:  # Ensure we have enough columns
            # The "existing test" column is typically the 4th column (index 3)
            existing_test = parts[4].strip() if len(parts) > 4 else ""

            # Skip empty, "—", or "none" entries
            if not existing_test or existing_test == "—" or existing_test.startswith("none"):
                continue

            # Skip header rows and separators
            if "existing test" in existing_test or "---|" in existing_test:
                continue

            # Extract test names from the existing_test field
            # Remove backticks
            existing_test = existing_test.replace('`', '')

            # Split by common separators: " + " and ", "
            parts_list = re.split(r'\s*\+\s*|,\s*', existing_test)

            # Extract all test names matching the pattern
            matches = re.findall(r'[A-Z]\w+?Test(?:\.test\w+\b)?', existing_test)
            for match in matches:
                if match:
                    tests.add(match)

# Remove class-only entries if the same class has method entries
filtered_tests = set(tests)
for test in tests:
    if '.' not in test:  # This is a class-only entry
        # Check if there are any method entries for this class
        has_methods = any(t.startswith(test + '.') for t in tests)
        if has_methods:
            filtered_tests.discard(test)

# Sort and write to file
sorted_tests = sorted(filtered_tests)
with open('/Users/aparajita/Developer/projects/SongScribe/docs/testing/tests-to-keep.txt', 'w') as f:
    for test in sorted_tests:
        f.write(test + '\n')

print(f"Extracted {len(sorted_tests)} unique test names")
