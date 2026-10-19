# OpenAI Model Settings

## Scope

Provide an Android-native settings surface for the OpenAI-compatible endpoint used by the on-device Agent. This is an Android-only feature and its documents remain under `android/docs/spec/`.

## Behavior

- The reader settings surface offers a separate AI model configuration action.
- A new debug installation starts with base URL `http://127.0.0.1:3457/v1`; a new release installation starts with an empty base URL because release builds require HTTPS. Both start with model `cline-free/mimo-v2.6-flash` and no API key. The model and URL are editable; the application never supplies a key.
- The base URL must be an absolute HTTP or HTTPS URL with a host, and must not contain user information, query, or fragment components. Invalid input is rejected before the existing configuration is replaced. Debug builds accept HTTP only for `localhost`, `127.0.0.1`, and emulator host alias `10.0.2.2`; release builds require HTTPS.
- The API key is encrypted with an AES-GCM key held by Android Keystore. The encrypted configuration is stored under `Context.noBackupFilesDir`; neither the plaintext key nor ciphertext is placed in a pagenote project or included in Android backup.
- Reopening the dialog must not reveal the saved key. A blank key edit preserves an existing key; a dedicated clear action removes it. Entering a replacement key replaces it.
- The editing view reports whether a key is configured without returning the secret. Agent execution may resolve the full endpoint into process memory; missing keys continue to use `ModelConfig`'s explicit missing-configuration failure.
- The UI explains that `127.0.0.1` addresses the Android device itself. Debug builds permit cleartext traffic only to `localhost`, `127.0.0.1`, and emulator host alias `10.0.2.2`; arbitrary remote HTTP endpoints are rejected. Release builds retain Android's default cleartext protection. To use a computer-hosted local relay on a physical device, the user can forward port 3457 with `adb reverse tcp:3457 tcp:3457`. See [Android Network Security Configuration](https://developer.android.com/privacy-and-security/security-config) and [ADB reverse port forwarding](https://developer.android.com/develop/ui/views/layout/webapps/access-local-server).
- Android Keystore or encrypted-file errors remain visible and do not silently discard an earlier valid configuration.

## Acceptance

- A new debug store returns the local relay/model defaults; a new release store returns a blank base URL and the model default. Both report no configured key.
- Valid URL/model values survive closing and reopening the settings surface.
- An entered key is encrypted at rest, cannot be found as plaintext in the stored file, and is available to the Agent only after decrypting into memory.
- Saving without entering a key preserves a previously configured key; explicit clear removes it; replacement stores only the new key.
- Invalid URLs leave the previous configuration unchanged and show a localized error.
- Debug builds may connect to the default local HTTP relay but reject HTTP to arbitrary hosts; the debug network config and runtime validation permit only `localhost`, `127.0.0.1`, and `10.0.2.2`. Release builds reject HTTP.
- A missing key produces the existing actionable `MissingModelConfigException`; no embedded credential or project-local key file is introduced.
- English and Chinese labels/help/error text are available from Android resources.

## Verification

- JVM tests cover defaults, local-only debug HTTP, release HTTPS policy, URL validation, replacement/keep/clear semantics, invalid-save preservation, and encrypted-store round trips with an injected test cipher.
- Android instrumentation tests exercise local-only cleartext network policy and the Android Keystore AES-GCM implementation, and assert the persisted bytes do not contain the entered key.
- `:app:testDebugUnitTest`, `:app:compileDebugAndroidTestKotlin`, and `:app:assembleDebug` pass. Device execution remains part of task 12.
