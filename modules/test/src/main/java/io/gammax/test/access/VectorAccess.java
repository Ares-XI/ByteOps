package io.gammax.test.access;

public interface VectorAccess {
    boolean isZeroS();
    String getStats();
    int getOperationCount();
    String getLastOperation();
    void setOperationCount(int count);
    void setLastOperation(String operation);
}