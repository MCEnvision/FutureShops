package com.enviouse.futureshops.server.escrow.playershop;

public final class PlayerShopEscrowConstants {
    public static final int RESERVED_JOURNAL_EVENT_ID = 35;
    public static final int RESERVED_CHECKPOINT_STORE_ID = 13;
    public static final int MAX_IDENTIFIER_LENGTH = 160;
    public static final int MAX_TEXT_LENGTH = 256;
    public static final int MAX_LISTING_OUTPUTS = 36;
    public static final int MAX_ITEM_PORTIONS = 4096;
    public static final int MAX_TRANSFERS = 8192;
    public static final int MAX_CLAIMS = 8192;
    public static final int MAX_STORAGE_MUTATIONS = 8192;
    public static final int MAX_COMPONENT_BYTES = 1_048_576;
    public static final int MAX_ENCODED_BYTES = 32 * 1_048_576;

    private PlayerShopEscrowConstants() {
    }
}
