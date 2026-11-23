# Sideload signing key

`liquidmihon.jks` signs LiquidMihon's sideloaded builds (store and key password: `liquidmihon`,
alias: `liquidmihon`). `app/build.gradle.kts` uses it whenever there is no `keystore.properties`
and no release signing set up in the environment, so every build made from this repo is signed
the same way and installs over the previous one.

The key is public, so anyone could sign an APK that Android would accept as an update. Only
install LiquidMihon APKs from this repository's `apk/` folder.

SHA-256 certificate fingerprint:
`F0:5E:12:66:34:38:32:AA:29:0F:2A:EB:60:80:D3:1E:27:6C:B4:03:46:45:3E:08:C2:7C:08:51:36:C5:D0:F0`
