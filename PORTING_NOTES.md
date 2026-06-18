# FutureShops — Porting Notes (1.20.1 Forge → 1.21.1 NeoForge)

Living record of every non-trivial mapping decision. Audit lives in `FutureShopsAudit.md`.
Old source (read-only) in `SourceCodeOld/FutureShops`. New mod = repo root.

## Identity / namespace (Decision A — save-compat critical)

- **Runtime modid + ALL resource/data namespaces = `futureshops`** (unchanged from old mod).
  Renaming would turn every existing coin / placed shop block into air.
- **Java package = `com.enviouse.futureshopsp`** (the trailing "p" is the project rename; does
  NOT affect runtime IDs). Project folder = `FutureShopsP`.
- **Main class = `com.enviouse.futureshopsp.Futureshops`, `MODID = "futureshops"`.**
- **FOOTGUN when porting old files:** change ONLY the package/import FQN segment
  `com.enviouse.futureshops` → `com.enviouse.futureshopsp`. **Never** touch the bare string
  literal `"futureshops"` — it is the runtime namespace / NBT key root / network channel id.
  A blanket `s/futureshops/futureshopsp/` would corrupt save data. Sed is banned for the port.

## Coin legacy migration (Decision B)

- Lazy / on-touch rescue inside `MoneyValidationService.validate()`: if `stack.get(COIN_DATA)`
  is null, before failing, read the legacy `minecraft:custom_data` (CustomData) for the old
  `futureshops:coin_data` compound; if present + structurally valid, reconstruct `CoinData`,
  set the proper component, strip the custom_data remnant, then validate against the recomputed
  checksum. If absent → fail `MISSING_COIN_DATA`. NOT a DataFixer. NOT an economy reset.
- The checksum salt (`Config.moneyChecksumSalt`) and the `futureshops:coin_data` namespace must
  stay byte-identical.
- **MANDATORY STOP:** empirically confirm the legacy-NBT landing location on a real pre-port
  world (mint on old jar → load on new build → dump stack components) BEFORE finalizing the
  rescue path. Do not assume `custom_data` is where it lands.
- **RESOLVED + signed off.** DataFixer probe (3465→3955, the real vanilla chain) confirmed legacy
  `coin_data` lands in `minecraft:custom_data` under the identical key, all 7 fields byte-intact.
  Rescue implemented in `MoneyValidationService.rescueLegacy`: promote to typed component, strip the
  remnant, and remove `custom_data` entirely if empty so a rescued coin is component-identical to a
  fresh one (they stack). Guarded by `LegacyCoinDataFixLandingTest` (landing) + `CoinRescueTest`
  (stack-equivalence, tampered-fails-on-checksum, other-keys-survive). `consume()` untouched.

## Deliberate omissions / decisions

1. **HandshakeHandlerMixin: DROPPED (not redesigned).** Targeted Forge-only
   `net.minecraftforge.network.HandshakeHandler` / `HandshakeMessages` / `NetworkEvent`, all
   removed in NeoForge. Its only job was a cosmetic "update the mod" disconnect screen on a
   channel-version mismatch — zero economic/security behavior. The anti-desync property is
   preserved natively: the payload registrar is versioned + mandatory, so NeoForge refuses a
   client whose `futureshops:main` protocol version differs. `mixins[]` stays empty.
2. **`MissingMappingsEvent` does NOT exist in NeoForge 1.21.1.** The `futureshops:coin → money`
   legacy remap uses **`DeferredRegister.addAlias(from, to)`** instead (resolves lookups of the
   old name to the new one when the old name is absent; must be called before `RegisterEvent`).

## Verified facts (don't re-litigate)

- NeoForge **21.1.233** (latest stable 21.1.x); Java **21**.
- **Gradle 8.8 + ModDevGradle 2.0.141** — the actual MDK pairing. (Audit said "Gradle 9.x";
  that was wrong — corrected to 8.8, the skeleton default. Do NOT bump.)
