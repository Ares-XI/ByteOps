package io.gammax.test.access;

public interface BoundingBoxAccess {
    void expandSymmetrical(double amount);
    String getDimensions();
    String getData();
}