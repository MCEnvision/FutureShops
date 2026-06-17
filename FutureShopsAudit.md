# FutureShops — Phase 0 Port Audit

**Minecraft 1.20.1 / Forge 47.4.20 / Java 17  →  1.21.1 / NeoForge / Java 21**

- **Mod:** FutureShops (economy mod), modid `futureshops`, group `com.enviouse`, mod version 2.1
- **Audit basis:** 186 Java files / ~30k LOC in `SourceCodeOld/FutureShops`, every surface grepped at source and adversarially re-verified; external API facts verified against `docs.neoforged.net`, the NeoForge `1.21.1` git branch, and Maven metadata.
- **Status:** Phase 0 — no code written, old source untouched. **Holding for review.** Two blocking decisions (A, B) needed before Phase 1.
- **Target path for Phase 1:** `/mnt/hermes/projects/FutureShopsP` (repo root — already a partial NeoForge skeleton). `SourceCodeOld/` stays untouched.

---

## 0. Corrections made to the machine audit (verified against actual NeoForge sources)

| # | Machine audit said | Reality (verified) | Source |
|---|---|---|---|
| C1 | Port `MissingMappingsEvent` for `coin→money` | **That event does not exist in NeoForge 1.21.1** (0 hits in `neoforged/NeoForge@1.21.1`). Replacement is **`DeferredRegister.addAlias(from, to)`** — "lookups targeting the first name resolve as the second if the first is absent"; must run before `RegisterEvent` fires. | `git@neoforged/NeoForge:1.21.1` `DeferredRegister.java:303`; `IRegistryExtension.addAlias` |
| C2 | 13 SavedData classes | **12** (enumerated in §2.8) | `find -name '*SavedData.java'` |
| C3 | NeoForge "21.1.233 (latest stable)" — unconfirmed | **Confirmed** `21.1.233` is the highest non-beta `21.1.x` | `maven.neoforged.net` metadata |
| C4 | Parchment / SavedData.Factory unverified | **Confirmed**: Parchment `1.21.1:2024.11.17`; `DimensionDataStorage.computeIfAbsent(SavedData.Factory<T>, String)`, `Factory(Supplier, BiFunction<CompoundTag,HolderLookup.Provider,T>, @Nullable DataFixTypes)`, `load/save(CompoundTag, HolderLookup.Provider)` | `docs.neoforged.net/docs/1.21.1/datastorage/saveddata`; parchmentmc.org |

---

## 1. Executive Summary

### Target stack (exact, frozen-line versions)

| Component | Version | Notes |
|---|---|---|
| Minecraft | **1.21.1** | range `[1.21.1,1.22)` |
| NeoForge | **21.1.233** | latest stable `21.1.x`. Do **not** use 21.2+/3.x (different MC). |
| Build plugin | ModDevGradle **`net.neoforged.moddev` 2.0.x** | no NeoGradle, **no MixinGradle** (mixin is built in) |
| Gradle | **9.x** | wrapper bump from 8.14 |
| Java | **21** | toolchain 17→21; mixins.json `JAVA_8`→`JAVA_21` |
| Parchment | **`parchment-1.21.1:2024.11.17`** | ⚠ skeleton currently pins `1.21.11/2025.12.20` — **wrong MC line, must fix** |
| GeckoLib | **`geckolib-neoforge-1.21.1:4.8.4`** (Cloudsmith) | in-line bump from 4.8.3; drop `mclib`. Stay on 4.8.x. |
| Refined Storage | **2.0.9** (`refinedstorage`, stable) | full API rewrite; soft-dep |

### The blocking decision — modid / namespace (save-compat critical)

The old mod's runtime identity is **`futureshops`** everywhere: items `futureshops:money` (+ legacy `futureshops:coin`), block `futureshops:shop_block`, channel `futureshops:main`, coin data root `futureshops:coin_data`. **The repo-root skeleton renames the modid to `futureshopsp`.** If the modid actually ships as `futureshopsp`, *every* registry object gets a new ID and **every coin, every placed shop block, in every existing world turns to air** unless individually aliased.

**Recommendation:** keep the **modid and all resource/data namespaces as `futureshops`** (the Java package can stay `com.enviouse.futureshopsp` and the project folder `FutureShopsP` — neither affects runtime IDs). That reduces save-compat to the single, already-planned `coin→money` alias. **This is Decision A in §6.**

### Five highest-risk items

