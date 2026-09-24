# Phase 004 provider conformance

This record covers the Forge 1.20.1 provider boundary for phase 004.

## Native Pixelmon path

The tested dependency was the unchanged Pixelmon Forge 1.20.1 9.2.3 universal artifact.

| field | value |
| --- | --- |
| source | `https://cdn.modrinth.com/data/59ZceYlU/versions/KjmzoXMR/Pixelmon-1.20.1-9.2.3-universal.jar` |
| sha 256 | `8f2777f5a7cd2b5fc48ac3f5531d434af4120c7d40adc9929d5a3737c404246a` |
| sha 512 | `3a9c6f375214c6d93c6cce8235e8a206e8f9731be8e168a254e78539087080796d97f0b22cb9a6db09d901c72e2e1ae53b9f2484761fb310479ce9f84ac9b145` |
| Forge runtime | `47.4.20` |
| Java runtime | `17.0.19` |

Bytecode inspection confirmed `PlayerPartyStorage` balance methods and the `NBTStorageSaveAdapter` writer. A dedicated Forge server loaded the exact artifact and logged the enabled native gate without a mixin failure.

The real server fixture then performed one deposit through `setBalance`, wrote the native storage image, stopped, and started a fresh process. The first image contained the balance and a bounded `futureshopsReceipt` with the original operation and request fingerprints. The fresh process recovered the same balance and receipt from the native file. The follow up test also rewrote the loaded storage and confirmed the receipt remained present.

The restart test initially found that Pixelmon dropped the receipt when its storage image was rewritten. The native mixin now retains a validated wrapper image on read and restores it on later writes. Unit coverage verifies bounded receipt preservation and malformed receipt rejection.

Absent Pixelmon startup was also exercised with the same FutureShops build. The server reached `Done` without loading the optional target. Unsupported versions and account classes remain fail closed through the exact version and target gate.

## Isolation

The production jar contains FutureShops native adapter classes only. The separately compiled fixture jar and Pixelmon jar were installed only in the disposable server runtime. The production build and archive boundary checks passed, and no fixture classes or nested provider jar were packaged.
