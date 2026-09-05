# Exact NeoForge Vault surface GameTest

This record covers the mutation surface proof for the separately installed FutureShops Vault proof registrant. It uses the same production FutureShops jar and exact Pixelmon runtime inputs as the packaged integration checks. The registrant and SQLite backend remain disposable proof artifacts and are not production dependencies.

## Runtime manifest

| Field | Value |
| --- | --- |
| Host | `node-1`, Linux amd64, headless dedicated GameTest server |
| Minecraft | `1.21.1` |
| NeoForge | `21.1.248` |
| Java | `21.0.11` |
| FutureShops artifact SHA 256 | `f97805026224e435d00ed6478f6d122313bc99d44628fa9033602fd15d36173d` |
| FutureShops artifact SHA 512 | `e319285ac9069b12c3b12701deba8c92cd430dcf6c9b12e7a038886799f1d924a732d164400992307b70ee64ed06cf4bd74faee8971d15587856b80b4eea42cf` |
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

The complete log is `/tmp/futureshops-neoforge-vault-surface-20260905-final.log` with SHA 256 `254c32130090186c5760c0ea3bf4832c4e29fba8bd157714c909f10505df5515`. NeoForge reported `24` registered tests and `All 24 required tests passed`. The process exited with status `0` and the disposable runtime was removed after the log, database hash, and receipt count were recorded. The provider database hash before cleanup was `0666d362122924a228fd0cea0f20a04248d5847ff9e8d8c4a576913e115fe880`. The receipt directory contained `53` files, including the clean marker.

Pixelmon emitted one external tag warning for a missing spawning reference. No FutureShops exception or FutureShops error was present. This headless GameTest is primary server evidence. No client run was needed because these assertions cover server-side provider mutation and refusal behavior only.

This proof does not certify the unmodified PixelmonEconomyBridge and FinalEconomy stack. That stack remains safely refused unless a separately installed bridge and backend provide the same request-aware durable receipt, lookup, retry, conversion, and recovery contract.
