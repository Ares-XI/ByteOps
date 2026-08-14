# Injectors:

## Basic injector to constructor:

**Target method before injector:**
```java
public BoundingBox(double x1, double y1, double z1, double x2, double y2, double z2) {
    this.resize(x1, y1, z1, x2, y2, z2);
}
```

**Injector:**
```java
@Inject(at = At.HEAD, method = @MethodReference(method = "<init>", parameters = {double.class, double.class, double.class, double.class, double.class, double.class}))
    private InjectResult<Void> onTest() {
        System.out.println("init:" + ((Object) this).getClass().getName());
        return InjectResult.pass();
    } //This is injector to constructor with 6 double parameters.
```

**Target method after injector:**
```java
private InjectResult<Void> injector$4accd1e0c6e441f2b14b63d2412ce7a2() {
    System.out.println("init:" + ((Object) this).getClass().getName());
    return InjectResult.pass();
}

public BoundingBox(double x1, double y1, double z1, double x2, double y2, double z2) {
    try {
        InjectResult result = injector$4accd1e0c6e441f2b14b63d2412ce7a2();
        if(result.isStop()) return;
    } catch (Throwable t) {
        throw new RuntimeException(t);
    }
    this.resize(x1, y1, z1, x2, y2, z2);
}
```

## Injector to constructor with arguments:

**Target method before injector:**
```java
public BoundingBox(double x1, double y1, double z1, double x2, double y2, double z2) {
    this.resize(x1, y1, z1, x2, y2, z2);
}
```

**Injector:**
```java
@Inject(at = At.HEAD, method = @MethodReference(method = "<init>", parameters = {double.class, double.class, double.class, double.class, double.class, double.class,}))
private InjectResult<Void> onTest(@Arg(0) double minX) {
    System.out.println("init:" + ((Object) this).getClass().getName() + ", minX: " + minX);
    return InjectResult.pass();
}
```

**Target method after injector:**
```java
private InjectResult<Void> injector$119c71d31e9e45f986db7ae4d23ee7a3(double minX) {
    System.out.println("init:" + ((Object) this).getClass().getName() + ", minX: " + minX);
    return InjectResult.pass();
}

public BoundingBox(double x1, double y1, double z1, double x2, double y2, double z2) {
    try {
        InjectResult result = injector$119c71d31e9e45f986db7ae4d23ee7a3(x1);
        if(result.isStop()) return;
    } catch (Throwable t) {
        throw new RuntimeException(t);
    }
    this.resize(x1, y1, z1, x2, y2, z2);
}
```

## Static injector to method with arguments:

**Target method before injector:**
```java
public static int floor(double num) {
    int floor = (int)num;
    return (double)floor == num ? floor : floor - (int)(Double.doubleToRawLongBits(num) >>> 63);
}
```

**Injector:**
```java
@Extend
private static int floorCallCount;

@Inject(at = At.HEAD, method = @MethodReference(method = "floor", parameters = double.class, result = int.class))
private static InjectResult<Void> onFloor(@Arg(0) double num) {
    floorCallCount++;
    System.out.println("[Inject HEAD] floor() called with " + num + " (total calls: " + floorCallCount + ")");
    return InjectResult.pass();
}
```

**Target method after injector:**
```java
private static int floorCallCount = 0; //Default value of integer is 0 if not inited;

private InjectResult<Void> injector$ec3e2081973049c195694b331dbe22d2(double num) { //Name of injector is generated, injector use random UUID
    floorCallCount++;
    System.out.println("[Inject HEAD] floor() called with " + num + " (total calls: " + floorCallCount + ")");
    return InjectResult.pass();
}

public static int floor(double num) {
    try {
        InjectResult result = injector$ec3e2081973049c195694b331dbe22d2(num);
        if(result.isStop()) return ((int) result.getValue());
    } catch (Throwable t) {
        throw new RuntimeException(t);
    }
    int floor = (int)num;
    return (double)floor == num ? floor : floor - (int)(Double.doubleToRawLongBits(num) >>> 63);
}
```