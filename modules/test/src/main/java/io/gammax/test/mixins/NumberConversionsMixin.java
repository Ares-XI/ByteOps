package io.gammax.test.mixins;

import io.gammax.api.Arg;
import io.gammax.api.Extend;
import io.gammax.api.Modify;
import io.gammax.api.Inject;
import io.gammax.api.util.At;
import io.gammax.api.util.InjectResult;
import io.gammax.api.util.MethodReference;
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