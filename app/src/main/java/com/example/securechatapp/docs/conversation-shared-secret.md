# Conversation shared-secret encryption

This layer is per-conversation only.

## Client behavior

- The user enables it from the settings of a single conversation.
- The raw token is never sent to the backend.
- The client derives an AES-256 key from `token + conversation_uuid` with PBKDF2-HMAC-SHA256.
- The backend receives only `shared_secret_fingerprint`.
- Outgoing payloads for that conversation are wrapped as `shared_secret_v1`.
- If another participant has not entered the same token for the same `conversation_uuid`, the client shows a locked-message placeholder instead of plaintext.

## Backend metadata

The backend may store:

- `shared_secret_enabled`
- `shared_secret_fingerprint`
- `shared_secret_updated_at`

The backend must not store the token or derived key.

## Payload format

`ss1:<fingerprint>:<nonce_base64>:<ciphertext_base64>`

AAD:

`securechat.shared-secret.message.v1:<conversation_uuid>:<fingerprint>`

This is an additional encryption layer and does not replace the main E2E roadmap.
