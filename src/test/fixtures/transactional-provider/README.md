# transactional provider fixture

This fixture is compiled as a separate project against the FutureShops public economy API. It is not included in the production jar. The provider stores the balance effect and its immutable request receipt in one forced, atomically replaced file image, then resolves the same receipt from a fresh provider instance.
