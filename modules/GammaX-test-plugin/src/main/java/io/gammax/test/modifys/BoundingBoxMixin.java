package io.gammax.test.modifys;

import io.byteops.modify.Arg;
import io.byteops.modify.Extend;
import io.byteops.modify.Modify;
import io.byteops.modify.Provide;
import io.byteops.modify.Inject;
import io.byteops.modify.util.InjectResult;
import io.byteops.modify.util.MethodReference;
import io.gammax.test.access.BoundingBoxAccess;
import org.bukkit.util.BoundingBox;
import io.byteops.modify.util.At;

@Modify(BoundingBox.class)
public abstract class BoundingBoxMixin implements BoundingBoxAccess {
    @Provide
    private double minX;

    @Provide
    private double minY;

    @Provide
    private double minZ;

    @Provide
    private double maxX;

    @Provide
    private double maxY;

    @Provide
    private double maxZ;

    @Extend
    private String data;

    @Extend
    public static final double EXPAND_FACTOR = 1.1;

    @Extend
    @Override
    public void expandSymmetrical(double amount) {
        minX -= amount;
        minY -= amount;
        minZ -= amount;
        maxX += amount;
        maxY += amount;
        maxZ += amount;
        System.out.println("[BoundingBox] Expanded by " + amount);
        System.out.println("Expand factor: " + EXPAND_FACTOR);
        data = "hello";
        System.out.println("data set to \"hello\"");
    }

    @Extend
    @Override
    public String getDimensions() {
        return String.format("BoundingBox{min=(%.2f,%.2f,%.2f), max=(%.2f,%.2f,%.2f)}", minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Extend
    @Override
    public String getData() {
        return data;
    }

    @Inject(at = At.HEAD, method = @MethodReference(method = "<init>", parameters = {
            double.class,
            double.class,
            double.class,
            double.class,
            double.class,
            double.class,
    }))
    private InjectResult<Void> onTest(@Arg(0) double minX) {
        System.out.println("init: " + minX);
        return InjectResult.pass();
    }

    @Inject(at = At.HEAD, method = @MethodReference(method = "getMinX", result = double.class))
    private InjectResult<Double> onGetMinX() {
        System.out.println("changing result value to: 67.0");
        return InjectResult.stop(67.0);
    }
}