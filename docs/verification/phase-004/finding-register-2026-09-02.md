# Phase 004 finding register

| Finding | Support line | Evidence | State |
| --- | --- | --- | --- |
| Issue [#40](https://github.com/MCEnvision/FutureShops/issues/40), catalog roots and files followed symbolic links | Forge and independently reviewed NeoForge | Symlinked `futureshops` or `shops` roots and catalog entries bypassed the intended storage boundary | Forge and NeoForge repairs implemented and tested. Exact merged verification remains before closure. |
| Issue [#41](https://github.com/MCEnvision/FutureShops/issues/41), incoming client text used unbounded decoding | Forge and independently reviewed NeoForge | C2S source scan found literal unbounded `readUtf()` calls in the Forge and NeoForge packet sets | Forge and NeoForge repairs implemented and tested. Exact merged verification remains before closure. |
| Issue [#42](https://github.com/MCEnvision/FutureShops/issues/42), leaderboard page caused avoidable work | Forge and independently reviewed NeoForge | `/baltop` and its UI packet accepted pages outside a bounded query range before taking a balance snapshot | Forge and NeoForge repairs implemented and tested. Exact merged verification remains before closure. |
| Issue [#43](https://github.com/MCEnvision/FutureShops/issues/43), history pagination arithmetic overflow | Forge and independently reviewed NeoForge | History and settlement pages multiplied attacker supplied values as an int before slicing | Forge and NeoForge repairs implemented and tested. Exact merged verification remains before closure. |
| Issue [#44](https://github.com/MCEnvision/FutureShops/issues/44), claim collection failure was swallowed | Forge 1.20.1 only | The Forge claim packet handler discarded runtime failures without an audit log or fallback response. NeoForge has no equivalent market claim collection packet. | Forge repair implemented and tested. |
| Issue [#45](https://github.com/MCEnvision/FutureShops/issues/45), player shop promotion accepted nonfinite values | Forge and independently reviewed NeoForge | Promotion requests accepted nonfinite or out of domain values before persistence. | Forge and NeoForge repairs implemented and tested. |

No other candidate is left unclassified in the Forge freeze. Existing platform dependency alerts, persistence work and integration work are recorded as accepted handoffs to their owning phase or repository security process. No private advisory was required because the verified findings are safe to describe without payload disclosure.

The register must be updated after each support line merge and must not mark a cross line issue closed until both line specific repairs and exact merged verification pass. Forge only findings close after the Forge merge and verification pass.
