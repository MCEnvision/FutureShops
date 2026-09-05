# Exact NeoForge Vault surface GameTest

This record covers the mutation surface proof for the separately installed FutureShops Vault proof registrant. It uses the same production FutureShops jar and exact Pixelmon runtime inputs as the packaged integration checks. The registrant and SQLite backend remain disposable proof artifacts and are not production dependencies.

## Runtime manifest

| Field | Value |
| --- | --- |
| Host | `node-1`, Linux amd64, headless dedicated GameTest server |
| Minecraft | `1.21.1` |
| NeoForge | `21.1.248` |
| Java | `21.0.11` |
| FutureShops source commit | `a8523e1e15cf5a4db79812ca6581fba25339ce67` |
| FutureShops artifact SHA 256 | `ab1284d23159d4e5fddacc7740ad13db433a8c2d37a67ceac7fcde291ee45247` |
| FutureShops artifact SHA 512 | `972baa653876716a8f2a1dee5340237687710261299d22b7ed329e773ed4dc0e9aa411c74ee947a01abcf90104b888f30ce64cb6aab0dfffa39fd761c949dae4` |
| Vault proof registrant SHA 256 | `9927e5897ff76b2bdd9eb2fff4bdec37480ba5aaf317ba7529782af2ce84c20d` |
| Pixelmon `9.4.0` SHA 256 | `9020393f98382ae8794ef2694e7bec1984c1a0eca735ea3eea06e0cb151c61f2` |
| GeckoLib `4.8.4` SHA 256 | `a1b6ce25e8627aa7e748672eedb6b71af68e0993462313649c259f38e42bcac9` |
| selected provider | `vault` |
| EULA | `eula=true` |

The SQLite JDBC driver was appended only to the disposable GameTest legacy classpath so the proof backend could run under the isolated NeoForge classloader. It is not present in the production jar.

## Surface assertions

The exact packaged server loaded the production jar, Pixelmon `9.4.0`, GeckoLib, and the separately installed proof registrant. The FutureShops Pixelmon mixin target resolved to `com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage`. The registrant resolved provider `vault` to `READY` before the tests ran.

The four fixture tests exercised the real server services and asserted the resulting provider balances, inventory, stock, custody, and refusal behavior.

* Server shop sell confirmed the provider credit of `25`, removed one sold item, increased stock by one, and left no incomplete custody.
* Player shop buy confirmed escrow delivery of one diamond, debited exactly `1`, and left no incomplete custody.
* Cart buy confirmed the aggregate debit of `500` and item delivery. The pay transfer confirmed one payer debit of `25` and one recipient credit of `25`.
* Physical money item use and `/withdraw` plus `/deposit` were refused while `vault` was selected. The item count and custody state remained unchanged.

The server log contains the bounded route diagnostics:

```text
FutureShops Vault surface route=server_shop_sell provider=vault status=CONFIRMED amount=25 balance_delta=25 item_delta=-1 stock_delta=1 custody_incomplete=false
FutureShops Vault surface route=physical_money provider=vault status=REFUSED reason=INTERNAL_ONLY item_consumed=false command_item_delta=0 custody_incomplete=false
FutureShops Vault surface route=player_shop_buy provider=vault status=CONFIRMED amount=1 balance_delta=-1 item_delta=1 custody_incomplete=false
FutureShops Vault surface route=cart_buy_and_pay provider=vault cart_status=CONFIRMED cart_amount=500 cart_balance_delta=-500 cart_item_delta=1 pay_status=CONFIRMED pay_amount=25 pay_source_delta=-25 pay_target_delta=25 custody_incomplete=false
```

The complete log for the current artifact is `/tmp/futureshops-vault-surface-packaged-current-20260905.log` with SHA 256 `fb6456f6bc72ea47f8c20b7ea97021d0640aba1a55ade645ef0f79fab6c3a96e`. NeoForge reported `24` registered tests and `All 24 required tests passed`. The process exited with status `0` and the disposable runtime was removed after the log, database hash, and receipt count were recorded. The provider database hash before cleanup was `39474b679087892193a8fbccaff5031d14fd3a7f17ae88806824237996511a04`. The receipt directory contained `53` files, including the clean marker.

Pixelmon emitted one external tag warning for a missing spawning reference. No FutureShops exception or FutureShops error was present. This headless GameTest is primary server evidence. No client run was needed because these assertions cover server-side provider mutation and refusal behavior only.

This proof does not certify the unmodified PixelmonEconomyBridge and FinalEconomy stack. That stack remains safely refused unless a separately installed bridge and backend provide the same request-aware durable receipt, lookup, retry, conversion, and recovery contract.