- Parchment **`parchment-1.21.1:2024.11.17`**.
- SavedData: `computeIfAbsent(new SavedData.Factory<>(ctor, load, /*DataFixTypes*/null), NAME)`;
  `load/save(CompoundTag, HolderLookup.Provider)`. `SavedDataType<T>` is 1.21.5+ — do NOT use.
- Networking: 37 registered payloads (21 C2S + 16 S2C). **enqueueWork trap:** the old
  `consumerMainThread` ran handlers on the main thread implicitly; the payload system does NOT.
  Every C2S handler that mutates economy / ledger / world MUST wrap the mutation in
  `ctx.enqueueWork(() -> ...)`. Per-batch checklist item in Step 4.
- `PacketDistributor.sendToPlayer(player, payload)` / `sendToServer(payload)`.
- `IPayloadContext.player()` returns the side-local player → server-side C2S = sending ServerPlayer.
- `StreamCodec.of(encoder, decoder)` — encoder FIRST (opposite of Codec); `StreamCodec.unit` for empties.

## Verify-in-env before relying on (from user answers C–N; confirm against the 21.1.233 jar)

- D `StreamCodec.of/composite` exact arg order (silent runtime failure if backwards).
- E RS2 2.0.9 `BlockEntity→Network` accessor chain + API FQNs + maven coords/auth (Step 8; stub first).
- F owner-head skin pipeline (`SkinManager.getOrLoad→CompletableFuture<PlayerSkin>`, `PlayerSkin.texture()`).
- G `LivingHurtEvent`→`LivingIncomingDamageEvent` (read-only, do NOT cancel); `*TickEvent.Post` accessor.
- H registry convenience method names (`registerSimpleBlockItem` etc.) + raw-path `Properties.setId`.
- I `RegisterClientExtensionsEvent.registerItem` arity; VertexConsumer `addVertex(...).setColor()...` names.
- J `appendHoverText(ItemStack, Item.TooltipContext, List<Component>, TooltipFlag)`.
- K `ShopBlock.use` → `useWithoutItem`(→`InteractionResult`) + `useItemOn`(→`ItemInteractionResult`).
- L CarryOn IMC name vs the `data/carryon` datapack tag (prefer the tag if it covers our use).
- M `GuiGraphics.blit` RenderType overload is **1.21.2+**; on 1.21.1 use the `blit(ResourceLocation, ...)` family.

## Check-in protocol (as of economy tranche)

Stream the mechanical work; **hard-stop only for**: (1) irreversible / user-facing-behavior calls
— legacy-coin landing dump before the rescue, anything touching existing worlds / player data /
a command's contract — show the actual evidence (dump, before/after); (2) a genuinely ambiguous
fork that reading the jar/source CANNOT settle. Do **not** stop for green builds, "big cluster"
notes, or sequencing — those stream. The verification (javap/compile/behavior-preserving) is the
quality bar; a green-build sign-off adds nothing it doesn't already have.

**Tests-as-gate for high-blast-radius clusters only** (where a silent runtime regression would pass
a quick playtest): money / ledger / balances, the vanish/visibility packet path, anything wrong-under-
concurrent-load. For those, a runnable invariant test is part of the cluster gate (as the ledger is).
Not everywhere — only where silent runtime regression is the real risk. Networking ledger-touching
C2S handlers get a concurrency/round-trip guard when those batches land.

**Networking `enqueueWork` concurrency test (spec, from user):** old `SimpleChannel.consumerMainThread`
ran handlers on the main thread implicitly; the payload system does NOT — every C2S handler is on the
network thread until proven otherwise, and every one touching ledger/balance/SavedData is
guilty-until-proven-on-main. The guard must fire the SAME mint redemption from multiple threads and
assert the ledger still refuses over-redeem: **total redeemed ≤ authorized_count, and total balance
conserved under parallel debit/credit.** That catches the missing `enqueueWork` — the bug passes a
single-threaded playtest and only corrupts under concurrent load. Pin conservation under concurrency,
not just round-trip correctness. (EphemeralTestServer makes this feasible.)

