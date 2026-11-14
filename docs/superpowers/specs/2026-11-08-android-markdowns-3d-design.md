# Android Markdown S 3D Renderer Design

## Intent

Keep the existing Markdown S `genetic-lab` fence and its JSON state as the cross-platform source of truth while replacing Android's 2D chromosome projection with a real native 3D scene. Desktop and Android may use different renderers, but they must interpret the same `genes`, `angle`, and `paused` fields and preserve the same file on disk.

## Approaches

- Keep Canvas 2D: lowest cost, but it cannot meet the requested 3D parity.
- Embed the desktop Three.js page in Android WebView: visual reuse is possible, but it executes HTML/JavaScript and conflicts with the native Markdown S boundary and existing fallback behavior.
- Use SceneView 2.3.0 backed by Filament: a native Android View fits the current Kotlin/AGP generation and can render procedural geometry, handle orbit interaction and hit testing. This is the selected approach.

## Scope

- Replace only chapter 08's renderer; leave the fence parser, genetic algorithm, Markdown editor, and save path unchanged.
- Build a procedural double helix from shared gene state. Tapping a base pair toggles its gene, dragging rotates the model, and the pause control toggles automatic rotation.
- Treat rendering failure as a local renderer fallback. Keep Markdown source and other chapter interactions available.
- Do not claim Android/desktop visual identity or general `mds-three` JavaScript compatibility.

## Constraints and acceptance

- Pin SceneView 2.3.0 initially because it wraps Filament behind the existing Android View integration style and was built against Kotlin 2.0.21 / Android API 35. The dependency must be verified against this repository's AGP 8.7.3 build before retention.
- Dispose scene nodes, animation callbacks and renderer resources on detach/rebind. Do not persist generated geometry or renderer state in the Markdown file.
- Verify the same Markdown fixture drives the existing desktop-compatible algorithm state and Android 3D scene; toggling genes and angle updates only that fence via the existing state editor.
- Capture a real Android runtime screenshot and identify emulator/device, renderer backend and source commit. If the emulator cannot provide a working Filament surface, record that limitation rather than claiming visual acceptance.
