package io.gammax.test.access;

import io.gammax.test.some.Test;
import org.bukkit.util.Vector;

public interface VectorAccess {
    boolean isZeroS();
    String getStats();
    int getOperationCount();
    String getLastOperation();
    Test getTest();
    void setTest(Test test);
    void setOperationCount(int count);
    void setLastOperation(String operation);
    Vector plusThis();
}