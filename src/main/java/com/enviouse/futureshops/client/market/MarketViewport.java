package com.enviouse.futureshops.client.market;

public record MarketViewport(int pixelWidth, int pixelHeight, int guiScale, int guiWidth, int guiHeight) {
    public MarketViewport {
        if (pixelWidth <= 0 || pixelHeight <= 0 || guiScale <= 0 || guiWidth <= 0 || guiHeight <= 0) {
            throw new IllegalArgumentException("Market viewport dimensions must be positive.");
        }
        if (guiWidth != divideRoundUp(pixelWidth, guiScale)
            || guiHeight != divideRoundUp(pixelHeight, guiScale)) {
            throw new IllegalArgumentException("Market viewport GUI dimensions do not match the pixel scale.");
        }
    }

    public static MarketViewport scaled(int pixelWidth, int pixelHeight, int guiScale) {
        if (pixelWidth <= 0 || pixelHeight <= 0 || guiScale <= 0) {
            throw new IllegalArgumentException("Market viewport dimensions must be positive.");
        }
        return new MarketViewport(
            pixelWidth,
            pixelHeight,
            guiScale,
            divideRoundUp(pixelWidth, guiScale),
            divideRoundUp(pixelHeight, guiScale)
        );
    }

    private static int divideRoundUp(int value, int divisor) {
        return Math.toIntExact(((long) value + divisor - 1L) / divisor);
    }
}
