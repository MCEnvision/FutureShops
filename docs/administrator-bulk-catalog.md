# Administrator bulk catalog editing

The admin shop edit screen uses one bounded searchable item grid. Select one or more registered
items, enter one shared base price and stock value, then choose Preview. The server resolves the
registry IDs, parses the price using the configured currency precision, validates stock, and returns
every row before any file changes.

Existing item identities are shown as Skip by default. A row is Replace eligible only when one
existing listing has the same item and exact NBT identity. Select Replace for that row to update its
item identity, exact NBT, shared buy price, and shared stock. Descriptions, categories, permissions,
active state, schedules, limits, bundle fields, promotions, and unknown JSON members remain
unchanged. Multiple matches, malformed existing identity data, missing registry entries, invalid
price or stock, stale catalog state, and permission loss block the complete batch.

For one selected item, the Exact NBT field accepts one complete SNBT compound. Blank means registry
identity only. NBT member ordering is canonicalized on the server, so equivalent compounds produce
one identity. The field is bounded by the server NBT limit and invalid text stays in the picker for
correction.

Apply Changes sends the preview fingerprint, expected catalog fingerprint, expected registry
fingerprint, and explicit replacements. The server replans against current state, validates the
complete candidate, writes the catalog atomically, reloads the runtime once, and refreshes active
shop sessions. A reload failure restores the prior complete catalog and runtime snapshot. Request
outcomes are stored in world SavedData, so retrying the same request after reconnect or restart
returns the original result without writing twice.

The workflow is limited to 256 selected identities per request and respects
`admin_shop.maximum_listings`. The existing simple and advanced editors remain the path for barter
ingredients, bundles, schedules, limits, descriptions, and other fields outside this bounded batch.
