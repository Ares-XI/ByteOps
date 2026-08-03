package io.gammax.test.mixins;

import io.gammax.api.Extend;
import io.gammax.api.Modify;
import io.gammax.api.Provide;
import io.gammax.test.access.BoundingBoxAccess;
import org.bukkit.util.BoundingBox;

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

    @Override
    @Extend
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

    @Override
    @Extend
    public String getDimensions() {
        return String.format("BoundingBox{min=(%.2f,%.2f,%.2f), max=(%.2f,%.2f,%.2f)}", minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    @Extend
    public String getData() {
        return data;
    }
}