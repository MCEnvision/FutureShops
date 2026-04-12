package com.enviouse.futureshops.catalog;

/**
 * Server-side record representing a shop category loaded from the catalog config.
 * Converted to {@link com.enviouse.futureshops.data.CatalogCategory} before sending over the network.
 */
public record CategoryDef(String id, String displayName, int sortOrder) {}

