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
