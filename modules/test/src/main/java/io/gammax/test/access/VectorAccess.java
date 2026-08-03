package io.gammax.test.access;

import io.gammax.test.some.Test;

public interface VectorAccess {
    boolean isZeroS();
    String getStats();
    int getOperationCount();
    String getLastOperation();
    Test getTest();
    void setTest(Test test);
    void setOperationCount(int count);
    void setLastOperation(String operation);
}