1. **Coin NBT → DataComponent with no automatic legacy migration (CRITICAL).** New coins are clean; coins minted under the old jar are not auto-converted — without a rescue path they fail `validate()`. The checksum salt and the `futureshops:coin_data` namespace must stay byte-identical. (`MoneyMintService.java:41`, `MoneyValidationService.java:21`) — deep-dive §3.
2. **`post()`-as-boolean cancellation breaks compilation (CRITICAL, ≥6 hard sites + ~40 renames).** NeoForge `post(E)` returns the event. Every `if (EVENT_BUS.post(pre))` gate (economy debit/credit, buy/sell/barter) must become `post(e); if (e.isCanceled())`. (`InternalEconomyProvider.java:50,82`; `ShopBuyService.java:176`; `ShopSellService.java:131`; `ShopBarterService.java:158,165`)
3. **`HandshakeHandlerMixin` is unportable → DROP it.** Targets Forge-only `HandshakeHandler`/`HandshakeMessages`/`NetworkEvent`. Deleted; version gate moves to `PayloadRegistrar.versioned("24")` (mandatory → mismatches still refused). No economic/security loss. Documented omission, no redesign.
4. **Refined Storage compat is a rewrite, not a port (CRITICAL).** The entire RS1 reflection chain is gone in RS2 2.0.x; `compat/rs2` must be rebuilt against `Network`/`StorageNetworkComponent`. Soft-dep + `ModList` guard. Exact accessor chain unverified.
5. **SavedData signature break across all 12 classes (HIGH) — incl. the anti-dupe ledger.** Enumerated in §2.8; `SpentMintsSavedData` gets coin-level scrutiny (show `consume()` + load/save round-trip before/after before moving on).

---

## 2. Per-API-surface tables

`⚠CHECK` = real-but-unconfirmed signature → verify against the NeoForge 1.21.1 MDK/source in Phase 1 *before* writing, never guess. `MISSED` = found only by the adversarial verify pass.

### 2.1 Build / entrypoint / config

| Surface | file:line | NeoForge 1.21.1 replacement | Risk | Notes |
|---|---|---|---|---|
| ForgeGradle plugin | `build.gradle:15` | `id 'net.neoforged.moddev' version '2.0.x'` + `neoForge{}` | CRITICAL | |
| MixinGradle buildscript + `apply mixin` + AP + `mixin{}` | `build.gradle:7-9,18,124-128,183` | delete all; mixin built-in | HIGH | register via toml `[[mixins]]` |
| Java toolchain 17 | `build.gradle:28` | `JavaLanguageVersion.of(21)` | HIGH | |
| Forge MC dep + `fg.deobf()` + `reobfJar` | `build.gradle:161,180,227` | `neoForge{ version=neo_version }`; plain `implementation`; delete reobf | CRITICAL | |
| GeckoLib flatDir + mclib | `build.gradle:180-181` | `implementation 'software.bernie.geckolib:geckolib-neoforge-1.21.1:4.8.4'`; drop mclib; re-add Cloudsmith maven | HIGH | |
| processResources expand | `build.gradle:200-212` | `generateModMetadata` from `src/main/templates`; `neo_version[_range]` | MEDIUM | |
| settings/wrapper/gradle.properties | `settings.gradle:4-7`, `gradle-wrapper.properties:3`, `gradle.properties` | NeoForge maven + foojay; Gradle 9.x; MC/neo/parchment coords | HIGH | **Parchment → `1.21.1/2024.11.17`** (skeleton wrong) |
| mods.toml → neoforge.mods.toml | `mods.toml` | `META-INF/neoforge.mods.toml`; dep `modId="neoforge" type="required"`; `[[mixins]]`; **add geckolib + optional RS deps** | CRITICAL | template exists; deps missing |
| pack.mcmeta pack_format 15 | `pack.mcmeta:4` | `34` for 1.21.1 | MEDIUM | ⚠CHECK |
| `@Mod` ctor + `FMLJavaModLoadingContext` | `Futureshops.java:43-44` | constructor injection `(IEventBus modEventBus, ModContainer modContainer)` | CRITICAL | |
| `ModLoadingContext.registerConfig` | `Futureshops.java:62` | `modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC)` | HIGH | |
| `ForgeConfigSpec` → `ModConfigSpec` | `Config.java:3,10,109,…` | `net.neoforged.neoforge.common.ModConfigSpec.*` | HIGH | |
| `MinecraftForge.EVENT_BUS` | `Futureshops.java:56-59` | `NeoForge.EVENT_BUS` | HIGH | |
| `InterModComms`/`InterModEnqueueEvent` (MISSED) | `Futureshops.java:24,32,78,80` | `net.neoforged.fml.*` package moves; CarryOn IMC | MEDIUM | ⚠CHECK IMC name |
| `TickEvent.ServerTickEvent` (phase==END) | `Futureshops.java:112-113` | `ServerTickEvent.Post` | HIGH | ⚠CHECK `getServer()` on `.Post` |
| **`coin→money` legacy remap** | `Futureshops.java:56,127-134` | **`ModItems.ITEMS.addAlias(rl("futureshops","coin"), rl("futureshops","money"))` before `register(bus)`** — *not* MissingMappingsEvent | **CRITICAL** | **save-compat; VERIFIED (C1)** |

### 2.2 Registration

