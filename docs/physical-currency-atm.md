# Physical currency and ATM

FutureShops keeps the account balance separate from the physical item used as cash. The active
physical provider is configured in `futureshops-common.toml` with `currency.provider`.

## Opening the ATM

- Run `/atm`.
- Run `/withdraw` without an amount.
- Open Marketplace Profile and click **ATM**.

Enter an amount and click **Auto**, use the 25% / 50% / Max shortcuts, or adjust individual bill
counts. Hold Shift while clicking a denomination stepper to move by 10. The server validates the
submitted denomination list, current balance, live provider configuration, arithmetic, and
inventory space before minting anything.

The ATM advertises up to 32 denominations from a custom provider (largest first), which keeps the
network request bounded while still allowing far more bill types than a practical currency needs.

The existing `/withdraw <amount> [yes|no]` command uses the same withdrawal engine as the ATM.

## Depositing physical currency

Select **Deposit**, choose Inventory, Main hand, or Off hand, and enter an exact amount. Leave the
amount blank to deposit all eligible currency from the selected source. The server binds the
request UUID to the provider signature, source, amount, player, and deterministic escrow
transaction before inventory mutation. A replay can inspect the original request but cannot
create a second credit.

Protected FutureShops bills retain exact mint evidence through intent, inventory mutation,
settlement, and cleanup. Foreign provider items use the same durable request and inventory
reconciliation contract, but FutureShops cannot prove how the foreign mod created those items.

## Deposit recovery

Protocol 55 retains the server authoritative ATM recovery summary introduced by protocol 52
whenever the player has active deposit evidence. It contains the original request UUID,
deterministic transaction UUID, amount, and recovery state. Reopening the ATM or reconnecting
adopts this identity before another deposit can be enabled.

- **Check recovery** sends only the original request and transaction UUID. It cannot submit an
  amount or currency source and cannot consume a second deposit.
- Deposit and Withdrawal tabs remain usable while recovery is retryable.
- **Collect cash** remains available for committed physical cash claims while deposit recovery is
  retryable.
- The deposit panel wraps the recovery state and complete transaction UUID. **Copy** places the
  full handle on the clipboard.
- `COMPLETED` refreshes the wallet and claims. `REFUNDED` reports the exact value that remained
  in the original physical currency inventory. `MANUAL_REVIEW` stops client retries and requires
  operator inspection.

Operators can run `/marketadmin inspect <transactionId>` with the copied handle. The command is
read only and reports the request, transaction state, participants, currency provider, durable
evidence phase, amount, claims, retry schedule, last error, and safe next action. Do not delete
player data, escrow data, journals, checkpoints, or claims to clear a recovery banner.

## Built-in protected money

`currency.provider = "futureshops"` mints `futureshops:money`. Every minted stack receives:

- a unique mint ID;
- denomination and authorized-count metadata;
- a server checksum;
- a persistent spent-mint ledger entry.

ATM withdrawals and command withdrawals both use this protected mint path.

## Foreign currency warning

Any provider other than `futureshops` is intentionally unprotected. FutureShops creates the
configured source-mod item as a plain `ItemStack` so it continues to stack with that mod's loot and
recipes. It does not attach a FutureShops checksum or mint ID and does not add it to the spent-mint
ledger.

This means FutureShops cannot detect copied currency or prevent exploits in the source mod's loot,
crafting, storage, networking, or item implementation. The generated TOML, server startup log, and
ATM screen all display this warning.

## Custom provider example

```toml
[currency]
provider = "custom"
items = ["examplemod:large_bill=10000", "examplemod:bill=100", "examplemod:coin=25"]
accept_only_items = ["examplemod:bill_block=900"]
```

Values use economy minor units: with two decimal places, `100` is `1.00` and `25` is `0.25`.
`items` are both withdrawable and depositable. `accept_only_items` are depositable but never
dispensed by the ATM. Review every source-mod conversion recipe before assigning values; otherwise
a crafting recipe can create an exchange-rate money printer.
