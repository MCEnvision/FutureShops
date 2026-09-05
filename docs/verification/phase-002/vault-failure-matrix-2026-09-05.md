# Exact NeoForge Vault failure and recovery matrix

This record covers the failure and recovery proof for the separately installed Vault proof registrant. It complements the surface GameTest record and uses the production FutureShops artifact, exact Pixelmon runtime input, and disposable proof registrant on the headless `node-1` host.

## Runtime manifest

| Field | Value |
| --- | --- |
| Host | `node-1`, Linux amd64, headless dedicated GameTest server |
| Minecraft | `1.21.1` |
| NeoForge | `21.1.248` |
| Java | `21.0.11` |
| FutureShops source commit | `6bbaf9156bcd8d79bee274717f2ae67d4db6f69e` |
| FutureShops artifact SHA 256 | `945d175c363ec06f6b0e965161cff081c5deebf1b1ed899e605b48890fc69563` |
| FutureShops artifact SHA 512 | `0e38cc66eaaf739413f5a8b2f193d97aca40ea4e8c5be18c4f9f29999ccbc6a1a8055045028a796183b623bf6e4d49478134683b23dbe31445e0db96fc02bae2` |
| Vault proof registrant SHA 256 | `ab578f60f8302f304000ee6d0b401ec36bbb93589357ac6dce3f75cc7539bb30` |
| Vault proof registrant SHA 512 | `b24883d97c8f82963909699da70889bcc2d863d7f1be70cabe1a1f4087a2865837398eceffc93ec17fcef7f85bfc29804e8080b1c3336b5db845e55ea01bfc38` |
| Pixelmon `9.4.0` SHA 256 | `9020393f98382ae8794ef2694e7bec1984c1a0eca735ea3eea06e0cb151c61f2` |
| GeckoLib `4.8.4` SHA 256 | `a1b6ce25e8627aa7e748672eedb6b71af68e0993462313649c259f38e42bcac9` |
| selected provider | `vault` |
| EULA | `eula=true` |

The SQLite JDBC driver was appended only to the disposable GameTest legacy classpath. It is not included in the production jar. The complete sanitized server log is `/tmp/futureshops-vault-surface-current-log.MekPqH` with SHA 256 `fc96646872f1a0cfcb49af9db3d678a55b1feca5f07ddef6c204eff4fa4947fd`.

## Failure and recovery assertions

The test created a separate SQLite proof account with a starting balance of `1,000` and exercised each provider boundary with fresh request UUIDs.

* Service loss returned typed `UNAVAILABLE` results for balance, precheck, mutation, and receipt lookup. Restoring service and retrying the same request produced one confirmed debit and a balance of `990`.
* Interruptions after the balance update, after receipt insertion, and before commit returned `RECOVERY_REQUIRED`, left the balance unchanged, left no receipt, and allowed one safe retry for each request.
* Interruption after commit returned `RECOVERY_REQUIRED`, but durable lookup returned the receipt. Retry and a duplicate request returned the same receipt without another debit.
* The final balance was `950` after five confirmed requests and the SQLite receipt count was `5`. The backend reported `journal_mode=delete` and `synchronous=full`.
* A second `vault` registration and a late registration were refused with `LATE` after the provider registry froze. Resolving an absent provider returned `MISSING`. No internal provider was selected or used.

The bounded server diagnostics were:

```text
FutureShops Vault failure matrix registration duplicate=LATE late=LATE missing_state=MISSING
FutureShops Vault failure matrix provider=vault service_balance=UNAVAILABLE service_precheck=UNAVAILABLE service_mutation=UNAVAILABLE service_lookup=UNAVAILABLE service_retry=CONFIRMED after_balance=RECOVERY_REQUIRED after_receipt=RECOVERY_REQUIRED before_commit=RECOVERY_REQUIRED after_commit=RECOVERY_REQUIRED after_commit_lookup=CONFIRMED after_commit_retry=CONFIRMED duplicate=CONFIRMED duplicate_registration=LATE late_registration=LATE missing_state=MISSING balance=950 receipt_count=5 journal_mode=delete synchronous_mode=full lifecycle=READY
All 27 required tests passed :)
```

The exact packaged runtime also ran the four Vault surface tests in the companion [surface GameTest record](vault-surface-gametest-2026-09-05.md). The complete process exited with status `0`, and the disposable runtime, world, database, classpath, and generated configuration were removed after the log and hashes were retained.

This proves the public provider contract and the separate proof backend. It does not certify the unmodified PixelmonEconomyBridge and FinalEconomy stack. That stack remains refused until its own bridge and backend provide stable request identity, one transaction balance and receipt persistence, lookup, retry deduplication, and recovery evidence.
