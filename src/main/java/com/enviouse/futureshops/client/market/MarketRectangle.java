package com.enviouse.futureshops.client.market;

public record MarketRectangle(int x, int y, int width, int height) {
    public MarketRectangle {
        if (x < 0 || y < 0 || width < 0 || height < 0) {
            throw new IllegalArgumentException("Market rectangle values must not be negative.");
        }
    }

    public int right() {
        return Math.addExact(x, width);
    }

    public int bottom() {
        return Math.addExact(y, height);
    }

    public boolean contains(int pointX, int pointY) {
        return pointX >= x && pointX < right() && pointY >= y && pointY < bottom();
    }

    public boolean overlaps(MarketRectangle other) {
        return x < other.right() && right() > other.x
            && y < other.bottom() && bottom() > other.y;
    }
}
