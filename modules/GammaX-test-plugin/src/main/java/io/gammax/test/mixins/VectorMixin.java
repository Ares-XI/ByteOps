package io.gammax.test.mixins;

import io.byteops.modify.*;
import io.byteops.modify.util.At;
import io.byteops.modify.util.InjectResult;
import io.byteops.modify.util.LocalData;
import io.byteops.modify.util.MethodReference;
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

    @Extend
    @Override
    public boolean isZeroS() {
        return Math.abs(x) < EPSILON && Math.abs(y) < EPSILON && Math.abs(z) < EPSILON;
    }

    @Extend
    @Override
    public String getStats() {
        return String.format("Vector{ops=%d, last='%s', pos=(%.2f,%.2f,%.2f)}", operationCount, lastOperation, x, y, z);
    }

    @Extend
    @Override
    public int getOperationCount() {
        return operationCount;
    }

    @Extend
    @Override
    public void setOperationCount(int count) {
        operationCount = count;
    }

    @Extend
    @Override
    public String getLastOperation() {
        return lastOperation;
    }

    @Extend
    @Override
    public void setLastOperation(String operation) {
        lastOperation = operation;
    }

    @Extend
    @Override
    public Test getTest() {
        return test;
    }

    @Extend
    @Override
    public void setTest(Test test) {
        this.test = test;
    }

    @Inject(at = At.GET, method = @MethodReference(method = "rotateAroundY", result = Vector.class, parameters = {double.class}), index = 11)
    private InjectResult<Void> onXLoad(@Local(2) double x) {
        System.out.println("x before setX: " + x + " → setting to 999.0");
        return InjectResult.pass().setLocals(new LocalData(2, 999.0));
    }

    @Inject(at = At.RETURN, method = @MethodReference(method = "angle", result = float.class, parameters = {Vector.class}))
    private InjectResult<Void> onAngle(@Local(0) double dot) {
        System.out.println("called inject to Return point, dot: " + dot);
        return InjectResult.pass();
    }

    @Inject(at = At.GET, method = @MethodReference(method = "angle", result = float.class, parameters = {Vector.class}), index = 4)
    private InjectResult<Void> onAcos(@Local(0) double dot) {
        System.out.println("Before acos, dot=" + dot + " → setting to 0.5");
        return InjectResult.pass().setLocals(new LocalData(0, 0.5));
    }

    @Inject(at = At.RETURN, method = @MethodReference(method = "hashCode", result = int.class))
    private InjectResult<Integer> onHashCode() {
        System.out.println("called inject to return 67");
        return InjectResult.stop(67);
    }

    @Inject(at = At.INVOKE, method = @MethodReference(method = "isNormalized", result = boolean.class), index = 1)
    private InjectResult<Boolean> onIsNormalized() {
        System.out.println("called in invoke 2-nd, with result: true");
        return InjectResult.stop(true);
    }
}