**`NbtMatchUtil` "equal item" semantics (transaction services, beat of care):** `stack.getTag()` exact
`CompoundTag` equality → component world. `isSameItemSameComponents` vs a `DataComponentPatch` compare
changes what "equal item" means for listing-match and barter; the empty-patch-vs-`null` edge can shift
match results. Verify match outcomes against real listings — do NOT flatten into a mechanical swap;
"equal item" semantics drifting is a behavior change wearing a compile-fix's clothes.

**NbtMatchUtil design (decided, to implement with the block/caps/transaction cluster):** `requiredTag`
originates from player-created listings (`ShopBlockEntity.nbtTag`, persisted) + SNBT (`ShopSellService`
`TagParser.parseTag`); consumed by caps adapters, transaction services, `PlayerShopBlockService`.
Plan: match on `DataComponentPatch` — candidate `stack.getComponentsPatch()` vs the listing's required
patch; old `null` requiredTag (matched only no-tag items) → empty patch (preserves the edge);
`nbtAware=false` → any variant. Existing stored `nbtTag` listings: lazy-migrate old CompoundTag →
component patch via the vanilla item DataFixer (wrap `{id,Count,tag}`, fix, take patch) at load —
analogous to the coin rescue. Verify match outcomes against real listing shapes (bare / NBT-variant /
empty-patch edge) with a test, since this touches player-created listing data.

## Build log

