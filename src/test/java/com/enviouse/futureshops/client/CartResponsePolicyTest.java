package com.enviouse.futureshops.client;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CartResponsePolicyTest {
    private static final long NOW = 1_000L;
    private static final long TIMEOUT = 15_000L;

    @Test
    void matchingSuccessfulTerminalResponseClearsExactSnapshot() {
        CartResponsePolicy policy = new CartResponsePolicy();
        UUID requestId = UUID.randomUUID();
        List<CartResponsePolicy.Line> lines = List.of(
                new CartResponsePolicy.Line(0, "diamond", 7),
                new CartResponsePolicy.Line(0, "emerald", 3));

        assertEquals(CartResponsePolicy.BeginDecision.STARTED,
                policy.begin(requestId, lines, NOW, TIMEOUT));
        CartResponsePolicy.ResponseResult result =
                policy.onResponse(requestId, 0, true, true, NOW + 1L);

        assertEquals(CartResponsePolicy.ResponseDecision.CHECKOUT_SUCCEEDED, result.decision());
        assertEquals(lines, result.linesToClear());
        assertTrue(result.matched());
        assertTrue(result.checkoutComplete());
        assertTrue(result.checkoutSuccessful());
        assertFalse(policy.isPending());
    }

    @Test
    void staleResponseDoesNotClearOrFinishPendingCheckout() {
        CartResponsePolicy policy = new CartResponsePolicy();
        UUID current = UUID.randomUUID();
        policy.begin(current,
                List.of(new CartResponsePolicy.Line(0, "diamond", 4)), NOW, TIMEOUT);

        CartResponsePolicy.ResponseResult result =
                policy.onResponse(UUID.randomUUID(), 0, true, true, NOW + 1L);

        assertEquals(CartResponsePolicy.ResponseDecision.STALE, result.decision());
        assertTrue(result.linesToClear().isEmpty());
        assertFalse(result.matched());
        assertTrue(policy.isPending());
    }

    @Test
    void retryableFailureRetainsLineAndAllowsNewCheckoutAfterTerminalResponse() {
        CartResponsePolicy policy = new CartResponsePolicy();
        UUID requestId = UUID.randomUUID();
        policy.begin(requestId,
                List.of(new CartResponsePolicy.Line(0, "diamond", 4)), NOW, TIMEOUT);

        CartResponsePolicy.ResponseResult result =
                policy.onResponse(requestId, 0, false, true, NOW + 1L);

        assertEquals(CartResponsePolicy.ResponseDecision.CHECKOUT_FINISHED_WITH_FAILURES,
                result.decision());
        assertTrue(result.linesToClear().isEmpty());
        assertTrue(result.checkoutComplete());
        assertFalse(result.checkoutSuccessful());
        assertFalse(policy.isPending());
    }

    @Test
    void serverErrorRetainsLine() {
        CartResponsePolicy policy = new CartResponsePolicy();
        UUID requestId = UUID.randomUUID();
        policy.begin(requestId,
                List.of(new CartResponsePolicy.Line(9, "shop.4", 2)), NOW, TIMEOUT);

        CartResponsePolicy.ResponseResult result =
                policy.onResponse(requestId, 9, false, true, NOW + 1L);

        assertTrue(result.linesToClear().isEmpty());
        assertEquals(CartResponsePolicy.ResponseDecision.CHECKOUT_FINISHED_WITH_FAILURES,
                result.decision());
    }

    @Test
    void nonterminalSuccessDoesNotClearLine() {
        CartResponsePolicy policy = new CartResponsePolicy();
        UUID requestId = UUID.randomUUID();
        policy.begin(requestId,
                List.of(new CartResponsePolicy.Line(0, "diamond", 4)), NOW, TIMEOUT);

        CartResponsePolicy.ResponseResult result =
                policy.onResponse(requestId, 0, true, false, NOW + 1L);

        assertEquals(CartResponsePolicy.ResponseDecision.NON_TERMINAL, result.decision());
        assertTrue(result.linesToClear().isEmpty());
        assertTrue(policy.isPending());
    }

    @Test
    void timeoutRetainsUnacknowledgedLinesAndReleasesPendingGate() {
        CartResponsePolicy policy = new CartResponsePolicy();
        UUID requestId = UUID.randomUUID();
        policy.begin(requestId,
                List.of(new CartResponsePolicy.Line(0, "diamond", 4)), NOW, TIMEOUT);

        assertEquals(CartResponsePolicy.TimeoutDecision.WAITING,
                policy.expire(NOW + TIMEOUT - 1L));
        assertTrue(policy.isPending());
        assertEquals(CartResponsePolicy.TimeoutDecision.TIMED_OUT,
                policy.expire(NOW + TIMEOUT));
        assertFalse(policy.isPending());
        assertTrue(policy.hasTrackedRequest());
    }

    @Test
    void delayedMatchingResponseStillCompletesTimedOutRequest() {
        CartResponsePolicy policy = new CartResponsePolicy();
        UUID requestId = UUID.randomUUID();
        policy.begin(requestId,
                List.of(new CartResponsePolicy.Line(0, "diamond", 4)), NOW, TIMEOUT);

        CartResponsePolicy.ResponseResult result =
                policy.onResponse(requestId, 0, true, true, NOW + TIMEOUT);

        assertEquals(CartResponsePolicy.ResponseDecision.CHECKOUT_SUCCEEDED, result.decision());
        assertEquals(List.of(new CartResponsePolicy.Line(0, "diamond", 4)),
                result.linesToClear());
        assertFalse(policy.isPending());
        assertFalse(policy.hasTrackedRequest());
    }

    @Test
    void retryReusesTrackedRequestAndRejectsReplacementRequest() {
        CartResponsePolicy policy = new CartResponsePolicy();
        UUID original = UUID.randomUUID();
        List<CartResponsePolicy.Line> lines =
                List.of(new CartResponsePolicy.Line(0, "diamond", 4));
        policy.begin(original, lines, NOW, TIMEOUT);
        policy.expire(NOW + TIMEOUT);

        assertEquals(CartResponsePolicy.BeginDecision.ALREADY_PENDING,
                policy.begin(UUID.randomUUID(), lines, NOW + TIMEOUT + 1L, TIMEOUT));
        assertEquals(original, policy.pendingRequestId().orElseThrow());
        assertEquals(CartResponsePolicy.RetryDecision.RETRIED,
                policy.retry(NOW + TIMEOUT + 2L, TIMEOUT));
        assertTrue(policy.isPending());
        assertEquals(CartResponsePolicy.RetryDecision.ALREADY_PENDING,
                policy.retry(NOW + TIMEOUT + 3L, TIMEOUT));
    }

    @Test
    void staleResponseAfterTimeoutDoesNotAbandonTrackedRequest() {
        CartResponsePolicy policy = new CartResponsePolicy();
        UUID original = UUID.randomUUID();
        policy.begin(original,
                List.of(new CartResponsePolicy.Line(0, "diamond", 4)), NOW, TIMEOUT);
        policy.expire(NOW + TIMEOUT);

        CartResponsePolicy.ResponseResult stale = policy.onResponse(
                UUID.randomUUID(), 0, true, true, NOW + TIMEOUT + 1L);

        assertEquals(CartResponsePolicy.ResponseDecision.STALE, stale.decision());
        assertTrue(stale.linesToClear().isEmpty());
        assertTrue(policy.hasTrackedRequest());
        assertEquals(original, policy.pendingRequestId().orElseThrow());
    }

    @Test
    void retryKeepsResolvedTokensAndCannotClearSuccessfulLineTwice() {
        CartResponsePolicy policy = new CartResponsePolicy();
        UUID requestId = UUID.randomUUID();
        CartResponsePolicy.Line first = new CartResponsePolicy.Line(0, "shop.0", 2);
        CartResponsePolicy.Line second = new CartResponsePolicy.Line(1, "shop.1", 6);
        policy.begin(requestId, List.of(first, second), NOW, TIMEOUT);
        assertEquals(List.of(first), policy.onResponse(
                requestId, 0, true, true, NOW + 1L).linesToClear());
        policy.expire(NOW + TIMEOUT);
        policy.retry(NOW + TIMEOUT + 1L, TIMEOUT);

        assertEquals(CartResponsePolicy.ResponseDecision.STALE,
                policy.onResponse(
                        requestId, 0, true, true, NOW + TIMEOUT + 2L).decision());
        CartResponsePolicy.ResponseResult completion = policy.onResponse(
                requestId, 1, true, true, NOW + TIMEOUT + 3L);
        assertEquals(List.of(second), completion.linesToClear());
        assertTrue(completion.checkoutSuccessful());
    }

    @Test
    void secondCheckoutIsRejectedWhileFirstIsPending() {
        CartResponsePolicy policy = new CartResponsePolicy();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        List<CartResponsePolicy.Line> lines =
                List.of(new CartResponsePolicy.Line(0, "diamond", 4));

        assertEquals(CartResponsePolicy.BeginDecision.STARTED,
                policy.begin(first, lines, NOW, TIMEOUT));
        assertEquals(CartResponsePolicy.BeginDecision.ALREADY_PENDING,
                policy.begin(second, lines, NOW + 1L, TIMEOUT));
        assertEquals(first, policy.pendingRequestId().orElseThrow());
    }

    @Test
    void multiLineCheckoutClearsOnlySuccessfulMatchingLines() {
        CartResponsePolicy policy = new CartResponsePolicy();
        UUID requestId = UUID.randomUUID();
        CartResponsePolicy.Line first = new CartResponsePolicy.Line(0, "shop.0", 2);
        CartResponsePolicy.Line second = new CartResponsePolicy.Line(1, "shop.1", 6);
        policy.begin(requestId, List.of(first, second), NOW, TIMEOUT);

        CartResponsePolicy.ResponseResult firstResult =
                policy.onResponse(requestId, 0, true, true, NOW + 1L);
        assertEquals(List.of(first), firstResult.linesToClear());
        assertEquals(CartResponsePolicy.ResponseDecision.LINE_SUCCEEDED, firstResult.decision());
        assertTrue(policy.isPending());

        CartResponsePolicy.ResponseResult secondResult =
                policy.onResponse(requestId, 1, false, true, NOW + 2L);
        assertTrue(secondResult.linesToClear().isEmpty());
        assertEquals(CartResponsePolicy.ResponseDecision.CHECKOUT_FINISHED_WITH_FAILURES,
                secondResult.decision());
        assertFalse(policy.isPending());
    }

    @Test
    void duplicateLineResponseIsStaleAndCannotClearTwice() {
        CartResponsePolicy policy = new CartResponsePolicy();
        UUID requestId = UUID.randomUUID();
        policy.begin(requestId, List.of(
                new CartResponsePolicy.Line(0, "shop.0", 2),
                new CartResponsePolicy.Line(1, "shop.1", 6)), NOW, TIMEOUT);

        assertEquals(CartResponsePolicy.ResponseDecision.LINE_SUCCEEDED,
                policy.onResponse(requestId, 0, true, true, NOW + 1L).decision());
        CartResponsePolicy.ResponseResult duplicate =
                policy.onResponse(requestId, 0, true, true, NOW + 2L);

        assertEquals(CartResponsePolicy.ResponseDecision.STALE, duplicate.decision());
        assertTrue(duplicate.linesToClear().isEmpty());
        assertTrue(policy.isPending());
    }
}
