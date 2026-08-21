# ByteOps Modify API

> **Note:** This module contains only annotations.
> The actual transformation logic is in `api-boot`.
> For compilation use Java Agent with `api-boot` implementation.

**api-modify** — annotation-based API for modifying Java classes at runtime.

### Info:
- [Repository](#repository)
- [Dependency](#dependency)
- [Config](#config)
- [Modify](#modify)
- [Provide](#provide)
- [Extend](#extend)
- [Implementation](#implementation)
- [Inject](#inject)
    - [MethodReference](#method-reference)
    - [Injection Points](#injection-points-at)
    - [Argument](#argument)
    - [InjectResult](#inject-result)
    - [Local and LocalData](#local-and-localdata)
- [Complete Examples](#complete-examples)
- [Important Notes](#important-notices)
- [See Also](#see-also)

---

## Repository:

Maven:
```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

Gradle(groovy):
```groovy
maven { 
    url 'https://jitpack.io' 
}
```

Gradle(kotlin script):
```kotlin
maven { 
    url = uri("https://jitpack.io") 
}
```

[**Back to info**](#info)

---

## Dependency:

Maven:
```xml
<dependency>
    <groupId>com.github.Ares-XI.ByteOps</groupId>
    <artifactId>byteops-api-modify</artifactId>
    <version>v1.0.0-alpha.1</version>
    <scope>provided</scope>
</dependency>
```

Gradle(groovy):
```groovy
compileOnly 'com.github.Ares-XI.ByteOps:byteops-api-modify:v1.0.0-alpha.1'
```

Gradle(kotlin script)
```kotlin
compileOnly("com.github.Ares-XI.ByteOps:byteops-api-modify:v1.0.0-alpha.1")
```

[**Back to info**](#info)

---

## Config:

In `api-boot` config is start point. Config must contain [Json](https://en.wikipedia.org/wiki/JSON) format.
Config serialize class `io.byteops.internal.util.ModifyConfigFormat` which contains those fields:
- Array of [`@Modify`](#modify) classes. (Must be initialized)
- Array of classes, which will be added to classpath if they are not loaded automatically(reflection etc.)

Config must be located in resource folder, is named that way, as indicated in the Java Agent (which uses `api-boot`) and end with `.json`.
If you don't know the value of the config name parameter at startup, use the config name `$byteops.json`, api-boot will parse it automatically anyway.

**Example:**
```json
{
  "modify": [
    "com.example.modifications.FirstMod",
    "com.example.modifications.SecondMod"
  ],
  "classpath": [
    "com.example.utils.HelperClass"
  ]
}
```

[**Back to info**](#info)

---

## Modify:

[`@Modify`](src/main/java/io/byteops/modify/Modify.java) is a parent annotation which use, to get class which will be modified.
`@Modify` must be:
- annotated to ABSTRACT class(if you annotate it into class or interface ot will cause `ModifyFormatExepion` in runtime)
- contain class reference which will be modified.

**Example class which will be modified:**
```java
public class Calculator {
    // ...
    // ...
    // ...
}
```

**Example modify class:**
```java
@Modify(Calculator.class)
public abstract class CalculatorMod {
    // ...
    // ...
    // ...
}
```

[**Back to info**](#info)

---

## Provide:

[`@Provide`](src/main/java/io/byteops/modify/Provide.java) allows you to get a field/method from the target class for work with that field/method.
Field modifiers in target class must equal modifiers in [`@Modify`](#modify) class with `@Provide`. Method signatures, and modifiers in target class should also equal in [`@Modify`](#modify) class with `@Provide`.

**Example class which will be modified:**

```java
public class Calculator {
    private static UUID classUUID = UUID.randomUUID;
    
    private boolean isValid = CalculatorUtils.isValid();

    public int add(int a, int b) {
        return a + b;
    }

    public int subtract(int a, int b) {
        return a - b;
    }

    public int multiply(int a, int b) {
        return a * b;
    }

    public int divide(int a, int b) {
        if (b == 0) throw new ArithmeticException("divide to zero not allowed");
        return a / b;
    }
}
```

**Example modify class:**

```java
@Modify(Calculator.class)
public abstract class CalculatorMod {
    @Provide
    private static UUID classUUID;

    @Provide
    private boolean isValid;

    @Provide
    public int add(int a, int b) { 
        return 0; //fallback value doesn't matter will be used original method in target class
    }
}
```

[**Back to info**](#info)

---

## Extend:

[`@Extend`](src/main/java/io/byteops/modify/Extend.java) allows you to add a new field/method from the target class.
> **Note for `@Extend` fields and methods.** 
> All `@Extend` fields and `@Extend` methods will be added in RUNTIME, it means you won't be able to get/set the `@Extend` field or invoke the `@Extend` method.
> Use [`Implementation`](#implementation) to get/set methods and invoke methods. Or use reflection etc. 

> **Note for `@Extend` fields**. 
> Value of new field will NOT move into target(except for `static final` fields with primitives or `java.lang.String` type, because they contain constant value).
> To set value use [`@Inject`](#inject) into constructors or static init block.


**Example class which will be modified(before):**

```java
public class Calculator {
    private static UUID classUUID = UUID.randomUUID;
    
    private boolean isValid = CalculatorUtils.isValid();

    public int add(int a, int b) {
        return a + b;
    }

    public int subtract(int a, int b) {
        return a - b;
    }

    public int multiply(int a, int b) {
        return a * b;
    }

    public int divide(int a, int b) {
        if (b == 0) throw new ArithmeticException("Деление на ноль");
        return a / b;
    }
}
```

**Example modify class:**

```java
@Modify(Calculator.class)
public abstract class CalculatorMod {
    @Provide
    private static UUID classUUID;

    @Provide
    private boolean isValid;

    @Provide
    public int add(int a, int b) {
        return 0;
    }

    @Extend
    private int doubleOperationCount;

    @Extend
    public double addDouble(double a, double b) {
        if(!isValid) throw new RuntimeException("Calculator is not valid: " + classUUID.toString());
        doubleOperationCount = add(doubleOperationCount, 1);
        return a + b;
    }

    @Extend
    public double subtractDouble(double a, double b) {
        if(!isValid) throw new RuntimeException("Calculator is not valid: " + classUUID.toString());
        doubleOperationCount = add(doubleOperationCount, 1);
        return a - b;
    }

    @Extend
    public double multiplyDouble(double a, double b) {
        if(!isValid) throw new RuntimeException("Calculator is not valid: " + classUUID.toString());
        doubleOperationCount = add(doubleOperationCount, 1);
        return a * b;
    }

    @Extend
    public double divideDouble(double a, double b) {
        if(!isValid) throw new RuntimeException("Calculator is not valid: " + classUUID.toString());
        if (b == 0) throw new ArithmeticException("divide to zero not allowed");
        doubleOperationCount = add(doubleOperationCount, 1);
        return a / b;
    }
    
    @Extend
    public int getDoubleOperationCount() {
        return doubleOperationCount;
    }
}
```

**Example class which will be modified(after):**

```java
public class Calculator {
    private static UUID classUUID = UUID.randomUUID;
    
    private boolean isValid = CalculatorUtils.isValid();
    
    private int doubleOperationCount;

    public int add(int a, int b) {
        return a + b;
    }

    public int subtract(int a, int b) {
        return a - b;
    }

    public int multiply(int a, int b) {
        return a * b;
    }

    public int divide(int a, int b) {
        if (b == 0) throw new ArithmeticException("Деление на ноль");
        return a / b;
    }
    
    public double addDouble(double a, double b) {
        if(!isValid) throw new RuntimeException("Calculator is not valid: " + classUUID.toString());
        doubleOperationCount = add(doubleOperationCount, 1);
        return a + b;
    }
    
    public double subtractDouble(double a, double b) {
        if(!isValid) throw new RuntimeException("Calculator is not valid: " + classUUID.toString());
        doubleOperationCount = add(doubleOperationCount, 1);
        return a - b;
    }
    
    public double multiplyDouble(double a, double b) {
        if(!isValid) throw new RuntimeException("Calculator is not valid: " + classUUID.toString());
        doubleOperationCount = add(doubleOperationCount, 1);
        return a * b;
    }
    
    public double divideDouble(double a, double b) {
        if(!isValid) throw new RuntimeException("Calculator is not valid: " + classUUID.toString());
        if (b == 0) throw new ArithmeticException("divide to zero not allowed");
        doubleOperationCount = add(doubleOperationCount, 1);
        return a / b;
    }
    
    public int getDoubleOperationCount() {
        return doubleOperationCount;
    }
}
```

[**Back to info**](#info)

---

## Implementation:

All implementations in [`@Modify`](#modify) class will be moved into target class, if you override all methods in implementation.

**Example interface:**
```java
public interface DoubleCalculator {
    double addDouble(double d1, double d2);
    double subtractDouble(double d1, double d2);
    double multiplyDouble(double d1, double d2);
    double divideDouble(double d1, double d2);
    int getOperationCount();
}
```

**Example class which will be modified(before):**

```java
public class Calculator {
    private static UUID classUUID = UUID.randomUUID;
    
    private boolean isValid = CalculatorUtils.isValid();

    public int add(int a, int b) {
        return a + b;
    }

    public int subtract(int a, int b) {
        return a - b;
    }

    public int multiply(int a, int b) {
        return a * b;
    }

    public int divide(int a, int b) {
        if (b == 0) throw new ArithmeticException("Деление на ноль");
        return a / b;
    }
}
```

**Example modify class:**

```java
@Modify(Calculator.class)
public abstract class CalculatorMod implements DoubleCalculator {
    @Provide
    private static UUID classUUID;

    @Provide
    private boolean isValid;

    @Provide
    public int add(int a, int b) {
        return 0;
    }

    @Extend
    private int doubleOperationCount;

    @Extend
    @Override
    public double addDouble(double a, double b) {
        if(!isValid) throw new RuntimeException("Calculator is not valid: " + classUUID.toString());
        doubleOperationCount = add(doubleOperationCount, 1);
        return a + b;
    }

    @Extend
    @Override
    public double subtractDouble(double a, double b) {
        if(!isValid) throw new RuntimeException("Calculator is not valid: " + classUUID.toString());
        doubleOperationCount = add(doubleOperationCount, 1);
        return a - b;
    }

    @Extend
    @Override
    public double multiplyDouble(double a, double b) {
        if(!isValid) throw new RuntimeException("Calculator is not valid: " + classUUID.toString());
        doubleOperationCount = add(doubleOperationCount, 1);
        return a * b;
    }

    @Extend
    @Override
    public double divideDouble(double a, double b) {
        if(!isValid) throw new RuntimeException("Calculator is not valid: " + classUUID.toString());
        if (b == 0) throw new ArithmeticException("divide to zero not allowed");
        doubleOperationCount = add(doubleOperationCount, 1);
        return a / b;
    }
    
    @Extend
    @Override
    public int getDoubleOperationCount() {
        return doubleOperationCount;
    }
}
```

**Example class which will be modified(after):**

```java
public class Calculator implements DoubleCalculator {
    private static UUID classUUID = UUID.randomUUID;
    
    private boolean isValid = CalculatorUtils.isValid();
    
    private int doubleOperationCount;

    public int add(int a, int b) {
        return a + b;
    }

    public int subtract(int a, int b) {
        return a - b;
    }

    public int multiply(int a, int b) {
        return a * b;
    }

    public int divide(int a, int b) {
        if (b == 0) throw new ArithmeticException("Деление на ноль");
        return a / b;
    }
    
    @Override
    public double addDouble(double a, double b) {
        if(!isValid) throw new RuntimeException("Calculator is not valid: " + classUUID.toString());
        doubleOperationCount = add(doubleOperationCount, 1);
        return a + b;
    }

    @Override
    public double subtractDouble(double a, double b) {
        if(!isValid) throw new RuntimeException("Calculator is not valid: " + classUUID.toString());
        doubleOperationCount = add(doubleOperationCount, 1);
        return a - b;
    }

    @Override
    public double multiplyDouble(double a, double b) {
        if(!isValid) throw new RuntimeException("Calculator is not valid: " + classUUID.toString());
        doubleOperationCount = add(doubleOperationCount, 1);
        return a * b;
    }

    @Override
    public double divideDouble(double a, double b) {
        if(!isValid) throw new RuntimeException("Calculator is not valid: " + classUUID.toString());
        if (b == 0) throw new ArithmeticException("divide to zero not allowed");
        doubleOperationCount = add(doubleOperationCount, 1);
        return a / b;
    }

    @Override
    public int getDoubleOperationCount() {
        return doubleOperationCount;
    }
}
```

**Usage:**
```java
Calculator calculator = new Calculator();
DoubleCalculator doubleCalc = (DoubleCalculator) calculator; //you can do invoke inlined

double result = doubleCalc.addDouble(5.5, 3.2);
int count = doubleCalc.getDoubleOperationCount();

System.out.println("Result: " + result);
System.out.println("Operations performed: " + count);
```

**Output:**
```
Result: 8.7
Operations performed: 1
```


[**Back to info**](#info)

---

## Inject:

[`@Inject`](src/main/java/io/byteops/modify/Inject.java) is an annotation which help you add code into ready methods. 
`@Inject` must be annotated to method which returns [`InjectResult`](#inject-result). 
If target method `static`, `@Inject` method must be `static`. 

`@Inject` parameters:
- [MethodReference](#method-reference)(`@MethodReference`) (doesn't have a default value(required parameter))
- [Inject point](#inject-point)(`At`) (doesn't have a default value(required parameter))
- Index(`int`) (default value: 0)
- Priority(`int`) (default value: 0)

### Method Reference:
[`@MethodReference`](src/main/java/io/byteops/modify/util/MethodReference.java) is a data annotation which used in [`@Inject`](#inject) to set a details in which method do inject.

`@MethodReference` parameters:
- Method name(`String`)
- Parameter types(`Class<?>[]`) default value: empty array
- Result type(`Class<?>[]`) default: `void.class`

>**Note.**
> Constructors(and static init block) in bytecode looks like method with parameters with void result. 
> To inject code into constructor use method name `<init>`(or `<clinit>` if static init block)

### Inject Point:

[`At`](src/main/java/io/byteops/modify/util/At.java) is an enum class with points. List of inject point below:

### Injection Points (`At`)

| Injection Point | In Java Code                                                                        | In JVM Instructions                                             | Is `index` Support |
|-----------------|-------------------------------------------------------------------------------------|-----------------------------------------------------------------|--------------------|
| `HEAD`          | before the very beginning of the code                                               | First Instruction                                               | No                 |
| `RETURN`        | before `return`                                                                     | `RETURN`, `ARETURN`, `IRETURN`, `LRETURN`, `FRETURN`, `DRETURN` | Yes                |
| `INVOKE`        | before invoke(methods, lamdas, constructors, binary operators, `instanceof`, casts) | see list below                                                  | Yes                |
| `NEW`           | before `new`, creating primitives                                                   | `NEW`, `NEW_ARRAY`, `NEW_MULTI_ARRAY`                           | Yes                |
| `GET`           | before `this`, getting local variables, fields, `static` fields, arrays `.lenght`   | see list below                                                  | Yes                |
| `PUT`           | before setting local variables, fields, `static` fields                             | see list below                                                  | Yes                |
| `THROW`         | before `throw`                                                                      | `ATHROW`                                                        | Yes                |


Get instructions:
```
GETFIELD
GETSTATIC
ILOAD
LLOAD
FLOAD
DLOAD
ALOAD
ARRAYLENGTH
IALOAD
LALOAD
FALOAD
DALOAD
AALOAD
BALOAD
CALOAD
SALOAD
LDC
BIPUSH
SIPUSH
ICONST_M1
ICONST_0
ICONST_1
ICONST_2
ICONST_3
ICONST_4
ICONST_5
LCONST_0
LCONST_1
FCONST_0
FCONST_1
FCONST_2
DCONST_0
DCONST_1
ACONST_NULL
```
Put instructions:
```
PUTFIELD
PUTSTATIC
ISTORE
LSTORE
FSTORE
DSTORE
ASTORE
IASTORE
LASTORE
FASTORE
DASTORE
AASTORE
BASTORE
CASTORE
SASTORE
```
Invoke instructions:
```
INVOKEVIRTUAL
INVOKESTATIC
INVOKESPECIAL
INVOKEINTERFACE
INVOKEDYNAMIC

IADD
ISUB
IMUL
IDIV
IREM
LADD
LSUB
LMUL
LDIV
LREM
FADD
FSUB
FMUL
FDIV
FREM
DADD
DSUB
DMUL
DDIV
DREM
ISHL
ISHR
IUSHR
LSHL
LSHR
LUSHR
IAND
IOR
IXOR
LAND
LOR
LXOR

CHECKCAST
INSTANCEOF

IFEQ
IFNE
IFLT
IFGE
IFGT
IFLE
IF_ICMPEQ
IF_ICMPNE
IF_ICMPLT
IF_ICMPGE
IF_ICMPGT
IF_ICMPLE
IF_ACMPEQ
IF_ACMPNE
IFNULL
IFNONNULL
LCMP
FCMPL
FCMPG
DCMPL
DCMPG

GOTO
TABLESWITCH
LOOKUPSWITCH
RETURN
ARETURN
IRETURN
LRETURN
FRETURN
DRETURN
JSR
RET

I2L
I2F
I2D
I2B
I2C
I2S
L2I
L2F
L2D
F2I
F2L
F2D
D2I
D2L
D2F
```

> **Note.**
> Don't do inline inject, because JVM stack can return wrong type, and code cause `VerifyError`. 
> To currently inject code: compile method, watch instructions, select current point and inject before that point.

### Argument:

[`@Arg`](src/main/java/io/byteops/modify/Arg.java) is an annotation which help to get argument from injected method. 
Value of annotation is an index from target method parameters index. 0 -> first parameter from target method.
Type from target method parameter and inject method parameter must equal.

### Inject Result:

[`InjectResult`](src/main/java/io/byteops/modify/util/InjectResult.java) is a data class which help to return custom result, or throw `RuntimeException`. 
`InjectResult` contain Generic type that will be returned.
`InjectResult` has three factory methods to init:

| Method                            | Description                                        | Return Type |
|-----------------------------------|----------------------------------------------------|-------------|
| `InjectResult.pass()`             | Continue normal execution                          | `Void`      |
| `InjectResult.stop()`             | Stop execution without returning a value           | `Void`      |
| `InjectResult.stop(T value)`      | Stop execution and return `value`                  | `T`         |
| `InjectResult.error(Throwable t)` | Stop execution and throw `t` as `RuntimeException` | `Void`      |

### Local and LocalData:

[`@Local`](src/main/java/io/byteops/modify/Local.java) is an annotation which helps to get a local variable from injected method.
Value of annotation is an index from target method local variable index. 0 -> first local variable from target method.
Type from target method local variable and inject method local variable must equal.

> **Warning.**
> If you try to get Local variable in injector, which called before that they're creating, code cause `VerifyError`.

[`LocalData`](src/main/java/io/byteops/modify/util/LocalData.java) is a data class which help you set data to local variables in target method.
Constructor:
- index(int) must equal to @Local index.
- value(Object) this is value which will be set to local variable. Type must be equal local variable in target method.

To set local variable use method in [InjectResult](#inject-result):
```java
withLocals(LocalData... locals);
```

### Examples:

If you don't understand how to use injectors, you can watch examples:

[`Examples`](../../examples/InjectExample.md)

[**Back to info**](#info)

---

## Complete examples:

To see complete examples you can watch it into examples ->
[`ModifyExamples`](../../examples/ModifyExample.md)

[**Back to info**](#info)

---

## Important notices

- **This Reference:**

To get `this` reference from target class use double cast to object:
```java
((CalculatorMod) ((Object) this));
```

- **Superclasses are not inherited:**

Super classes will not be moved to the target class, an alternative use `interface`

[**Back to info**](#info)

---

## See Also:
- [Boot API](../byteops-api-boot/README.md) — launch modification API.
- [Examples](../../examples) - package with some examples and help how to use `api-boot` and `api-modify`

---

## License

MIT License © 2026