| Surface | file:line | NeoForge replacement | Risk | Notes |
|---|---|---|---|---|
| Block/Item/BE/Tab DeferredRegister | `ModBlocks.java:8-15`, `ModItems.java:7-21`, `ModBlockEntities.java:6-14`, `ModCreativeTabs.java:8-24` | `DeferredRegister.Blocks/Items/create(Registries.X, modid)` + `DeferredHolder`/`DeferredBlock`/`DeferredItem`; `register(modEventBus)` | MEDIUM | ⚠CHECK convenience names + raw-path `Properties.setId` |
| `ForgeRegistries.ITEMS.getValue` (~13 sites) | `InventorySyncService.java:12,46`; `LocalShopAggregator.java:16,116`; `ShopTransactionUtil.java:9,39`; `TagDepartmentClassifier.java:8,116`; `ShopUiUtil.java:21,191,217,255,280,543,608`; `MarketplaceAnalyticsService.java:266` | **`BuiltInRegistries.ITEM.getOptional(rl).orElse(null)`** — *not* `.get()` (returns AIR, breaks null-checks) | MEDIUM | behavior-preserving correction |
| `ForgeRegistries.ITEMS.getKey/getKeys` | `AdminShopWizard.java:15,57`; `ShopAdminCommand.java:38,116,435,476`; `PlayerShopBlockService.java:33,181,221,304,859,1103`; `ShopBarterService.java:151`; `TagDepartmentClassifier.java:128`; `PlayerShopBlockScreen.java:1085` | `BuiltInRegistries.ITEM.getKey` / `.keySet()` — returns **air key, never null**; re-express "unknown" branches as air compare | MEDIUM | |
| `ForgeRegistries.BLOCK_ENTITY_TYPES.getKey` | `RefinedStorage2StorageAdapter.java:450`; `PlayerShopBlockService.java:1396,1406` | `BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey` (genuinely nullable) | LOW | |
| `@Mod.EventBusSubscriber` sites (MISSED) | `Config.java:8`; `Futureshops.java:137`; `SessionEventHandler.java:30`; `ModCommandEvents.java:8`; `AdminShopWizard.java:29` | `net.neoforged.fml.common.EventBusSubscriber`; `Bus.FORGE`→`Bus.GAME` | MEDIUM | |

### 2.3 Events

| Surface | file:line | NeoForge replacement | Risk | Notes |
|---|---|---|---|---|
| `Event` base + `@Cancelable` (9 classes) | `event/*` (BalanceChange, BarterTrade, ShopOpen, ShopTransaction, MoneyDeposit, MoneyMint, ShopClose, ShopReload, StockRefresh) | `extends net.neoforged.bus.api.Event`; `@Cancelable` → `implements ICancellableEvent` (the 4 cancel-checked ones) | HIGH | |
| **`post()` as boolean (6 hard sites)** | `InternalEconomyProvider.java:50,82`; `ShopBarterService.java:158,165`; `ShopBuyService.java:176`; `ShopSellService.java:131` | `post(e); if (e.isCanceled()) return …CANCELLED` | CRITICAL | won't compile otherwise |
| ~40 other `EVENT_BUS.post/addListener/register` (MISSED across dims) | `ShopModAPI.java:203`; `ShopCatalog.java:102`; `MoneyItem.java:81`; `ShopSessionManager.java:74,96`; `PlayerShopBlockService.java:69,629,700,781,1034,1612,…`; `ShopDataService.java:38-39`; cmd sites | `NeoForge.EVENT_BUS` + boolean→`isCanceled()` where gated | MEDIUM/CRIT | exhaustive sweep needed |
| `LivingHurtEvent` | `SessionEventHandler.java:62` | `LivingIncomingDamageEvent` **or** `LivingDamageEvent.Pre` | HIGH | ⚠CHECK — removed in NeoForge, semantics differ |
| `TickEvent.PlayerTickEvent` | `SessionEventHandler.java:73-76` | `PlayerTickEvent.Post`; replace `event.player/phase` | HIGH | ⚠CHECK `getEntity()` vs `getPlayer()` |
| `RegisterCommandsEvent` | `ModCommandEvents.java:4,14` | `net.neoforged.neoforge.event.RegisterCommandsEvent`; `Bus.GAME` | MEDIUM | |

### 2.4 Money / NBT → DataComponents (summary — full deep-dive §3)

