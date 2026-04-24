# Signal Protocol integration status

This client now contains the real Signal Protocol integration layer for the legacy Android Signal Protocol library (`org.whispersystems:signal-protocol-android:2.8.1`).

## Implemented

- Durable local `SignalProtocolStore` backed by private app `SharedPreferences`.
- Persistent identity key pair and registration id.
- Persistent one-time pre-key, signed pre-key, session and trusted identity records.
- Real bootstrap key material generation for `/auth/bootstrap`.
- Real one-time pre-key refill material.
- Real signed pre-key rotation material.
- Real session setup from server `/keys/bundle/{user_id}`.
- Real Signal encrypt/decrypt primitives through `SessionBuilder` and `SessionCipher`.

## Still required before switching the main message path

The server message schema currently exposes `message_type = text/file/service` and `encryption_mode = signal/signal_plus_shared_secret`, but a Signal ciphertext also needs the Signal message subtype:

- `PREKEY_TYPE`
- `WHISPER_TYPE`

Without this field the receiver cannot reliably choose `PreKeySignalMessage` or `SignalMessage` during decrypt.

Recommended backend addition:

```json
{
  "signal_message_type": "prekey|signal"
}
```

The client should switch `MessageRepository` from the current compatibility encryption to `SignalMessageCryptoEngine` only after that field is accepted and returned by the backend.

## Dependency

Add the dependency to the Android app module:

```kotlin
implementation("org.whispersystems:signal-protocol-android:2.8.1")
```

The artifact is old and GPL-3.0 licensed. Validate licensing before a commercial release.
