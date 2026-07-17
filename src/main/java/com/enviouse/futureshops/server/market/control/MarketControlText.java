package com.enviouse.futureshops.server.market.control;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

final class MarketControlText {
    private MarketControlText() {
    }

    static String require(String value, String name, int maximumBytes) {
        String normalized = Objects.requireNonNull(value, name).strip();
        int size;
        try {
            size = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(java.nio.CharBuffer.wrap(normalized))
                    .remaining();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException(
                    name + " is not valid Unicode text", exception);
        }
        if (size == 0 || size > maximumBytes) {
            throw new IllegalArgumentException(
                    name + " size is invalid");
        }
        for (int index = 0; index < normalized.length(); index++) {
            if (Character.isISOControl(normalized.charAt(index))) {
                throw new IllegalArgumentException(
                        name + " contains a control character");
            }
        }
        return normalized;
    }

    static String requireFingerprint(String value, String name) {
        String fingerprint = require(value, name, 64);
        if (fingerprint.length() != 64) {
            throw new IllegalArgumentException(
                    name + " length is invalid");
        }
        for (int index = 0; index < fingerprint.length(); index++) {
            char character = fingerprint.charAt(index);
            if (!((character >= '0' && character <= '9')
                    || (character >= 'a' && character <= 'f'))) {
                throw new IllegalArgumentException(
                        name + " is not canonical hexadecimal text");
            }
        }
        return fingerprint;
    }
}
