package io.gammax.test.mixins;

import io.gammax.api.Extend;
import io.gammax.api.Modify;
import io.gammax.api.Provide;
import org.bukkit.util.NumberConversions;

@Modify(NumberConversions.class)
public abstract class NumberConversionsMixin {
    @Provide
    public static native int floor(double num);

    @Extend
    private static int floorCallCount;

//    @Inject(
//            method = "floor",
//            at = At.HEAD,
//            signature = @Signature(
//                    parameters = double.class,
//                    result = int.class
//            )
//    )
//    private static void onFloor(@Arg(0) double num) {
//        floorCallCount++;
//        System.out.println("[Inject HEAD] floor() called with " + num + " (total calls: " + floorCallCount + ")");
//    }

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