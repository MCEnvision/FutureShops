package com.enviouse.futureshops.server.security;

import java.util.LinkedHashMap;
import java.util.Map;

final class ServerRequestSecurityPolicy {
    private ServerRequestSecurityPolicy() {
    }

    static ServerRequestRateLimiter createLimiter(
            ServerRequestRateLimiter.NanoClock clock,
            ServerRequestSecuritySettings settings
    ) {
        LinkedHashMap<String, ServerRequestRateLimiter.BucketPolicy> policies =
                new LinkedHashMap<>();
        Map<ServerRequestAction, ServerRequestSecuritySettings.ActionLimit>
                limits = settings.actionLimits();
        for (ServerRequestAction action : ServerRequestAction.values()) {
            ServerRequestSecuritySettings.ActionLimit limit =
                    limits.get(action);
            policies.put(action.code(), new ServerRequestRateLimiter.BucketPolicy(
                    limit.capacity(), limit.refillTokens(),
                    limit.refillPeriod()));
        }
        return new ServerRequestRateLimiter(
                policies, settings.trackedKeyCap(),
                settings.idleRetention(), clock);
    }
}