| Surface | file:line | NeoForge replacement | Risk | Notes |
|---|---|---|---|---|
| `MoneyNbtKeys` ROOT+7 keys | `MoneyNbtKeys.java:4-11` | `CoinData` record + `registerComponentType("coin_data",…)` (namespace stays `futureshops`) | MEDIUM | |
| `mintStack` getOrCreateTag | `MoneyMintService.java:30-41` | `stack.set(COIN_DATA, new CoinData(...))` | HIGH | |
| `validate` checksum (crown jewel) | `MoneyValidationService.java:21-68` | `stack.get(COIN_DATA)`; decode-null → `MISSING_COIN_DATA` | HIGH | |
| `createChecksum` SHA-256 + salt | `MoneyChecksumService.java:19-24` | **no change** — salt byte-identical | LOW | |
| `appendHoverText` | `MoneyItem.java:99-110` | component read + signature → `Item.TooltipContext` | MEDIUM | ⚠CHECK signature |
| **`NbtMatchUtil.matches/tagsEqual` (equality semantics)** | `NbtMatchUtil.java:21-39` | `isSameItemSameComponents` **or** `DataComponentPatch` compare — see §4 semantics flag | HIGH | ⚠CHECK representation decision |
| `isSameItemSameTags` (2×) | `ShopTransactionUtil.java:183,220` | `isSameItemSameComponents` | LOW | |
| NbtMatch consumers + setTag probes | `ForgeCapabilityStorageAdapter.java:43,62,82`; `RefinedStorage2StorageAdapter.java:269,272,384,397,411`; `PlayerShopBlockService.java:1171,1303,1428,1445` | component-aware compare / `DataComponentPatch` | HIGH | ⚠CHECK |
| Listing NBT persistence | `ShopBlockEntity.java:47-63,580,592,654-655`; `ShopConfigClipboard.java:22`; admin SNBT `AdminShopWizard.java:77`, `ShopAdminCommand.java:523` | `DataComponentPatch.CODEC` / SNBT→component | HIGH | ⚠CHECK |
| **No auto-migration of old coins** | `MoneyMintService.java:41`, `MoneyValidationService.java:21` | manual legacy rescue (likely `minecraft:custom_data`) **or** DataFixer | CRITICAL | Decision B (§6) |
| Per-batch stacking | `MoneyMintService.java:16-25` | unchanged — shared `CoinData` value stays stackable | LOW | |
| FALSE POSITIVE (don't touch) | `ShopBlockEntity.java:953` | `ClientboundBlockEntityDataPacket.getTag()` — SavedData/Provider, not components | — | |

### 2.5 Capabilities / storage linking

| Surface | file:line | NeoForge replacement | Risk |
|---|---|---|---|
| Forge caps imports + `getCapability(ForgeCapabilities.ITEM_HANDLER).resolve()` | `ForgeCapabilityStorageAdapter.java:8-9,27-29,120-122` | `level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side)` → **nullable** `IItemHandler` (`net.neoforged.neoforge.items`); no `LazyOptional` | HIGH |
| Link resolvers (query on **Level**, not BlockEntity) | `PlayerShopBlockService.java:31-32,1325-1329,1353-1357,1384-1386,1522` | same; derive `level`+`pos` from BE → **`ExternalStorageAdapter` SPI must take Level/pos** | HIGH |
| RS2 caps imports + sided fallback | `RefinedStorage2StorageAdapter.java:11-12,464-473` | same capability query loop | MEDIUM |
| Adapter registration | `Futureshops.java:65-71` | keep in `FMLCommonSetupEvent`; consume-only → **no `RegisterCapabilitiesEvent`** | LOW |
| Shop block exposes **no** capability | `ShopBlockEntity.java` (whole) | no action (hopper/pipe interop = it *reads* others, doesn't expose) | LOW |

### 2.6 Menu / GUI open

| Surface | file:line | NeoForge replacement | Risk |
|---|---|---|---|
| **No vanilla menu API anywhere** (0 `MenuType`/`IForgeMenuType`/`openMenu`) | tree | all GUIs are custom `Screen`s opened by S2C packets — **do not introduce menus** | LOW |
| `ShopBlock.use` override | `ShopBlock.java:161` | `useWithoutItem(...)` (+ `useItemOn` → `ItemInteractionResult`) | MEDIUM |
| `Minecraft.setScreen` from S2C / in-screen nav | `ShopClientPacketHandler.java:74,91,127,183,206,279,307`; `ShopMainScreen.java:103,719`; etc. | unchanged; rewire to payload handlers | LOW |
| `Minecraft.tell(Runnable)` | `PlayerShopBlockScreen.java:171`; `FranchiseManagementScreen.java:356` (MISSED) | survives / `mc.execute` | LOW |

### 2.7 Networking / payloads

| Surface | file:line | NeoForge replacement | Risk |
|---|---|---|---|
| `SimpleChannel`/`NetworkRegistry` | `ShopPackets.java:54-59` | `RegisterPayloadHandlersEvent` + `registrar.versioned("24")` (mandatory) | HIGH |
| `messageBuilder()` ×N + `nextId` | `ShopPackets.java:66-295` | `registrar.playToServer/playToClient(TYPE, STREAM_CODEC, ::handle)` — **37 registered: 21 C2S + 16 S2C** (≈40 packet *classes* on disk) | HIGH |
| `encode/decode(FriendlyByteBuf)` | every packet | implement `CustomPacketPayload` + `Type<T>` + `StreamCodec<RegistryFriendlyByteBuf,T>`; `StreamCodec.unit` for empties | HIGH ⚠CHECK `StreamCodec.of` arg order |
| `getSender`/`setPacketHandled`/`DistExecutor` | `C2SBuyRequestPacket.java:66-75`; `S2CShopDataPacket.java:105-111`; S2C client refs `S2CDepartmentListPacket.java:37-42`, `S2CFranchiseDataPacket` (MISSED) | `handle(pkt, IPayloadContext)`; `(ServerPlayer) ctx.player()`; client-only handler registration | HIGH ⚠CHECK `ctx.player()` is sender server-side |
| `CHANNEL.send(PacketDistributor.PLAYER.with(...))` | `ShopPackets.java:301-303` | `PacketDistributor.sendToPlayer(player, payload)` / `sendToServer` | CRITICAL |
| `new ResourceLocation(...)` | mixin + various | `ResourceLocation.fromNamespaceAndPath` (`ShopPackets` already uses `.parse`) | LOW |

### 2.8 SavedData — per-class enumeration

All 12 use the **old** `computeIfAbsent(loadFn, ctor, NAME)` + `load(CompoundTag)` / `save(CompoundTag)`. Each must become `save(CompoundTag, HolderLookup.Provider)`, static `load(CompoundTag, HolderLookup.Provider)`, and `computeIfAbsent(new SavedData.Factory<>(Ctor::new, Cls::load, /*DataFixTypes*/ null), NAME)`. **`SavedDataType<T>` is 1.21.5+ — do NOT use on 21.1.233.** All payloads are primitive/UUID/String → the `Provider` arg is threaded but unused.

| # | Class | file | load | save | computeIfAbsent | Risk |
|---|---|---|---|---|---|---|
| 1 | **`SpentMintsSavedData`** (anti-dupe ledger) | `money/SpentMintsSavedData.java` | :44 | :83 | :108 | **HIGH — coin-level care** |
| 2 | `InternalBalanceSavedData` | `server/economy/` | :20 | :35 | (via `BalanceManager`) | HIGH |
| 3 | `DynamicPricingSavedData` | `server/pricing/` | ::load@27 | :61 | :27 | MEDIUM |
| 4 | `AdminCategorySavedData` | `server/shop/` | ::load@42 | :192 | :42 | MEDIUM |
| 5 | `AdminShopToggleSavedData` | `server/shop/` | :43 | :37 | :51 | MEDIUM |
| 6 | `DepartmentSavedData` | `server/shop/` | ::load@29 | :80 | :29 | MEDIUM |
| 7 | `FranchiseSavedData` | `server/shop/` | :197 | :175 | :222 | MEDIUM |
| 8 | `PlayerShopRegistrySavedData` | `server/shop/` | :24 | :49 | :71 | HIGH |
| 9 | `PlayerShopSettlementSavedData` | `server/shop/` | :27 | :72 | :110 | MEDIUM |
| 10 | `ShopLimitsSavedData` | `server/shop/` | :22 | :34 | :43 | LOW |
| 11 | `StockRefreshSavedData` | `server/shop/` | ::load@28 | :43 | :28 | MEDIUM |
| 12 | `TransactionHistorySavedData` | `server/transaction/` | :26 | :55 | :81 | MEDIUM |

`SavedDataMigrations.java:31-39` (version read/write helpers) — **unchanged** (vanilla `CompoundTag` getters survive). When I reach #1 I will **stop and show the before/after of `consume()` and a load→save→load round-trip** before continuing.

Other Forge-API in this dimension: `FMLPaths.CONFIGDIR` → `net.neoforged.fml.loading.FMLPaths` (`ShopDefinitionLoader.java:7,40,52`); `/shopadmin reload` uses vanilla `hasPermission(2)` — unchanged.

### 2.9 GeckoLib / client renderers

| Surface | file:line | NeoForge replacement | Risk |
|---|---|---|---|
| `IClientItemExtensions#initializeClient` | `ShopBlockItem.java:7,22-35` | `RegisterClientExtensionsEvent.registerItem(ext, item)` | HIGH ⚠CHECK |
| renderer registration | `Futureshops.java:29,137,141,147-152` | `EntityRenderersEvent.RegisterRenderers.registerBlockEntityRenderer`; NeoForge package moves; + `RegisterClientExtensionsEvent` | HIGH ⚠CHECK |
| GeoBlockEntity/cache/controllers + GeoBlockRenderer | `ShopBlockEntity.java:14-21,30,32,854-867`; `ShopBlockGeoRenderer.java:29,47-59` | unchanged (4.8.x, jar-verified) | LOW |
| `new ResourceLocation` / `ForgeRegistries.ITEMS` | `ShopBlockGeoModel.java:10,12,14`; `ShopBlockGeoRenderer.java:27,73` | `fromNamespaceAndPath` / `BuiltInRegistries.ITEM` | MEDIUM |
| VertexConsumer builder chain | `ShopBlockGeoRenderer.java:187-188,218-227` | `addVertex(Matrix4f,…).setColor().setUv()…`; no `endVertex()` | HIGH ⚠CHECK |
| `ItemRenderer#renderStatic` / BEWLR `renderItem` | `ShopBlockGeoRenderer.java:92-100`; `ShopBlockItemRenderer.java:24-50` | verify arity vs item-render-state refactor | MEDIUM ⚠CHECK |
| **Owner-head skin pipeline (CRITICAL MISS)** | `ShopBlockGeoRenderer.java:6,246,256,259-261` | `MinecraftProfileTexture` gone; `DefaultPlayerSkin.getDefaultSkin(UUID)`→`PlayerSkin`; `SkinManager.getOrLoad(GameProfile)→CompletableFuture<PlayerSkin>`; `PlayerSkin.texture()` | HIGH ⚠CHECK |

### 2.10 Refined Storage compat

| Surface | file:line | NeoForge replacement | Risk |
|---|---|---|---|
| RS1 reflection chain (`INetwork`/`IStorageCache`/`IStackList`/`Action`) | `RefinedStorage2StorageAdapter.java:94-135,229-259,277-374` | rewrite vs `Network.getComponent(StorageNetworkComponent.class)` + `insert/extract(ResourceKey, long, Action, Actor)` via `ItemResource` | CRITICAL ⚠CHECK chain |
| `ModList` guard | `RefinedStorage2Compat.java:6,30,56` | `net.neoforged.fml.ModList` (same shape) | — |
| Forge caps + getTag + ForgeRegistries | `RefinedStorage2StorageAdapter.java:11,12,269,272,308,320,450,466,469` | caps on Level; components; `BuiltInRegistries` | HIGH |
| bad mod id `refinedstorage2` (MISSED) | `RefinedStorage2StorageAdapter.java:42-46`; `RefinedStorage2Compat.java:19,30` | RS2 reuses modid **`refinedstorage`** — drop `refinedstorage2` | MEDIUM |

### 2.11 UI rendering — no-external-textures guarantee **holds** ✓

Tree-wide grep: **zero** `setShaderTexture`/`TextureManager`/`Tesselator`/`BufferBuilder`, **zero** `AbstractContainerScreen`, only ARGB `ShopColors` constants. The only `blit` is the runtime player skin. The real (non-texture) breaks: `GuiGraphics.blit` RenderType overload (`ShopUiUtil.java:479,481` ⚠CHECK whether this lands in 1.21.1 vs .2), `getTooltipLines(Item.TooltipContext,…)`, the `ForgeRegistries`→`BuiltInRegistries` AIR-guard fixes, and component/skin accessors.

---

## 3. Deep-dive: Coin anti-dupe / NBT → DataComponent

**Today (1.20.1):** `/withdraw` → `MoneyMintService.mintStack` packs `{denomination, mint_id(=per-batch UUID), mint_timestamp, mint_player, mint_server, authorized_count, checksum}` into a `CompoundTag` under root `futureshops:coin_data`; all coins in a batch share identical NBT. `MoneyChecksumService` = `SHA-256(denom|mintId|ts|player|server|authorizedCount|salt)` (storage-agnostic). `validate()` checks structure + recomputes checksum. **The dupe-proofing is `SpentMintsSavedData.consume()`** — a world ledger keyed by batch `mint_id` that atomically decrements `remaining_count`, so N cloned stacks can never redeem more than `authorized_count` total. **This enforcement is world data, not item data.**

**Proposed component:**

```java
public record CoinData(long denomination, String mintId, long mintTimestamp,
                       String mintPlayer, String mintServer, int authorizedCount, String checksum) {}
// DeferredRegister.createDataComponents("futureshops")  // namespace MUST stay "futureshops"
// COIN_DATA = registerComponentType("coin_data", b -> b.persistent(COIN_CODEC).networkSynchronized(COIN_STREAM_CODEC));
```

Mint → `stack.set(COIN_DATA, …)`; validate → `var d = stack.get(COIN_DATA); if (d==null) error("MISSING_COIN_DATA")`; checksum recompute unchanged. `registerComponentType/persistent/networkSynchronized/get/set/isSameItemSameComponents` are confirmed-real API.

**Can the guarantee be preserved functionally identical?**

- **New coins: yes — arguably strengthened.** Salt and ledger carry over byte-for-byte; a tampered/partial component fails Codec decode → `null` → rejected at least as hard as today. *Because the ledger is independent of item storage, the dupe-proofing itself is untouched by the NBT→component change* — this is the finding that de-risks the port.
- **Pre-existing coins: NOT automatic (CRITICAL).** No auto-migration from legacy NBT; on an in-place world upgrade the old `coin_data` compound likely lands in `minecraft:custom_data`, and (if the modid also changes) under the wrong namespace. **Decision B (§6).**

**Stacking:** `mint_id` is unique **per batch, shared across the batch**, so coins stack iff their `CoinData` is value-equal (within a batch) — exactly today's behavior. The "unique component ⇒ unstackable" caveat does **not** bite. Preserving per-batch (not per-item) sharing is mandatory.

---

## 4. Cross-cutting risks

- **Handshake mixin — DROP:** deleted, no redesign; `registrar.versioned("24")` keeps the anti-desync refusal; the friendly "update the mod" screen is a deliberate, documented omission (first `PORTING_NOTES.md` entry in Phase 1). Open-question on a custom disconnect message is therefore **closed (won't-do)**.
- **Equality-semantics flag:** moving `Objects.equals(CompoundTag,CompoundTag)` → component equality changes what "equal item" means. It governs **admin multi-variant listing matching, coin matching in barter/sell, RS2 `stackMatches`, and `ShopTransactionUtil` line merges.** Two concrete behavior risks to validate, not just compile: (a) the "required NBT" criterion persisted in JSON config must round-trip to a `DataComponentPatch` representation with identical match results; (b) the empty-tag-vs-empty-patch edge (`null`/`{}` today) must not start matching/rejecting differently. Diff match outcomes on real listings before declaring done.
- **RS soft-dep:** RS2 2.0.9, `type='optional'`, `ModList.isLoaded("refinedstorage")`, isolated compat class; generic-`IItemHandler` fallback only reaches DiskDrive/Interface (partial counts) — likely drop once the network path lands.
- **SavedData (12) + the ledger:** mechanical but pervasive; ledger gets coin-level scrutiny.
- **No-external-textures guarantee:** verified intact.

---

## 5. Ordered porting plan (each step ends at a `./gradlew build` gate)

**Target path:** `/mnt/hermes/projects/FutureShopsP` (repo root — already a partial NeoForge skeleton; `SourceCodeOld/` stays untouched). `PORTING_NOTES.md` created at Step 0 commit.

- **Step 0 — Skeleton + metadata, build EMPTY first.** Fix the skeleton: ModDevGradle/`neoForge{}`, **Parchment → 1.21.1/2024.11.17**, Gradle 9.x, GeckoLib+Cloudsmith, add geckolib + optional-RS deps to `neoforge.mods.toml`, mixins.json `JAVA_21` with **empty `mixins[]` (delete HandshakeHandlerMixin)**, **resolve Decision A (modid)**. Gate: empty/near-empty tree configures + builds; GeckoLib/RS jars resolve.
- **Step 1 — Entrypoint + registries.** `@Mod` ctor injection; `NeoForge.EVENT_BUS`; `ModConfigSpec`; 4 DeferredRegisters; `ForgeRegistries`→`BuiltInRegistries` (AIR-guard rewrites); **`addAlias("futureshops:coin"→"money")`**; `EventBusSubscriber` rename. Gate: dev boot, items/blocks/BE/tab register, no AIR regressions.
- **Step 2 — Data components (coin model).** `CoinData` + Codec/StreamCodec (field names = legacy keys, namespace `futureshops`); migrate mint/validate/withdraw/deposit/tooltip; **decide & apply equality representation**; **implement legacy-coin rescue per Decision B.** Gate: mint→deposit accepts; tamper→`MISSING_COIN_DATA`; same-batch stacks; **pre-port world behaves per Decision B.**
- **Step 3 — Capabilities.** caps on Level at all sites; `ExternalStorageAdapter` SPI takes Level/pos. Gate: link a chest; count/extract/insert work.
- **Step 4 — Networking, in BATCHES, build per batch.**
  - (4a) shop-open/data: `C2SOpenShop`, `S2CShopData`, `S2CForceClose`
  - (4b) transactions: `C2SBuy/Sell/Barter` + `S2CBuy/Sell/Barter` + `C2SVerifyCart`/`C2SVerifyAdminCart`/`S2CVerifyCartResponse`
  - (4c) player-shop: `C2SPlayerShopAction/Buy/Sell/Config/Promo/BuybackConfig`, `S2CPlayerShopData/Result`, `C2SInventorySync`/`S2CInventorySync`
  - (4d) departments/local: `C2SSetDepartment`/`C2SFetchDepartments`/`S2CDepartmentList`, `C2SFetchLocalShops`/`S2CLocalShops`
  - (4e) balance/history: `C2SOpenBalanceUi`/`C2SOpenBalTopUi`/`S2CBalanceUi`/`S2CBalTopUi`, `C2SFetchHistory`/`S2CHistoryResponse`, `C2SFetchSettlementHistory`/`S2CSettlementHistory`
  - (4f) franchise: `C2SFranchiseAction`/`S2CFranchiseData`
  - Each batch: `CustomPacketPayload`+`StreamCodec`, register, wire `handle(pkt,ctx)`, **build**. Gate: `WireRoundTripTest`-style round-trip per batch; protocol-version mismatch refuses connection.
- **Step 5 — GUI render + GeckoLib.** `ShopBlock.use`→`useWithoutItem/useItemOn`; GuiGraphics/tooltip; renderer registration + `RegisterClientExtensionsEvent` + **owner-head skin rewrite** + VertexConsumer rename. Gate: every screen, item icons, tooltips, animated block, player-face decal render.
- **Step 6 — Commands / config.** `RegisterCommandsEvent`/`Bus.GAME`; `FMLPaths`. Gate: 11 commands register; `/shopadmin reload` reloads JSON.
- **Step 7 — Events.** 9 event classes; **all `post()`-boolean → `post();isCanceled()`**; `LivingHurt`/`TickEvent` replacements. Gate: cancellation blocks a transaction; session force-close on damage; tick scheduler fires; `coin→money` alias loads on a real world.
- **Step 8 — RS2 compat + SavedData (12).** Rewrite `compat/rs2` behind the guard; migrate all 12 SavedData (ledger shown first). Gate: with RS works / without RS loads clean; all `.dat` survive a restart (ledger persists, coins stay consumed).
- **Final gate:** `./gradlew build runClient runGameTestServer` green + manual economy-loop play-test.

---

## 6. Open questions / decisions needed before Phase 1

### Blocking (need a decision)

- **A. Modid / namespace.** Keep runtime modid + resource/data namespaces as **`futureshops`** (recommended — minimal save-compat surface), or actually ship **`futureshopsp`** (then every registry object must be aliased + the component/data namespaces migrated)? The skeleton currently says `futureshopsp`.
- **B. Legacy-coin rescue policy.** (a) rescue old `coin_data` from `minecraft:custom_data` inside `validate()`; (b) ship a DataFixer; or (c) accept that pre-port coins are invalidated (economy reset). The actual data-landing location will be confirmed during Step 2 regardless.

### Needs live NeoForge-source confirmation in Phase 1 (will verify, won't guess)

- **C.** `IPayloadContext.player()` returns the sending `ServerPlayer` server-side (21 C2S casts depend on it).
- **D.** `StreamCodec.of`/`composite` argument order.
- **E.** RS2 2.0.9 `BlockEntity→Network` accessor chain + API FQNs + maven repo/auth.
- **F.** Owner-head skin API set (`SkinManager.getOrLoad`, `DefaultPlayerSkin.getDefaultSkin→PlayerSkin`, `ResolvableProfile`?).
- **G.** `LivingHurtEvent`→`LivingIncomingDamageEvent` vs `LivingDamageEvent.Pre`; `PlayerTickEvent.Post`/`ServerTickEvent.Post` accessors.
- **H.** NeoForge convenience registry factories (`DeferredBlock`/`registerSimpleBlockItem`) + raw-path `Properties.setId`.
- **I.** `RegisterClientExtensionsEvent.registerItem` arity; VertexConsumer rename set; `pack_format` (34?) for 1.21.1.
- **J.** `appendHoverText(ItemStack, Item.TooltipContext, List<Component>, TooltipFlag)` exact 1.21.1 signature.
- **K.** `ShopBlock.use` split — whether to map to `useWithoutItem` only, or also `useItemOn` (held-item path), and the `InteractionResult`/`ItemInteractionResult` return types.
- **L.** `InterModComms` / CarryOn IMC method names on NeoForge 1.21.1 (and whether the `data/carryon` datapack tag is the preferred path instead).
- **M.** `GuiGraphics.blit` RenderType-first overload — confirm it applies on 1.21.1 (vs 1.21.2+).
- **N.** `PacketDistributor.sendToPlayer`/`sendToServer` exact names + whether a separate client-side distributor is required.

### Resolved by this audit (no longer open)

- MissingMappings → **`DeferredRegister.addAlias`** (C1).
- NeoForge **`21.1.233`** (latest stable 21.1.x).
- Parchment **`1.21.1:2024.11.17`**.
- SavedData **`Factory` + `HolderLookup.Provider`** shape (and `SavedDataType` is 1.21.5+, do not use).
- Friendly-disconnect message → **dropped with the mixin** (deliberate, documented omission).

---

## 7. Sources (externally-verified facts)

- [NeoForge SavedData docs (1.21.1)](https://docs.neoforged.net/docs/1.21.1/datastorage/saveddata/)
- [ParchmentMC getting-started](https://parchmentmc.org/docs/getting-started.html)
- [ModDevGradle](https://projects.neoforged.net/neoforged/moddevgradle)
- NeoForge `1.21.1` branch source (`DeferredRegister.addAlias` — `DeferredRegister.java:303`)
- `maven.neoforged.net` release metadata (`21.1.233` is the latest stable `21.1.x`)
