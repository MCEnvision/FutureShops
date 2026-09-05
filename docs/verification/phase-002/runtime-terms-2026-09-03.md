# phase 002 runtime terms authorization

## authorization

The owner authorized full disposable runtime validation in the project task on 2026-09-03. This authorization covers the exact Pixelmon and hybrid profiles assembled for Phase 002. It contains no credentials, player data, or production server information.

The authorization was recorded before the full launches. The profiles were not used for production gameplay, and no public release or external upload was performed.

## exact profile records

| Profile | Terms file | Content | SHA 256 | Provider configuration |
| --- | --- | --- | --- | --- |
| Exact Pixelmon | `/tmp/futureshops-pixelmon-exact.xIDOL4/eula.txt` | `eula=true` | `ee27072e4a23e088522f740ddaab0c7c4145c186969e90a86254faa3a5ec5ce6` | `pixelmon` |
| Exact hybrid | `/tmp/futureshops-youer-pixelmon-248.zhVzs4/eula.txt` | `eula=true` | `ee27072e4a23e088522f740ddaab0c7c4145c186969e90a86254faa3a5ec5ce6` | `pixelmon` |

The current development run profile also contains the same `eula=true` file hash. It is configured for `internal` and is separate from the exact external profiles.

## launch chronology

The first assembly checks used `eula=false` and stopped at the terms gate. Those logs remain historical evidence of preauthorization behavior only. After authorization, the exact Pixelmon profile launched on port `25567` and the exact hybrid profile launched on port `25566`. Both reached FutureShops server startup with the reviewed artifact set. Their applicable logs are recorded in `pixelmon-refusal-2026-09-03.md` and `completion-2026-09-03.md`.

This record resolves the Phase 002 `EXT-008` terms gate for the two exact disposable environments. It does not authorize production use, publication, balance changes, or unsupported external mutations. The direct Pixelmon adapter still refuses mutation because the reviewed API has no durable receipt or idempotent retry capability.
