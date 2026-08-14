package io.gammax.test.commads;

import io.gammax.test.access.BoundingBoxAccess;
import io.gammax.test.access.VectorAccess;
import io.gammax.test.some.Test;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.NumberConversions;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class TestMixinCommand implements CommandExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        sender.sendMessage("§e=== GammaX Mixin Tests ===\n");

        try {
            System.out.println("==== Vector ====");
            for (Method method: Vector.class.getDeclaredMethods()) System.out.println(method.getName());
            for (Field field: Vector.class.getDeclaredFields()) System.out.println(field.getName());

            System.out.println("================");
            System.out.println();
            System.out.println("==== BoundingBox ====");

            for (Method method: BoundingBox.class.getDeclaredMethods()) System.out.println(method.getName());
            for (Field field: BoundingBox.class.getDeclaredFields()) System.out.println(field.getName());

            System.out.println("================");
            System.out.println();
            System.out.println("==== NumberConversions ====");

            for (Method method: NumberConversions.class.getDeclaredMethods()) System.out.println(method.getName());
            for (Field field: NumberConversions.class.getDeclaredFields()) System.out.println(field.getName());

            System.out.println("================");
            System.out.println();

            testBoundingBox(sender);
            testNumberConversions(sender);
            testVector(sender);

            sender.sendMessage("§a✅ Все тесты успешно пройдены!");

        } catch (Exception e) {
            sender.sendMessage("§c❌ Тест провален: " + e.getMessage());
            e.printStackTrace(System.err);
        }

        return true;
    }

    private void testVector(CommandSender sender) throws Exception {
        sender.sendMessage("§6=== Тест Vector (интерфейсы) ===");

        Vector vec = new Vector(1, 2, 3);

        vec.multiply(2.0);
        vec.add(new Vector(1, 1, 1));
        vec.multiply(new Vector(1, 2, 3));

        Vector vec2 = vec.clone();
        sender.sendMessage("§7Создан вектор: " + vec);
        sender.sendMessage("создан ёщё один вектор: " + vec2);
        sender.sendMessage("Вектор: " + vec.isNormalized());

        System.out.println(vec2.hashCode());
        System.out.println(vec.angle(vec2));

        Vector vec3 = new Vector(1, 2, 3);
        vec3.rotateAroundY(Math.PI / 4);
        sender.sendMessage("x after rotate: " + vec3.getX());

        try {
            VectorAccess access = (VectorAccess) vec;

            sender.sendMessage("§7getStats(): " + access.getStats());
            sender.sendMessage("§7getOperationCount(): " + access.getOperationCount());
            sender.sendMessage("§7getLastOperation(): " + access.getLastOperation());
            sender.sendMessage("§7isZeroS(): " + access.isZeroS());

            sender.sendMessage("§7plusThis(): " + access.plusThis().toString());

            sender.sendMessage("§7setOperationCount(10)");
            access.setOperationCount(10);
            sender.sendMessage("§7setLastOperation(\"someOperation\")");
            access.setLastOperation("someOperation");

            vec.multiply(2);
            sender.sendMessage("§7После multiply(2): " + vec);

            double length = vec.length();
            sender.sendMessage("§7length(): " + length);

            vec.clone();
            sender.sendMessage("§7После clone()");

            vec.add(new Vector(1, 1, 1));
            sender.sendMessage("§7После add(1,1,1): " + vec);

            sender.sendMessage("§7getStats() после операций: " + access.getStats());
            sender.sendMessage("§7getOperationCount(): " + access.getOperationCount());
            sender.sendMessage("§7getLastOperation(): " + access.getLastOperation());

            access.setTest(Test.VAL1);
            System.out.println("Test: " + access.getTest().name());

        } catch (ClassCastException e) {
            sender.sendMessage("§cИнтерфейс VectorAccess не найден, используем рефлексию");

            Method getStats = vec.getClass().getMethod("getStats");
            Method getOpCount = vec.getClass().getMethod("getOperationCount");
            Method getLastOp = vec.getClass().getMethod("getLastOperation");

            sender.sendMessage("§7getStats(): " + getStats.invoke(vec).toString());
            sender.sendMessage("§7getOperationCount(): " + getOpCount.invoke(vec));
            sender.sendMessage("§7getLastOperation(): " + getLastOp.invoke(vec));

            e.printStackTrace(System.err);
        }

        sender.sendMessage("");
    }

    private void testBoundingBox(CommandSender sender) throws Exception {
        sender.sendMessage("§6=== Тест BoundingBox (интерфейсы) ===");

        BoundingBox box = new BoundingBox(0, 0, 0, 10, 10, 10);
        sender.sendMessage("§7Создан бокс: " + box);

        box.shift(1.0, 1.0, 1.0);
        sender.sendMessage(String.valueOf(box.getVolume()));
        sender.sendMessage(String.valueOf(box.contains(1.0, 1.0, 1.0)));

        try {
            BoundingBoxAccess access = (BoundingBoxAccess) box;

            sender.sendMessage("§7getDimensions(): " + access.getDimensions());

            access.expandSymmetrical(1.0);
            sender.sendMessage("§7После expandSymmetrical(1): " + access.getDimensions());

            box.shift(2, 2, 2);
            sender.sendMessage("§7После shift(2,2,2): " + box);

            boolean contains1 = box.contains(5, 5, 5);
            sender.sendMessage("§7contains(5,5,5): " + contains1);

            boolean contains2 = box.contains(15, 15, 15);
            sender.sendMessage("§7contains(15,15,15): " + contains2);

            double volume = box.getVolume();
            sender.sendMessage("§7Объём: " + volume);

            sender.sendMessage(String.valueOf(box.getMinX()));

            sender.sendMessage("§7getDimensions() после: " + access.getDimensions());
            sender.sendMessage("§7getData(): " + access.getData());

        } catch (ClassCastException e) {
            sender.sendMessage("§cИнтерфейс BoundingBoxAccess не найден, используем рефлексию");

            Method getDimensions = box.getClass().getMethod("getDimensions");
            Method getCheckCount = box.getClass().getMethod("getCheckCount");
            Method expandSym = box.getClass().getMethod("expandSymmetrical", double.class);

            sender.sendMessage("§7getDimensions(): " + getDimensions.invoke(box));
            sender.sendMessage("§7getCheckCount(): " + getCheckCount.invoke(box));

            expandSym.invoke(box, 1.0);
            sender.sendMessage("§7После expandSymmetrical(1): " + getDimensions.invoke(box));

            e.printStackTrace(System.err);
        }

        sender.sendMessage("");
    }

    private void testNumberConversions(CommandSender sender) throws Exception {
        sender.sendMessage("§6=== Тест NumberConversions (статические методы) ===");

        Method resetMethod = NumberConversions.class.getDeclaredMethod("resetCount");
        resetMethod.setAccessible(true);
        resetMethod.invoke(null);

        sender.sendMessage(String.valueOf(NumberConversions.floor(1.0)));

        int f1 = NumberConversions.floor(5.7);
        int f2 = NumberConversions.floor(1000000.5);
        int f3 = NumberConversions.floor(3.2);
        int f4 = NumberConversions.floor(-2.3);

        sender.sendMessage("§7floor(5.7) = " + f1);
        sender.sendMessage("§7floor(1000000.5) = " + f2);
        sender.sendMessage("§7floor(3.2) = " + f3);
        sender.sendMessage("§7floor(-2.3) = " + f4);

        Method getCallsMethod = NumberConversions.class.getDeclaredMethod("getFloorCalls");
        getCallsMethod.setAccessible(true);
        int calls = (int) getCallsMethod.invoke(null);

        sender.sendMessage("§7Всего вызовов floor: " + calls);

        sender.sendMessage("");
    }
}