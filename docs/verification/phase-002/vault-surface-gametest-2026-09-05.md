# Exact NeoForge Vault surface GameTest

This record covers the mutation surface proof for the separately installed FutureShops Vault proof registrant. It uses the same production FutureShops jar and exact Pixelmon runtime inputs as the packaged integration checks. The registrant and SQLite backend remain disposable proof artifacts and are not production dependencies.

## Runtime manifest

| Field | Value |
| --- | --- |
| Host | `node-1`, Linux amd64, headless dedicated GameTest server |
| Minecraft | `1.21.1` |
| NeoForge | `21.1.248` |
| Java | `21.0.11` |
| FutureShops source commit | `995b9f386d5a4edbd6db295e06bdee675e205752` |
| FutureShops artifact SHA 256 | `a3b3a1bdc1014efcbadf492b20599acb7f7dd35f41b53489de857a104447ac48` |
| FutureShops artifact SHA 512 | `a4b21001342677af52347c96c6dbbbeaf8a2a9c4f313819cbf2e9dbd9e82d8b112cb6a1679831de806e956ecbcdb71796c50d4dc49d353208442fcbcfbec7a0c` |
| Vault proof registrant SHA 256 | `ab578f60f8302f304000ee6d0b401ec36bbb93589357ac6dce3f75cc7539bb30` |
| Pixelmon `9.4.0` SHA 256 | `9020393f98382ae8794ef2694e7bec1984c1a0eca735ea3eea06e0cb151c61f2` |
| GeckoLib `4.8.4` SHA 256 | `a1b6ce25e8627aa7e748672eedb6b71af68e0993462313649c259f38e42bcac9` |
| selected provider | `vault` |
| EULA | `eula=true` |

The SQLite JDBC driver was appended only to the disposable GameTest legacy classpath so the proof backend could run under the isolated NeoForge classloader. It is not present in the production jar.

## Surface assertions

The exact packaged server loaded the production jar, Pixelmon `9.4.0`, GeckoLib, and the separately installed proof registrant. The FutureShops Pixelmon mixin target resolved to `com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage`. The registrant resolved provider `vault` to `READY` before the tests ran.

The six fixture tests exercised the real server services and public API and asserted the resulting provider balances, inventory, stock, custody, and refusal behavior.

* Server shop sell confirmed the provider credit of `25`, removed one sold item, increased stock by one, and left no incomplete custody.
* Player shop buy confirmed escrow delivery of one diamond, debited exactly `1`, and left no incomplete custody.
* Server shop buy confirmed provider debit and delivery of one diamond at the exact catalog price, with no incomplete custody.
* Cart buy confirmed the aggregate debit of `500` and item delivery. The pay transfer confirmed one payer debit of `25` and one recipient credit of `25`.
* The public `ShopModAPI` confirmed provider backed withdrawal and deposit with a net balance delta of `1`.
* Physical money item use and `/withdraw` plus `/deposit` were refused while `vault` was selected. The item count and custody state remained unchanged.

The server log contains the bounded route diagnostics:

```text
FutureShops Vault surface route=server_shop_sell provider=vault status=CONFIRMED amount=25 balance_delta=25 item_delta=-1 stock_delta=1 custody_incomplete=false
FutureShops Vault surface route=public_api provider=vault status=CONFIRMED withdrawal=2 deposit=3 balance_delta=1 custody_incomplete=false
FutureShops Vault surface route=physical_money provider=vault status=REFUSED reason=INTERNAL_ONLY item_consumed=false command_item_delta=0 custody_incomplete=false
FutureShops Vault surface route=player_shop_buy provider=vault status=CONFIRMED amount=1 balance_delta=-1 item_delta=1 custody_incomplete=false
FutureShops Vault surface route=cart_buy_and_pay provider=vault cart_status=CONFIRMED cart_amount=500 cart_balance_delta=-500 cart_item_delta=1 pay_status=CONFIRMED pay_amount=25 pay_source_delta=-25 pay_target_delta=25 custody_incomplete=false
FutureShops Vault surface route=server_shop_buy provider=vault status=CONFIRMED amount=500 balance_delta=-500 item_delta=1 custody_incomplete=false
```

The complete current artifact log is `/tmp/futureshops-vault-packaged-final-first-log.aEfL1a` with SHA 256 `be5ab5a210d8e0d32d026ecec7ff36cd8a6b22a02e772c8e78a38c9cf17a1d19`. NeoForge reported `27` registered tests and `All 27 required tests passed`. Six tests cover the enabled surface routes described above, and the companion failure matrix runs in the same exact server process. The process exited with status `0` and the disposable runtime was removed after the log and route diagnostics were recorded.

The companion [Vault failure and recovery matrix](vault-failure-matrix-2026-09-05.md) adds the exact packaged interruption, service loss, lookup, retry, duplicate registration, late registration, and missing provider checks. It ran in the same `27` test process and is recorded in the current log above.

Pixelmon emitted one external tag warning for a missing spawning reference. No FutureShops exception or FutureShops error was present. This headless GameTest is primary server evidence. No client run was needed because these assertions cover server-side provider mutation and refusal behavior only.

This proof does not certify the unmodified PixelmonEconomyBridge and FinalEconomy stack. That stack remains safely refused unless a separately installed bridge and backend provide the same request-aware durable receipt, lookup, retry, conversion, and recovery contract.
