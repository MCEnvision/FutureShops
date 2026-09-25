# phase 009 GameTest isolation record

The initial GameTest attempt exposed that the Forge run configuration ignored the requested verification directory and used the pre-existing `run` directory. That attempt still passed all nine required tests, but its runtime output was not retained as phase evidence and the existing directory was preserved because its baseline ownership could not be established.

The phase fix adds the optional Gradle property `verificationGameDirectory`. The default remains `run` for ordinary development. When supplied, every Forge run configuration uses the requested path.

The rerun supplied `-PverificationGameDirectory=.phase009-gametest-runtime-20260925`. Before launch, the exact runtime contained `eula=true`. Forge logged the requested game directory, started no client or renderer, ran the `futureshops.offer_service` batch, and reported all nine required tests passed. The server stopped normally and no Forge GameTest process remained.

The disposable runtime contained only generated server files, logs, configuration, and the test world. It was removed after the evidence was reviewed and its absence was verified. The pre-existing `run` directory is preserved and is not phase-owned cleanup material.
