# Legacy bt_did.conf backend. Source this before apple_identity.sh.
# Original APK/APEX bytes remain untouched; only owned temporary bind mounts are removed.
cfg_dir() { printf '%s/apple-identity-config' "${lock%/*}"; }
cfg_hash() { sha256sum "$1" | awk '{print $1}'; }
cfg_inode() { stat -c '%d:%i' "$1"; }
cfg_ns() { readlink "/proc/$1/ns/mnt"; }
cfg_pids() { pidof zygote64 zygote com.android.bluetooth com.google.android.bluetooth 2>/dev/null || true; }
cfg_in() { pid=$1; shift; nsenter -t "$pid" -m -- "$@"; }
cfg_find() {
    command -v nsenter >/dev/null || return 1
    for config_target in /apex/com.android.bt/etc/bluetooth/bt_did.conf /apex/com.android.btservices/etc/bluetooth/bt_did.conf /system/etc/bluetooth/bt_did.conf; do
        [ -f "$config_target" ] || continue
        primary=$(awk '/^\[/{section=$0} section=="[DID1]" && /^[[:space:]]*primaryRecord[[:space:]]*=/ {gsub(/[[:space:]]/,"",$0);print $0}' "$config_target")
        [ "$primary" = primaryRecord=true ] || continue
        [ -n "$(pidof zygote64 zygote 2>/dev/null || true)" ] || continue
        return 0
    done
    return 1
}
cfg_pending() {
    c=$(cfg_dir)
    [ -f "$c/boot" ] && [ "$(cat "$c/boot")" = "$boot" ] && [ -f "$c/namespaces" ]
}
cfg_load() {
    c=$(cfg_dir)
    [ -f "$c/boot" ] && [ "$(cat "$c/boot")" = "$boot" ] || return 1
    config_target=$(cat "$c/target")
    case "$config_target" in /apex/com.android.bt/etc/bluetooth/bt_did.conf|/apex/com.android.btservices/etc/bluetooth/bt_did.conf|/system/etc/bluetooth/bt_did.conf) ;; *) return 1;; esac
    [ "$(cfg_hash "$c/original")" = "$(cat "$c/original.sha")" ] || return 1
    [ "$(cfg_hash "$c/patched")" = "$(cat "$c/patched.sha")" ] || return 1
}
cfg_clean() {
    c=$(cfg_dir)
    for name in boot target original original.sha patched patched.sha namespaces; do rm -f "$c/$name"; done
    rmdir "$c" 2>/dev/null || true
}
cfg_prepare() {
    c=$(cfg_dir)
    if [ -d "$c" ]; then
        if cfg_pending; then cfg_load; return $?; fi
        # A stale old-boot private snapshot cannot describe live mounts in this boot.
        [ -f "$c/boot" ] && [ "$(cat "$c/boot")" != "$boot" ] || return 1
        cfg_clean; [ ! -d "$c" ] || return 1
    fi
    cfg_find || return 1
    mkdir "$c"; chmod 700 "$c"
    parent=$(pidof zygote64 zygote 2>/dev/null | awk '{print $1}')
    [ -n "$parent" ] || return 1
    cfg_in "$parent" cat "$config_target" > "$c/original"
    cfg_hash "$c/original" > "$c/original.sha"
    printf '%s\n' "$config_target" > "$c/target"
    awk '
        /^\[/ {if(section=="[DID1]" && !source)print "vendorIdSource = 0x0001";section=$0}
        section=="[DID1]" && /^[[:space:]]*vendorId[[:space:]]*=/ {$0="vendorId = 0x004C";vendor++}
        section=="[DID1]" && /^[[:space:]]*vendorIdSource[[:space:]]*=/ {$0="vendorIdSource = 0x0001";source++}
        {print}
        END {if(section=="[DID1]" && !source)print "vendorIdSource = 0x0001";if(vendor!=1||source>1)exit 1}
    ' "$c/original" > "$c/patched" || return 1
    chmod 644 "$c/patched"
    # The Bluetooth UID must be able to read the bound inode under its original label.
    label=$(cfg_in "$parent" ls -Z "$config_target" | awk '{print $1}')
    case "$label" in u:object_r:*:s0) ;; *) return 1;; esac
    chcon "$label" "$c/patched" || return 1
    cfg_hash "$c/patched" > "$c/patched.sha"
    : > "$c/namespaces"
    printf '%s\n' "$boot" > "$c/boot"
}
cfg_apply() {
    cfg_prepare || return 1
    c=$(cfg_dir); original_hash=$(cat "$c/original.sha"); patch_inode=$(cfg_inode "$c/patched")
    # Future Bluetooth processes inherit zygote mounts; also cover the current process.
    for pid in $(cfg_pids); do
        ns=$(cfg_ns "$pid") || return 1
        inode=$(cfg_in "$pid" stat -c '%d:%i' "$config_target") || return 1
        [ "$inode" != "$patch_inode" ] || continue
        current=$(cfg_in "$pid" sha256sum "$config_target" | awk '{print $1}')
        [ "$current" = "$original_hash" ] || return 1
        printf '%s %s\n' "$pid" "$ns" >> "$c/namespaces"
        cfg_in "$pid" mount --bind "$c/patched" "$config_target" || return 1
        [ "$(cfg_ns "$pid")" = "$ns" ] || return 1
        [ "$(cfg_in "$pid" stat -c '%d:%i' "$config_target")" = "$patch_inode" ] || return 1
    done
}
cfg_restore_pid() {
    pid=$1; expected_ns=$2
    current_ns=$(cfg_ns "$pid" 2>/dev/null) || return 0
    [ "$current_ns" = "$expected_ns" ] || return 0
    inode=$(cfg_in "$pid" stat -c '%d:%i' "$config_target") || return 1
    current=$(cfg_in "$pid" sha256sum "$config_target" | awk '{print $1}')
    if [ "$inode" = "$patch_inode" ]; then
        [ "$current" = "$patch_hash" ] || return 1
        cfg_in "$pid" umount "$config_target" || return 1
        current=$(cfg_in "$pid" sha256sum "$config_target" | awk '{print $1}')
    fi
    [ "$current" = "$original_hash" ]
}
cfg_restore() {
    cfg_pending || return 0
    cfg_load || return 1
    c=$(cfg_dir); original_hash=$(cat "$c/original.sha"); patch_hash=$(cat "$c/patched.sha");patch_inode=$(cfg_inode "$c/patched")
    failed=0
    while read -r pid ns; do
        case "$pid" in ''|*[!0-9]*) failed=1;continue;; esac
        cfg_restore_pid "$pid" "$ns" || failed=1
    done < "$c/namespaces"
    # A fresh Bluetooth process may have inherited a binding after the original journal entry.
    for pid in $(cfg_pids) $(pidof dev.airpodsnative.companion 2>/dev/null || true) $$; do
        ns=$(cfg_ns "$pid" 2>/dev/null) || continue
        cfg_restore_pid "$pid" "$ns" || failed=1
    done
    [ "$failed" = 0 ]
}
cfg_verify() {
    cfg_load || return 1
    c=$(cfg_dir)
    expected=$(cat "$c/patched.sha")
    [ "$1" != restored ] || expected=$(cat "$c/original.sha")
    pids=$(pidof com.android.bluetooth com.google.android.bluetooth 2>/dev/null || true)
    [ -n "$pids" ] || return 1
    for pid in $pids; do
        [ "$(cfg_in "$pid" sha256sum "$config_target" | awk '{print $1}')" = "$expected" ] || return 1
    done
}
