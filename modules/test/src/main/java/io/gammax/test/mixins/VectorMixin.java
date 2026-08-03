package io.gammax.test.mixins;

import io.gammax.api.Extend;
import io.gammax.api.Modify;
import io.gammax.api.Provide;
import io.gammax.test.access.VectorAccess;
import io.gammax.test.some.Test;
import org.bukkit.util.Vector;

@Modify(Vector.class)
public abstract class VectorMixin implements VectorAccess {
    @Provide
    protected double x;

    @Provide
    protected double y;

    @Provide
    protected double z;

    @Extend
    private int operationCount;

    @Extend
    public static final double EPSILON = 0.0001;

    @Extend
    private String lastOperation;

    @Extend
    public Test test;

    @Override
    @Extend
    public boolean isZeroS() {
        return Math.abs(x) < EPSILON && Math.abs(y) < EPSILON && Math.abs(z) < EPSILON;
    }

    @Override
    @Extend
    public String getStats() {
        return String.format("Vector{ops=%d, last='%s', pos=(%.2f,%.2f,%.2f)}", operationCount, lastOperation, x, y, z);
    }

    @Override
    @Extend
    public int getOperationCount() {
        return operationCount;
    }

    @Override
    @Extend
    public void setOperationCount(int count) {
        operationCount = count;
    }

    @Override
    @Extend
    public String getLastOperation() {
        return lastOperation;
    }

    @Override
    @Extend
    public void setLastOperation(String operation) {
        lastOperation = operation;
    }

    @Override
    @Extend
    public Test getTest() {
        return test;
    }

    @Override
    @Extend
    public void setTest(Test test) {
        this.test = test;
    }
}