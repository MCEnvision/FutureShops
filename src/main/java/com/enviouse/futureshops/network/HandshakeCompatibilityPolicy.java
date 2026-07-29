package com.enviouse.futureshops.network;

public final class HandshakeCompatibilityPolicy {
    public enum PeerSide {
        CLIENT,
        SERVER
    }

    public enum Result {
        MATCH(""),
        MISSING_ON_CLIENT(
                "futureshops is required by this server but is missing from your client.\n\n"
                        + "install the same futureshops version on your client."),
        MISSING_ON_SERVER(
                "futureshops is installed on your client but is missing from the server.\n\n"
                        + "install the same futureshops version on the server."),
        CLIENT_OUTDATED(
                "your futureshops version is older than the server version.\n\n"
                        + "update futureshops on your client."),
        SERVER_OUTDATED(
                "the server is using an older futureshops version than your client.\n\n"
                        + "ask the server owner to update futureshops."),
        INCOMPATIBLE(
                "the client and server use incompatible futureshops versions.\n\n"
                        + "install matching futureshops versions on both sides.");

        private final String message;

        Result(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }

        public boolean compatible() {
            return this == MATCH;
        }
    }

    private HandshakeCompatibilityPolicy() {
    }

    public static Result evaluate(String localProtocol, String peerProtocol,
                                  PeerSide peerSide) {
        if (peerProtocol == null || peerProtocol.isBlank()) {
            return peerSide == PeerSide.CLIENT
                    ? Result.MISSING_ON_CLIENT
                    : Result.MISSING_ON_SERVER;
        }
        if (localProtocol.equals(peerProtocol)) {
            return Result.MATCH;
        }

        try {
            int local = Integer.parseInt(localProtocol);
            int peer = Integer.parseInt(peerProtocol);
            if (local == peer) {
                return Result.MATCH;
            }
            boolean peerOutdated = peer < local;
            boolean clientOutdated = peerSide == PeerSide.CLIENT
                    ? peerOutdated
                    : !peerOutdated;
            return clientOutdated
                    ? Result.CLIENT_OUTDATED
                    : Result.SERVER_OUTDATED;
        } catch (NumberFormatException exception) {
            return Result.INCOMPATIBLE;
        }
    }
}
