# ByteOps (Class File Transformer by Annotation Processor)

**ByteOps** — runtime bytecode modification library for Java, built on top of `java.lang.instrument.Instrumentation`.

Perfect for:
- **Debugging & Profiling** — trace method calls, log arguments
- **Hot patching/fix** — fix bugs without restarting the application
- **Modding** — modify game or application behavior on the fly

---

## Modules:

### `byteops-boot` — core bootstrapping API
Used in custom Java Agents. Contains all instruments to launch ByteOps with arguments.

[Boot API Info →](modules/api-boot/README.md)

### `byteops-modify` — class modification API
Used in projects with modifications. Contains annotations for modifying classes.

[Modify API Info →](modules/api-modify/README.md)