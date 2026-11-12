# Signing keys

These are the **public AOSP test keys** for the `platform` certificate, from
`build/target/product/security/` in the Android source tree (`oreo-mr1-release`):

- `platform.pk8`, `platform.x509.pem`: as published by AOSP
- `platform.p12`: the same key and certificate in a PKCS#12 keystore for Gradle
  (store and key password `android`, alias `platform`)

Certificate SHA-256: `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`

They are not secret: they ship with every copy of AOSP. Firmwares built with them ("test-keys"
in the build fingerprint), like the inkPalm 5's, accept any app signed with them as a system
app. That is the whole point of this project, and also why such firmwares are not secure.