| Step | What | `./gradlew build` result |
|---|---|---|
| 0 | Skeleton reconciled (modid→futureshops, parchment→1.21.1/2024.11.17, namespace dir, mixins filename, main class `Futureshops`); empty build | **SUCCESSFUL in 5s** → `futureshops-2.2.0.jar`. (1 fix: skeleton `Config.java` still referenced old `Futureshopsp.MODID`.) NeoForge 21.1.233 + MC 1.21.1 + Parchment resolve OK. |
| 1 | Leaf tranche: real `Config` (`ForgeConfigSpec`→`ModConfigSpec`, 1:1) + 17 pure value/enum/util classes + 15 `data/` DTOs. FQN package rename only (`com.enviouse.futureshops`→`...futureshopsp`); bare `"futureshops"` namespace strings untouched. | **compileJava SUCCESSFUL** (data DTOs use no removed `writeItem`/`readItem`/`new ResourceLocation`) |
| 3 | **MANDATORY STOP — anti-dupe ledger.** `SpentMintsSavedData` ported: `load`/`save` gain `HolderLookup.Provider` (unused — all primitive/UUID/String); `save` now `@Override`; `computeIfAbsent(loader, ctor, NAME)` → `computeIfAbsent(new SavedData.Factory<>(ctor, load, null), NAME)`. `consume()` + `registerMint()` byte-identical. Serialized NBT keys unchanged ⇒ existing `futureshops_coin_mints.dat` loads as-is (save-compat). | **compileJava SUCCESSFUL; consume()+round-trip SIGNED OFF by user — do NOT touch consume()** |
| 3t | **Test harness** (own commit, pre-economy). MDG `neoForge.unitTest{ enable(); testedMod = mods."$mod_id" }` + JUnit 5.10.2 + `test{ useJUnitPlatform() }`. `SpentMintsSavedDataTest`: (a) load→save→load stability, (b) anti-dupe — N clones of one mint_id ≤ authorized_count. **From here, "tests green" is a tranche gate alongside "build green."** Broader old suite (WireRoundTripTest, ProtocolVersionConstantTest, …) revived opportunistically alongside the code each covers. | **./gradlew test SUCCESSFUL — 2/2 passed** |
| 3a/3b | Events (9: `Event` import swap; `@Cancelable`→`implements ICancellableEvent` ×4 incl. top-level `ShopOpenEvent`) + economy (`InternalBalanceSavedData` SavedData-migrated; `BalanceManager` rename-only; `InternalEconomyProvider`: `MinecraftForge.EVENT_BUS`→`NeoForge.EVENT_BUS`, **`if(post(e)){…}`→`post(e); if(e.isCanceled()){…}`** ×2, `computeIfAbsent`→`SavedData.Factory`). **GOTCHA (recurring):** an FQN sed (`net.minecraftforge.common.MinecraftForge`→`NeoForge`) swaps the *import* but leaves bare `MinecraftForge.EVENT_BUS` *usages* — swap both (`MinecraftForge.`→`NeoForge.`). | **build+test SUCCESSFUL — 2/2** |
| 3c | `DynamicPricingSavedData` SavedData-migrated (load/save+`HolderLookup.Provider`, `SavedData.Factory`). `DynamicPricingEngine` **deferred** to the catalog tranche (depends on un-ported `ShopCatalog`). | **build+test SUCCESSFUL — 2/2** |
| Catalog | `ShopCatalog` (`MinecraftForge`→`NeoForge` on fire-and-forget `ShopReloadEvent` post), `ShopDefinitionLoader` (`FMLPaths` pkg), `AdminShopConfigWriter`, `TagDepartmentClassifier` (`ForgeRegistries.ITEMS.getValue`→`BuiltInRegistries.ITEM.getOptional(...).orElse(null)`, `getKey` direct), `DynamicPricingEngine`, + `AdminCategorySavedData` (pulled in; SavedData-migrated). | **build+test SUCCESSFUL — 2/2** |
| SavedData | Remaining 8 SavedData migrated (AdminShopToggle, Department, Franchise, PlayerShopRegistry, PlayerShopSettlement, ShopLimits, StockRefresh, TransactionHistory). **All 12 SavedData classes now done.** Gotcha: `computeIfAbsent` is multiline in several → whitespace-tolerant `perl -0777` (`\s*` between args), not line sed. | **build+test SUCCESSFUL — 2/2** |
| Coin vertical | `MoneyMintService`/`MoneyValidationService`(+rescue)/`MoneyItem` (NBT→`CoinData` component; `appendHoverText`→`Item.TooltipContext`; `MinecraftForge`→`NeoForge`), minimal `ModItems` (MONEY_ITEM only; `registerItem(name,factory,props)`), `EconomyCommandUtil`. Entrypoint rewritten minimal: register ITEMS+COMPONENTS, **`addAlias(futureshops:coin→money)`** (NeoForge MissingMappings replacement). **Tests-as-gate:** MDG `EphemeralTestServerProvider` boots a server with our registries → `CoinRescueTest` (3: stack-equivalence, tampered-fails, other-keys) + `LegacyCoinDataFixLandingTest` (1). | **build+test SUCCESSFUL — 6/6** |
| 2 | Data components: new `CoinData` record + `ModDataComponents` (`createDataComponents`/`registerComponentType`/`.persistent(CODEC)`/`.networkSynchronized(STREAM_CODEC)`). Codec field names = legacy `MoneyNbtKeys` strings; component id `futureshops:coin_data` (namespace continuity). 7-field hand-written `StreamCodec.of(encoder,decoder)` — composite() caps at 6; **confirms answer D: encoder-first**. Bus registration deferred to entrypoint tranche. | **compileJava SUCCESSFUL** — NeoForge data-component API compiler-verified real |

## Sequencing refinement (compile-unit order ≠ API-migration order)

The audit plan lists "registries first." But the registry classes (`ModItems`/`ModBlocks`/`ModBlockEntities`)
transitively reference `MoneyItem`→`MoneyValidationService`→`SpentMintsSavedData`/economy/events, and
`ShopBlock`/`ShopBlockEntity`→the session/service/networking layer — i.e. ~80% of the mod. So a registry
class cannot compile green until its referents exist. **Compile order is therefore bottom-up (leaves first),
even though the API-migration emphasis order from the audit is preserved.** Tranches, each ending green:
1. Leaf value/data types (catalog records, data DTOs, enums, `MoneyNbtKeys`, pure `MoneyChecksumService`, `MoneyMintRecord`, result codes) — import/API swaps only.
2. Data components (`CoinData` + registration).
3. SavedData base + economy/money services (incl. the ledger — MANDATORY STOP at `SpentMintsSavedData`).
4. Items/blocks/block-entity (BE needs GeckoLib dep; client renderer deferred to GUI step).
5. Registries + entrypoint wiring (now their referents exist).
6. Networking (batched), 7. GUI/render+GeckoLib, 8. commands/config, 9. events sweep, 10. RS2 compat.
