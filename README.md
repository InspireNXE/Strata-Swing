<p align="center"><img src=".github/assets/logo.svg" width="128" alt="Strata Swing logo"></p>

# Strata Swing

The Java2D/Swing adapter for [Strata](https://github.com/InspireNXE/Strata). It shows Strata screens in any Swing application and renders them to images without a display, which makes it the adapter for tools, tests and the [gallery](https://github.com/InspireNXE/Strata-Gallery).

```java
StrataPanel panel = new StrataPanel(new SwingPlatform(SwingTextMetrics.defaults()), Themes.flat(), 2f);
panel.setDefault();
frame.setContentPane(panel);
Strata.screens().open(new SettingsScreen());
```

The last argument of `StrataPanel` is the number of pixels per UI unit. `SwingSnapshot` renders a screen to a `BufferedImage`, optionally after scripted input. Sprites load from `assets/<namespace>/sprites/<path>.png` unless a `SpriteResolver` says otherwise.

Apps depend on `strata-swing`, which brings `strata-api` and `strata-spi` and keeps `strata-core` off the compile classpath. The adapter passes Strata's SPI contract tests.

## Building

Building needs JDK 25 and a Strata checkout beside this one at `../Strata`, for Strata's convention plugins in `build-logic`. The adapter builds against that checkout, or against the Strata version in `gradle.properties` from Maven Central when only `build-logic` is there.

```bash
./gradlew build
```

The [Swing platform page](https://inspirenxe.github.io/Strata/platforms/swing/) covers the rest.

## License

Strata Swing is released under the Apache License, Version 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
