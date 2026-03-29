# WorldWind Reforged

A modernised and extended build of the [NASA World Wind Java](https://worldwind.arc.nasa.gov/java/) SDK,
developed by [seaglassfoundry.com](https://seaglassfoundry.com).

WorldWind Reforged preserves, maintains, and advances the NASA World Wind Java platform so that it
remains a viable open-source 3D globe toolkit for researchers, educators, and developers.

## What's New in Reforged

- **Maven build** — Fully mavenised project replacing the original Ant/manual-JAR build. Dependencies
  (JOGL 2.6.0, Gluegen 2.6.0, Jackson 2.18.6, GDAL 3.12.0, FlatLaf 3.4.1) are managed automatically.
  The 75 embedded Jackson 1.x source files have been removed and replaced with the Maven dependency.
- **Java 17 modernisation** — Compiles and runs on Java 17+ with all required `--add-opens`
  flags. Raw types fixed (~35 files), diamond operator (~1,138 instances), try-with-resources (31
  blocks), lambda expressions (43 conversions), pattern matching instanceof (54 conversions), switch
  expressions (~30 conversions), StringBuffer to StringBuilder (31 instances). Dead NASA WMS server
  errors downgraded from SEVERE to WARNING.
- **Modern WMS engine** — GIBS time-series animation, layer preview, and opacity blending
- **GLSL shader-based rendering** — Dashed lines, procedural fill patterns, and GPU styling
- **GPU-accelerated terrain** — Shader heightmaps (GL 3.0+), hardware tessellation (GL 4.0+),
  compute-shader mesh generation (GL 4.3+), and crack-free LOD stitching
- **Performance** — Pick optimisation, tile invalidation, heightmap throttling, VAO rendering, FPS stats
- **Bug fixes** — Event consumption, shutdown handling, AMD driver workarounds, and more
- **Examples** — New ExamplesIndex launcher with dark-themed UI, category browser, and comprehensive
  in-app documentation for every example. New examples: GPU Terrain Demo, WMS Time-Series Demo,
  Layer Opacity & Blending, Dashed Lines, Procedural Fill Patterns, Surface Shape Showcase,
  GeoJSON Viewer, Coordinate Search, Measure Tool, and Terrain Rendering Benchmark. All existing
  examples fixed for proper `DISPOSE_ON_CLOSE` when launched from the browser.
- Full backwards compatibility with the original WorldWind Java API

## Requirements

| Requirement | Minimum | Notes |
|---|---|---|
| **Java** | JDK 17+ | JDK, not JRE — required for `--add-opens` flags |
| **Maven** | 3.8+ | Dependency management and build |
| **OpenGL** | 2.0+ | 3.0+ for Phase 3 shaders; 4.0+ for tessellation; 4.3+ for compute shaders |
| **Network** | Internet | WMS/GIBS imagery tiles are downloaded on first run |

## Building

```bash
mvn clean compile
```

## Running

### From Maven

```bash
# Launch the example browser
mvn exec:java -Dexec.mainClass="gov.nasa.worldwindx.examples.ExamplesIndex"

# Launch any individual example
mvn exec:java -Dexec.mainClass="gov.nasa.worldwindx.examples.SimplestPossibleExample"
```

Maven applies the required VM arguments automatically via `.mvn/jvm.config`.

### From an IDE (Eclipse, IntelliJ)

Import the project as a **Maven project**, then run any example class with a `main()` method.

You **must** add the following VM arguments to your run configuration:

```
--add-opens java.base/java.lang=ALL-UNNAMED
--add-opens java.base/java.lang.reflect=ALL-UNNAMED
--add-opens java.base/java.io=ALL-UNNAMED
--add-opens java.base/java.nio=ALL-UNNAMED
--add-opens java.base/java.util=ALL-UNNAMED
--add-opens java.logging/java.util.logging=ALL-UNNAMED
--add-opens java.desktop/java.awt=ALL-UNNAMED
--add-opens java.desktop/java.awt.peer=ALL-UNNAMED
--add-opens java.desktop/sun.awt=ALL-UNNAMED
--add-opens java.desktop/sun.awt.windows=ALL-UNNAMED
--add-opens java.desktop/sun.java2d=ALL-UNNAMED
--add-opens java.desktop/javax.swing=ALL-UNNAMED
```

### Why are these flags required?

These flags are required by **JOGL** (Java OpenGL), not by WorldWind itself. JOGL must use reflective
access and JNI to bind OpenGL contexts to Java's AWT/Swing graphics system. This requires access to
internal JDK packages — `sun.awt`, `sun.java2d`, AWT peer classes, and NIO buffer internals — that
Java's module system (JPMS, introduced in Java 9) blocks by default.

Starting with Java 16, the `--illegal-access=permit` workaround was removed entirely, making
explicit `--add-opens` flags the only way to grant this access. This affects **every** JOGL-based
application on Java 17+, not just WorldWind. JOGL 2.6.0 (the latest release, actively maintained as
of 2026) still requires these flags, and there is no planned version of JOGL that eliminates them —
the constraint is architectural. Only the JDK itself could remove the need by adding `opens`
directives to the `java.desktop` module, which Oracle/OpenJDK has no plans to do.

**What each group of flags unlocks:**

| Flags | Required by | Purpose |
|---|---|---|
| `java.desktop/sun.awt`, `sun.awt.windows`, `sun.java2d` | JOGL | Binding OpenGL to native window surfaces via AWT peers and Java2D |
| `java.desktop/java.awt`, `java.awt.peer` | JOGL | Creating and managing OpenGL-capable AWT Canvas/Panel components |
| `java.desktop/javax.swing` | JOGL | Swing integration for `GLJPanel` and `TextRenderer` |
| `java.base/java.nio` | JOGL/Gluegen | Direct buffer allocation and management for GPU data transfer |
| `java.base/java.lang`, `java.lang.reflect` | JOGL/Gluegen, GDAL | Native library loading, reflective method dispatch for platform APIs |
| `java.base/java.io`, `java.base/java.util` | JOGL/Gluegen | Internal I/O and collection access during native code marshalling |
| `java.logging/java.util.logging` | JOGL | Logger configuration during initialization |

When running via Maven, these flags are applied automatically through `.mvn/jvm.config` and the
Surefire plugin configuration in `pom.xml` — no manual setup is needed. The JAR manifest also
includes them via the `Add-Opens` entry. You only need to add them manually when running from an
IDE.

> **Note:** On Linux / macOS the `sun.awt.windows` flag can be omitted — it is Windows-specific and
> is harmlessly ignored on other platforms.

### Where to add VM arguments in Eclipse

1. **Run > Run Configurations...**
2. Select your Java Application configuration (or create one)
3. Go to the **Arguments** tab
4. Paste all `--add-opens` flags into the **VM arguments** text box
5. Click **Apply**, then **Run**

### Where to add VM arguments in IntelliJ

1. **Run > Edit Configurations...**
2. Select your configuration (or create one)
3. Click **Modify options > Add VM options** (if the field is not visible)
4. Paste all `--add-opens` flags into the **VM options** field
5. Click **OK**, then run

## Dependencies

All dependencies are managed by Maven and downloaded automatically:

| Library | Version | License |
|---|---|---|
| JOGL | 2.6.0 | BSD |
| Gluegen | 2.6.0 | BSD |
| Jackson Core | 2.18.6 | Apache 2.0 |
| GDAL Java | 3.12.0 | MIT |
| FlatLaf | 3.4.1 | Apache 2.0 |
| JUnit | 4.13.2 | EPL 1.0 (test scope) |

## Acknowledgements

This project would not exist without the extraordinary work of the people who created and maintained
NASA World Wind. We owe an enormous debt of gratitude to:

- The **NASA Ames Research Center** team who conceived and built World Wind as a free, open-source
  platform for exploring the Earth and beyond.
- **Patrick Hogan** — NASA World Wind project founder and visionary, whose leadership made this
  technology available to the world.
- **Tom Gaskins**, **Dave Collins**, and the many NASA and contractor engineers whose names appear
  throughout the source code — each commit, each class, and each carefully written Javadoc comment
  represents years of dedicated effort.
- The wider **World Wind open-source community** — contributors, testers, and users who filed issues,
  submitted patches, wrote examples, and kept the project alive long after official NASA development
  slowed.

Thank you, sincerely, for building something remarkable and sharing it with everyone.

## License

The original NASA World Wind Java SDK is licensed under the
**Apache License, Version 2.0**:

> Copyright 2006-2009, 2017, 2020 United States Government, as represented by the Administrator of
> the National Aeronautics and Space Administration. All rights reserved.
>
> Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
> in compliance with the License. You may obtain a copy of the License at
> http://www.apache.org/licenses/LICENSE-2.0
>
> Unless required by applicable law or agreed to in writing, software distributed under the License
> is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
> or implied. See the License for the specific language governing permissions and limitations under
> the License.

WorldWind Reforged modifications and additions by [seaglassfoundry.com](https://seaglassfoundry.com)
are also released under the **Apache License, Version 2.0**, in full compliance with the terms of
the original license. Under Section 4 of the Apache 2.0 license, derivative works may be distributed
provided that modified files carry prominent notices stating that you changed the files. Every
modified or new source file in this project carries such a notice in its header.

## Contributing

Contributions are welcome. Please open an issue to discuss proposed changes before submitting a pull
request. All contributions must be licensed under the Apache License, Version 2.0.
