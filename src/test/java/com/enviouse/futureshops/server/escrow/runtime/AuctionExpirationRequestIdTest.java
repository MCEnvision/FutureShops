package com.enviouse.futureshops.server.escrow.runtime;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The idempotency contract behind {@link AuctionExpirationScheduler}: an expiration request
 * id is derived deterministically from {@code "auction.expire." + revision + "."} over the
 * listing id, so a retried sweep replays the stored commit instead of producing a second
 * economic result, while a revision change (e.g. a late bid) yields a fresh request id.
 * The sweep loop itself needs a live escrow runtime and is covered at integration level.
 */
class AuctionExpirationRequestIdTest {

    private static final UUID LISTING_ID =
            UUID.fromString("7d444840-9dc0-11d1-b245-5ffdce74fad2");
    private static final UUID OTHER_LISTING_ID =
            UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");

    @Test
    void expireRequestIdMatchesDocumentedDerivationContract() {
        long revision = 4L;
        UUID requestId = AuctionActionService.derived(
                "auction.expire." + revision + ".", LISTING_ID);

        assertEquals(UUID.nameUUIDFromBytes(
                        ("auction.expire." + revision + "." + LISTING_ID)
                                .getBytes(StandardCharsets.UTF_8)),
                requestId);
    }

    @Test
    void expireRequestIdIsStableAcrossRetriesOfTheSameRevision() {
        assertEquals(
                AuctionActionService.derived("auction.expire." + 4L + ".", LISTING_ID),
                AuctionActionService.derived("auction.expire." + 4L + ".", LISTING_ID));
    }

    @Test
    void expireRequestIdChangesWhenTheListingRevisionAdvances() {
        assertNotEquals(
                AuctionActionService.derived("auction.expire." + 4L + ".", LISTING_ID),
                AuctionActionService.derived("auction.expire." + 5L + ".", LISTING_ID));
    }

    @Test
    void expireRequestIdIsDistinctAcrossListingsAndFromItsTransactionId() {
        UUID requestId = AuctionActionService.derived(
                "auction.expire." + 4L + ".", LISTING_ID);

        assertNotEquals(requestId, AuctionActionService.derived(
                "auction.expire." + 4L + ".", OTHER_LISTING_ID));
        // The scheduler derives the expire transaction id from the request id with a
        // different prefix; the two identifiers must never collide.
        assertNotEquals(requestId,
                AuctionActionService.derived("auction.expiretxn.", requestId));
    }
}
