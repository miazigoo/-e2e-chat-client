# Crypto status

## Messages

Current message payloads are encrypted on-device before they are sent to the server. The
active production path still uses the app `CryptoEngine` and must not be advertised as
Signal Protocol until durable Signal stores, message type persistence and migration are
completed.

## Attachments

Attachments are encrypted on the client before upload. The server receives only the encrypted
blob, encrypted file name and metadata needed to verify/download the blob.

Per attachment:
- a random 256-bit AES key is generated;
- a random 96-bit GCM nonce is generated;
- the blob is encrypted with AES-256-GCM;
- the encrypted blob SHA-256 is sent for integrity tracking;
- the file name is encrypted with a separate AES-256-GCM key/nonce;
- keys/nonces are carried only inside the encrypted message payload.

## Additional key layer

The attachment layer already uses an additional random content key per file. Do not add
passphrase-based shared-key encryption as a substitute for Signal: it creates unsafe manual
key distribution and does not provide forward secrecy.
