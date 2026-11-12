#!/system/bin/sh
# Runtime properties and reversible legacy config views. No APK/APEX write, module, pairing or reboot.
set -eu
vendor_key=bluetooth.device_id.vendor_id
source_key=bluetooth.device_id.vendor_id_source
boot=$(cat /proc/sys/kernel/random/boot_id)
state() { dumpsys bluetooth_manager | sed -n 's/^  State: *//p' | head -n 1; }
normal() { dumpsys audio | grep -q 'Actual mode = MODE_NORMAL'; }
backend() {
    # Inspect the libraries actually loaded by Android's Bluetooth process.
    for p in $(pidof com.android.bluetooth com.google.android.bluetooth 2>/dev/null || true); do
        for lib in $(awk '/bluetooth.*[.]so/ {print $NF}' "/proc/$p/maps" | sort -u); do
            case "$lib" in /apex/*|/system/*|/vendor/*|/product/*|/system_ext/*) ;; *) continue;; esac
            if grep -aFq "$vendor_key" "$lib" && grep -aFq "$source_key" "$lib"; then return 0; fi
        done
    done
    return 1
}
valid() {
    # Empty means Android's original unset/default property, never a guessed vendor.
    case "$1" in '') return 0;; 0x*|0X*)
        v=${1#??}; [ -n "$v" ] && [ ${#v} -le 4 ] || return 1
        case "$v" in *[!0-9a-fA-F]*) return 1;; esac;;
      *) case "$1" in *[!0-9]*) return 1;; esac
         [ ${#1} -le 5 ] && [ "$1" -le 65535 ] || return 1;;
    esac
}
[ "$(id -u)" = 0 ] || exit 10
case "${1:-}" in probe) [ "$#" = 2 ] || exit 11; lock=$2;;
    change|enable-config|disable-config) [ "$#" = 7 ] || exit 11; lock=$7;; *) exit 11;; esac
case "$lock" in /*/apple-identity.lock) ;; *) exit 11;; esac
busy() {
    [ -d "$lock" ] || return 1
    [ -f "$lock/owner" ] || return 0
    read -r owner_boot owner_pid < "$lock/owner" || return 0
    case "$owner_pid" in ''|*[!0-9]*) return 0;; esac
    [ "$owner_boot" = "$boot" ] && kill -0 "$owner_pid" 2>/dev/null
}
if [ "$1" = probe ]; then
    capable=0; if backend; then capable=1; fi
    if command -v cfg_find >/dev/null && cfg_find; then capable=2; fi
    config_pending=0; config_active=0
    if command -v cfg_pending >/dev/null && cfg_pending; then
        config_pending=1
        if cfg_verify enabled; then config_active=1; fi
    fi
    pending=0; if busy; then pending=1; fi
    printf 'BOOT=%s\nVENDOR=%s\nSOURCE=%s\nBACKEND=%s\nSTATE=%s\nBUSY=%s\nCONFIG_ACTIVE=%s\nCONFIG_PENDING=%s\n' \
        "$boot" "$(getprop "$vendor_key")" "$(getprop "$source_key")" "$capable" "$(state)" "$pending" "$config_active" "$config_pending"
    exit 0
fi
expected_vendor=$2; expected_source=$3; desired_vendor=$4; desired_source=$5
for value in "$expected_vendor" "$expected_source" "$desired_vendor" "$desired_source"; do valid "$value" || exit 11; done
[ "$boot" = "$6" ] || exit 18
config_mode=none
case "$1" in enable-config) config_mode=enabled;command -v cfg_apply >/dev/null || exit 19;;
 disable-config) config_mode=restored;command -v cfg_restore >/dev/null || exit 19;;
 *) backend || exit 19;; esac
if ! mkdir "$lock" 2>/dev/null; then
    busy && exit 21
    # Only this app's known metadata in an abandoned lock; never recursive removal.
    rm -f "$lock/owner" && rmdir "$lock" && mkdir "$lock" || exit 21
