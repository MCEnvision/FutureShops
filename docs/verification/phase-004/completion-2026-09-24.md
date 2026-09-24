# Phase 004 completion evidence

## Checks

| check | result |
| --- | --- |
| Pixelmon receipt codec tests | passed |
| transactional provider fixture test | passed |
| complete Gradle build | passed |
| packaged dependency boundary check | passed |
| exact Pixelmon server startup | passed |
| Pixelmon native mutation and durable save | passed |
| fresh process receipt lookup | passed |
| Pixelmon absent startup | passed |

All runtime checks used Java 17 and dedicated server processes in disposable phase 004 directories. No client was launched. Pixelmon warning output unrelated to FutureShops remained outside the native integration result.

The disposable server, provider artifact, fixture jar, temporary source, logs, and generated runtime files must be removed after the phase packet is reviewed. They are not source or release artifacts.
