# Strata Swing

The Java2D/Swing adapter for [Strata](../Strata). It shows Strata screens in any Swing application and renders them to images without a display, which makes it the adapter for tools, tests and the [gallery](../Strata-Gallery).

| Class | Role |
|---|---|
| `StrataPanel` | A `JPanel` that hosts a screen stack and forwards mouse, keyboard and focus input |
| `SwingSnapshot` | Renders a screen to a `BufferedImage`, optionally after scripted input |
| `SwingPlatform`, `SwingTextMetrics` | Text measurement, clipboard and clock for Swing |
| `SwingVisuals`, `SpriteResolver` | Images as visuals, and where sprites load from (`assets/<namespace>/sprites/<path>.png` by default) |

Apps depend on `strata-canvas-swing`, which brings `strata-api` and `strata-spi` with it and keeps the implementation, `strata-core`, out of their compile classpath. The adapter passes Strata's SPI contract tests.

The adapter builds against the Strata checkout beside it at `../Strata`, including Strata's convention plugins, so check the two out side by side. Nothing is published yet. Building needs JDK 25.

```bash
./gradlew build
```

## License

Strata Swing is released under the Apache License, Version 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
