# OpenAI Model Settings Implementation Plan

> **For agentic workers:** Implement the single task below in order. Use test-first development and keep the SPEC authoritative.

**Goal:** Let the user configure an OpenAI-compatible endpoint and persist its API key only as Android-Keystore-encrypted, no-backup app data.

**Architecture:** A small Android model owns URL validation and edit semantics. A private encrypted-file store uses an injected cipher for JVM-testable persistence behavior and Android Keystore AES-GCM in production. Compose adds a separate configuration dialog reachable from reader settings.

**Tech Stack:** Kotlin, Jetpack Compose, Android Keystore, Java AES/GCM, Android private no-backup storage, JUnit, Android instrumentation tests.

**Spec:** `android/docs/spec/features/openai-model-settings.md`.

## Global Constraints

- Keep Android-only SPECs and plans under `android/docs/spec/`.
- Never embed an API key in source, assets, test fixtures, or project data.
- Store the encrypted configuration in `Context.noBackupFilesDir` and use AES-GCM with the encryption key in Android Keystore.
- Allow cleartext HTTP only for `localhost`, `127.0.0.1`, and `10.0.2.2` in the debug Network Security Config; release builds require HTTPS and keep platform cleartext traffic disabled.
- Preserve debug defaults `http://127.0.0.1:3457/v1` and `cline-free/mimo-v2.6-flash`; release defaults to a blank base URL and the same model; do not supply a default key.
- Add a matching `CHANGELOG.md` Unreleased entry to the feature commit that is pushed.

## Review Focus

- Invalid endpoint edits must not replace a previously valid configuration; test the failed-save preservation path.
- Blank key entry and explicit key clearing must be distinguishable; test both with a previously saved key.
- Saved keys must not appear as plaintext in the encrypted file; test the stored bytes.
- The editor must not reveal a saved key when reopened; test the configured-state indicator and blank input behavior.
- Device-local loopback differs from computer loopback; verify localized UI help names LAN/ADB forwarding and emulator host options.
- Release builds must not inherit the debug-only cleartext allowance; inspect both merged manifests and add a runtime policy test that rejects arbitrary debug HTTP hosts.

---

### Task 1: Persist and edit the OpenAI-compatible model configuration

**Files:**
- Create: `android/app/src/main/java/io/pagenote/android/AgentModelSettings.kt`
- Create: `android/app/src/main/java/io/pagenote/android/AgentModelSettingsStore.kt`
- Create: `android/app/src/main/java/io/pagenote/android/AgentModelSettingsDialog.kt`
- Modify: `android/app/src/main/java/io/pagenote/android/MainActivity.kt`
- Modify: `android/app/src/main/java/io/pagenote/android/ReaderSettingsDialog.kt`
- Create: `android/app/src/debug/AndroidManifest.xml`
- Create: `android/app/src/debug/res/xml/network_security_config.xml`
- Modify: `android/app/src/main/res/values/strings.xml`
- Modify: `android/app/src/main/res/values-zh-rCN/strings.xml`
- Test: `android/app/src/test/java/io/pagenote/android/AgentModelSettingsTest.kt`
- Test: `android/app/src/androidTest/java/io/pagenote/android/AgentModelSettingsStoreTest.kt`

**Interfaces:**
- `AgentModelSettingsDraft(baseUrl: String, model: String)` stores editable non-secret fields.
- `AgentKeyChange` has `Keep`, `Replace(value: String)`, and `Clear` variants.
- `AgentModelSettingsStore.loadForEditing(): AgentModelSettingsView` returns URL, model, and `hasApiKey`, never the secret.
- `AgentModelSettingsStore.resolveEndpoint(): ModelEndpoint` decrypts only for execution and delegates missing-field behavior to `ModelConfig.resolve`.
- `AgentModelSettingsStore.save(draft: AgentModelSettingsDraft, keyChange: AgentKeyChange)` validates before atomically replacing encrypted configuration.
- An `AgentSecretCipher` boundary allows deterministic JVM tests; production uses Android Keystore AES-GCM.

- [x] **Step 1: Write JVM tests** for defaults, debug-vs-release URL policy, remote HTTP rejection, keep/replace/clear semantics, invalid-save preservation, and encrypted file round-trip with a test cipher.
- [x] **Step 2: Run JVM tests and verify they fail** because the settings model/store contracts do not exist.
- [x] **Step 3: Implement the settings model, validation, cipher abstraction, and atomic encrypted store.** Production construction uses `Context.noBackupFilesDir` and an Android Keystore AES-GCM cipher; debug-only manifest merging opts into cleartext for local relay development.
- [x] **Step 4: Run focused and full app JVM tests** and verify defaults, edits, conflicts, and clear behavior pass.
- [x] **Step 5: Add Android instrumentation tests** for local-only cleartext policy, Keystore round-trip, and absence of plaintext in the persisted file; compile them even when device execution is unavailable.
- [x] **Step 6: Add localized configuration dialog and reader-settings entry.** Use password transformation, blank-on-reopen behavior, explicit clear control, load/save error states, and loopback help copy.
- [x] **Step 7: Run `:app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:processReleaseMainManifest`**; inspect merged debug/release manifests and debug network config to confirm only local dev hosts permit HTTP.
- [x] **Step 8: Commit the feature with its changelog entry and push only to `fork/codex/android-port`.**