fi
if ! printf '%s %s\n' "$boot" "$$" > "$lock/owner"; then rmdir "$lock" 2>/dev/null || true; exit 21; fi
release() { rm -f "$lock/owner"; rmdir "$lock" 2>/dev/null || true; }
trap release EXIT
[ "$(getprop "$vendor_key")" = "$expected_vendor" ] && [ "$(getprop "$source_key")" = "$expected_source" ] || exit 20
[ "$(state)" = ON ] || exit 14
normal || exit 13
if [ "$config_mode" = none ] && [ "$expected_vendor" = "$desired_vendor" ] && [ "$expected_source" = "$desired_source" ]; then
    echo RESULT=UNCHANGED; exit 0
fi
cycling=0
recover() {
    trap - EXIT HUP INT TERM
    config_recovered=0
    if [ "$config_mode" = enabled ]; then
        if cfg_restore; then config_recovered=1; else echo CONFIG_ROLLBACK=CONFLICT; fi
    fi
    # Never overwrite an unexpected third-party change during recovery.
    v=$(getprop "$vendor_key"); s=$(getprop "$source_key")
    if { [ "$v" = "$desired_vendor" ] || [ "$v" = "$expected_vendor" ]; } && \
       { [ "$s" = "$desired_source" ] || [ "$s" = "$expected_source" ]; }; then
        setprop "$vendor_key" "$expected_vendor" && setprop "$source_key" "$expected_source" && echo ROLLBACK=RESTORED
    else echo ROLLBACK=CONFLICT; fi
    radio_recovered=1
    if [ "$cycling" = 1 ]; then
        # If native init already loaded the temporary DID, enable alone does not reload it.
        if [ "$config_mode" = enabled ] && [ "$config_recovered" = 1 ] && [ "$(state)" = ON ]; then
            if normal; then
                cmd bluetooth_manager disable >/dev/null 2>&1 || radio_recovered=0
                for attempt in 1 2 3 4 5 6 7 8; do
                    case "$(state)" in OFF|BLE_ON) break;; esac
                    sleep 1
                done
                case "$(state)" in OFF|BLE_ON) ;; *) radio_recovered=0;; esac
            else radio_recovered=0; fi
        fi
        cmd bluetooth_manager enable >/dev/null 2>&1 || radio_recovered=0
        for attempt in 1 2 3 4 5 6 7 8; do [ "$(state)" != ON ] || break; sleep 1; done
        [ "$(state)" = ON ] || radio_recovered=0
    fi
    if [ "$config_mode" = enabled ] && [ "$config_recovered" = 1 ]; then
        if [ "$radio_recovered" = 1 ] && { ! cfg_pending || cfg_verify restored; }; then
            cfg_clean; echo CONFIG_ROLLBACK=RESTORED
        else echo CONFIG_ROLLBACK=PENDING; fi
    fi
    release
}
trap recover EXIT
trap 'exit 30' HUP INT TERM
if [ "$config_mode" = enabled ]; then cfg_apply || exit 22;
elif [ "$config_mode" = restored ]; then cfg_restore || exit 22; fi
setprop "$vendor_key" "$desired_vendor"
setprop "$source_key" "$desired_source"
normal || exit 13
cycling=1
cmd bluetooth_manager disable >/dev/null || exit 15
for attempt in 1 2 3 4 5 6 7 8 9 10; do
    case "$(state)" in OFF|BLE_ON) break;; esac
    sleep 1
done
case "$(state)" in OFF|BLE_ON) ;; *) exit 15;; esac
sleep 2
# Some stacks reset properties during shutdown; recheck instead of overwriting it.
[ "$(getprop "$vendor_key")" = "$desired_vendor" ] && [ "$(getprop "$source_key")" = "$desired_source" ] || exit 20
cmd bluetooth_manager enable >/dev/null || exit 16
for attempt in 1 2 3 4 5 6 7 8 9 10 11 12; do
    [ "$(state)" != ON ] || break
    sleep 1
done
[ "$(state)" = ON ] || exit 16
[ "$(getprop "$vendor_key")" = "$desired_vendor" ] && [ "$(getprop "$source_key")" = "$desired_source" ] || exit 17
if [ "$config_mode" != none ]; then cfg_verify "$config_mode" || exit 22; fi
if [ "$config_mode" = restored ]; then cfg_clean; fi
release; trap - EXIT HUP INT TERM
echo RESULT=CHANGED
