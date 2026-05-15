## Third Party API Documentation

Use context7. Two-step workflow:

1. `mcp__plugin_context7_context7__resolve-library-id` — pass the library name (e.g. `"gson"`, `"jackson"`)
2. `mcp__plugin_context7_context7__query-docs` — pass the resolved ID, a specific `topic` (e.g. `"conversion to object"`), and `tokens:10000`
