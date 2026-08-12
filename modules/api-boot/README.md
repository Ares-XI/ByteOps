# ByteOps Boot API

> **Warning:** Do not launch ByteOps in Java Agents attached via the Java Attach API.
> ByteOps will not work stably because `api-modify` adds fields and methods into the target class,
> which is not supported by agents attached via Attach API (retransforming/redefining classes does not support adding fields and methods).
> In the next release, I plan to add a Proxy Mode which will work with the Java Attach API.

**api-boot** — core module for bootstrapping the ByteOps system inside Java Agents.

This module provides:
- `BootManager` — main entry point for initialization
- `BootFlag` — configuration nodes for customizing behavior

---

## Dependency:
Maven:
```xml
<dependency>
    <groupId>io.byteops</groupId>
    <artifactId>api-boot</artifactId>
    <version>1.0.0</version>
</dependency>
```

Gradle(groovy):
```groovy
implementation 'io.byteops:api-boot:1.0.0'
```

Gradle(kts)
```kotlin
implementation("io.byteops:api-boot:1.0.0")
```

---

## Starting:

To start boot api you need:
- Instrumentation (provided in Java Agents)
- Array of JAR libraries (`File[]`).
- Array of JARs which contain modifications(`File[]`).
- Array of flags to start(`BootFlag[]`).

Start method:
```
io.byteops.boot.BootManager.init(Instrumentation inst, File[] libs, File[] classpath, BootFlag... args)
```

### Flags to start:

| Flag                                       | Description                                 | Cardinality                                              | Default Value |
|--------------------------------------------|---------------------------------------------|----------------------------------------------------------|---------------|
| `BootFlag.Name(String name)`               | Sets the agent name                         | Once (last wins)                                         | `"ByteOps"` |
| `BootFlag.Version(String version)`         | Sets the agent version                      | Once (last wins)                                         | `"1.0-alpha-build-0"` |
| `BootFlag.ConfigName(String name)`         | JSON config filename in JARs with modifings | Once (last wins)                                         | `"byte-ops"` |
| `BootFlag.BlockedClass(Class<?> classRef)` | Blocks class from modification              | Multiple(one boot flag(blockedClass) -> +1 blockedClass) | See default list below |

### Packages which blocked by default:
```
"java/**"
"jdk/**"
"sun/**"
"com/google/gson/**"
"org/intellij/**"
"org/jetbrains/**"
"org/objectweb/asm/**"
"io/byteops/**"
```

---

## Example:

### Java Agent main class:

```java
package io.gammax;

import io.byteops.boot.BootManager;
import io.byteops.boot.BootFlag;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class GammaStart {
    public static void premain(String args, Instrumentation instrumentation) {
        List<File> libs = new ArrayList<>();
        List<File> classpath = new ArrayList<>();

        //Get all jar files(recursive) in those folders:
        String[] extraDirs = {"libraries", "cache", "versions"};
        for (String extraDir : extraDirs) {
            try (Stream<Path> stream = Files.walk(Paths.get(extraDir)).filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".jar"))) {
                stream.forEach(path -> libs.add(path.toFile()));
            } catch (IOException e) {
                e.printStackTrace(System.err);
            }
        }

        //Get all jar files(not recursive) in "plugin" folder:
        try (Stream<Path> stream = Files.list(Paths.get("plugins"))) {
            stream.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".jar")).forEach(path -> classpath.add(path.toFile()));
        } catch (IOException e) {
            e.printStackTrace(System.err);
        }
        
        //Launch ByteOps with args
        BootManager.init(
                instrumentation, //Provided instrumentation 
                libs.toArray(new File[0]), // Jar libraries
                classpath.toArray(new File[0]), //Jars with modifications
                new BootFlag.Name("GammaX"), //Argument of name
                new BootFlag.Version("1.0-alpha"), //Argument of Version
                new BootFlag.ConfigName("gamma"), //Argument of json name which will be searched 
                new BootFlag.BlockedClass(GammaStart.class) //Argument of blocked class
        );
    }
}
```

### Command to start:

To start application with Java Agent you need add JVM flag to start(`-javaagent:your-agent.jar`).

Default start:

```
java -jar application.jar
```

Start with Java Agent:

```
java -javaagent:GammaX-1.0.jar -jar application.jar
```

### Output:
```
=====================================
|| GammaX started! Version: 1.0-alpha
=====================================
Find <n> gamma.json files
Start parsing
...
...
```

`<n>` -> count of parsed files

---

## See Also:
- [Modify API](../api-modify/README.md) — annotation-based modification API
---

## License

MIT License © 2026