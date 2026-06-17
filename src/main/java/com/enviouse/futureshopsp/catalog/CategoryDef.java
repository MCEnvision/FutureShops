package com.enviouse.futureshopsp.catalog;

/**
 * Server-side record representing a shop category loaded from the catalog config.
 * Converted to {@link com.enviouse.futureshopsp.data.CatalogCategory} before sending over the network.
 */
public record CategoryDef(String id, String displayName, int sortOrder) {}

