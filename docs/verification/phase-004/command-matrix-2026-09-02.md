# Phase 004 command behavior matrix

The matrix below is bound to Forge revision `254a8788aa9a1d2f228abd84665882de5b69c075`. Shared command helpers are expanded into every executable family so that an alias does not hide a different permission or target path.

| Executable family | Leaves and arguments | Source and permission | Target and mutation | Confirmation, idempotency and recovery |
| --- | --- | --- | --- | --- |
| `/shop`, `/market`, `/bazaar`, `/ah`, `/auctionhouse` | Root open, browse, create, mine, bids, watched, claims, history, products, buy, sell, orders and portfolio where offered by the module | Player source and module permission checks in the market service | The requesting player and the selected module route | Read only opens are repeatable. Mutations remain behind packet validation and escrow readiness. |
| `/claims`, `/claimall`, `/escrow` | Root and auction or bazaar claim views | Player source, claim access remains available during module freeze | The requesting player claims | Collection is request identified and durable. Delivery failure leaves a claim. |
| `/balance`, `/bal`, `/baltop` | Root, UI and optional page | Player source | Own balance or public leaderboard view | Read only and repeatable. |
| `/pay` | `status`, player and amount transfer | Player source, target validation and economy service | Named online or offline player according to provider rules | Request UUID and escrow journal prevent duplicate payment. |
| `/atm` | Root ATM screen | Player source and ATM readiness | Own physical currency | Deposit and withdrawal use bounded packets and durable recovery. |
| `/deposit`, `/withdraw` | Root, amount and optional mode | Player source and positive amount bounds | Own wallet and physical currency | Exact request identity and claim fallback protect partial failure. |
| `/sellall` | `adminshop`, `playershops`, optional `confirm` | Player source | Own eligible inventory and selected shop target | Without confirm, the quoted review screen is required. With confirm, the same server quote is committed without bypassing validation. |
| `/shopadmin` | `reload`, `promo set`, `promo clear`, `coinaudit` | Operator level 2 at root plus service checks | Catalog and economy administration | Reload is read and apply of last valid state. Promotional edits use validated fields and durable audit output. |
| `/shopadmin adminshop` | `toggle`, `add`, `remove` | Operator level 2 and admin shop service | Server catalog | Add and remove are validated and reload atomically. |
| `/shopadmin items` | `add`, `edit`, `remove`, `list`, `info`, `refresh`, `barter off`, `sale` with listing identifiers | Operator level 2 and catalog service | One listing or bounded listing query | Listing edits use stable identifiers. Invalid writes preserve the previous catalog. |
| `/shopadmin respond` | Wizard response arguments | Operator level 2 and active wizard identity | The requesting operator's wizard session | Response identity and timeout prevent cross session writes. |
| `/shopadmin bal` | `add`, `remove`, `set`, `check`, `reset` with target and amount | Operator level 2 plus economy service | Named player wallet | Amounts use checked minor units. Mutations are journaled and repeat safe by request identity. |
| `/shopadmin view` | Player target | Operator level 2 | Read only player view | No mutation and no private data beyond the authorized operator view. |
| `/shopadmin category` | `add`, `remove`, `restore`, `list`, `assign`, `unassign`, `items` | Operator level 2 and catalog category service | Catalog category or listing assignment | Validation and atomic reload preserve last valid state. |
| `/shopadmin limits` | `maxlistings`, `maxblocks`, `info`, player info | Operator level 2 | Configuration limits and read only report | Bounds are clamped by configuration validation. |
| `/shopadmin on`, `/shopadmin off` | Admin mode toggle | Operator level 2 | The issuing player session | Toggle is repeatable and does not mutate market value. |
| `/marketadmin`, `/madmin` | `status`, `audit`, `recovery` | Root operator level 2 and market permission service | Read only runtime state | Diagnostic leaves never mutate state. |
| `/marketadmin control` | Lifecycle transition verbs and bounded reason | Operator level 3 and escrow admin permission | Module lifecycle state | Transition is validated, journaled and audited. Repeated transition returns current state. |
| `/marketadmin maintenance` | `status`, `verify`, `resume confirm reason` | Operator level 3 and escrow admin permission | Maintenance state only after verified repair | Resume requires explicit confirmation, current revision, journal sequence, fingerprint, conservation and durable audit. Failure leaves maintenance active. |
| `/marketadmin adminshop` | `validate`, `quarantine_missing confirm reason` | Operator level 3 and shop admin permission | Catalog validation or missing item quarantine | Validation is read only. Quarantine preserves complete recovery copies, atomically removes only missing item entries and rolls back if reload fails. |
| `/marketadmin inspect`, `/marketadmin sweep` | Transaction identifier or sweep request | Operator level 3 and escrow admin permission | Read only inspection or bounded cleanup | Inspection is non mutating. Sweep is journaled and repeat safe. |
| `/marketadmin auction cancel` | Auction listing identifier and confirmation path | Operator level 4 and auction admin permission | One auction custody record | Cancellation is validated, request identified and claims any failed delivery durably. |
| `/marketadmin bazaar product` | Product state operation and identifier | Operator level 3 and bazaar admin permission | One Bazaar product | Product changes validate module state and are replay safe. |
| `/franchise` | `create`, `invite`, `accept`, `decline`, `kick`, `promote`, `manage`, `leave`, `disband` | Player source, ownership and franchise role checks | The requesting player's franchise and named members | Membership mutations validate current revision and ownership. |
| `/link` | Player shop storage link flow | Player source, block ownership and distance checks | One owned shop block | Link and unlink operations use position and owner binding. |
| `/shopdesc` | Root description view | Player source | Read only shop description | No mutation. |

Malformed input, non player sources where a player is required, missing permissions, stale revisions, disabled modules, unavailable escrow, wrong ownership and repeated confirmations terminate before a value mutation. Console and command block support is limited to leaves whose source helpers explicitly permit non player sources.
