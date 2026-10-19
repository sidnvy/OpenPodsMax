# Security and privacy

Never post Bluetooth addresses, identity/encryption keys, full bugreports, notification contents, signing files or tokens in an issue. Use redacted, narrowly scoped logs. The app does not need a cloud credential.

For a suspected vulnerability, use private vulnerability reporting if the repository owner has enabled it. Otherwise request a private contact channel without posting exploit details or secrets publicly. No response SLA is promised.

Private Android Bluetooth APIs and OEM Settings formats may change. The companion validates its explicitly selected bond and never claims vendor certification. Metadata helpers run with ADB shell authority and must be used only on an approved device with reviewed backups and rollback limits.

CI uses read-only repository permissions and does not receive signing keys. Automated secret scanning reduces risk but does not establish absence of all possible secrets.
