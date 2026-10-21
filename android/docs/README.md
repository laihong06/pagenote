# Android Documentation

Android-specific requirements, design decisions, task tracking, and feature
specifications live under [`spec/`](spec/). Keep them out of the repository-level
`docs/`, which is reserved for documentation shared by the desktop project.

## Beta APK

Build the installable Android beta with `./gradlew :app:assembleBeta`. The beta
uses the separate application ID `io.pagenote.android.beta`, its own app data,
and a `pagenote Beta` launcher label. It requires Android 14 (API 34) or later.
The APK is signed with the local Android debug signing key for sideload testing;
future beta APKs must use the same key to install as updates. If that key is
unavailable or changes, uninstall the existing beta first. This is not a
Play-ready signing configuration.

- [Requirements](spec/requirements.md)
- [Design](spec/design.md)
- [Implementation tasks](spec/tasks.md)
- [Renderer library evaluation](spec/renderer-library-evaluation.md)
- [Mermaid layout regression plan](spec/mermaid-layout-regression-plan.md)
- [Reader session restoration plan](spec/reader-session-restoration-plan.md)
- [Feature specifications](spec/features/)
- [Data contracts](spec/contracts/)
