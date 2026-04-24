# Signal Protocol scaffold

This package is intentionally fail-closed.

It defines the boundary for a future `org.signal:libsignal-android` integration without
shipping fake E2E cryptography. The current production path must keep using the existing
`CryptoEngine` until all required Signal stores and server-side pre-key flows are implemented.

Required before enabling `BuildConfig.ENABLE_SIGNAL_PROTOCOL`:

1. Durable identity key store.
2. Durable signed pre-key and one-time pre-key stores.
3. Durable session store.
4. Server endpoints for pre-key publishing, fetching, and rotation.
5. Message migration plan for existing AES-GCM payloads.
6. Test vectors and multi-device compatibility tests.

Do not replace this package with placeholder encryption.
