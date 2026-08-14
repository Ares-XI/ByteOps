package io.gammax.test.modifys;

import io.byteops.modify.Arg;
import io.byteops.modify.Extend;
import io.byteops.modify.Modify;
import io.byteops.modify.Inject;
import io.byteops.modify.util.At;
import io.byteops.modify.util.InjectResult;
import io.byteops.modify.util.MethodReference;
import org.bukkit.util.NumberConversions;

@Modify(NumberConversions.class)
public abstract class NumberConversionsMixin {
    @Extend
    private static int floorCallCount;

    @Inject(
            at = At.HEAD,
            method = @MethodReference(
                    method = "floor",
                    parameters = double.class,
                    result = int.class
            )
    )
    private static InjectResult<Void> onFloor(@Arg(0) double num) {
        floorCallCount++;
        System.out.println("[Inject HEAD] floor() called with " + num + " (total calls: " + floorCallCount + ")");
        return InjectResult.pass();
    }

    @Extend
    public static void resetCount() {
        floorCallCount = 0;
    }

    @Extend
    public static void addCount() {
        floorCallCount++;
    }

    @Extend
    public static int getFloorCalls() {
        return floorCallCount;
    }
}