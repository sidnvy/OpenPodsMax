pragma Singleton
import QtQuick
import Quickshell
import Quickshell.Io

Singleton {
    id: root
    property var state: ({ Connected: false })
    readonly property bool connected: state.Connected === true && state.IsHeadphones === true
    readonly property string executable: Quickshell.env("HOME") + "/.local/bin/openpodsmaxctl"
    property string pendingProperty: ""
    property var pendingValue: null
    property string error: ""
    readonly property bool busy: pendingProperty !== "" || setter.running

    function confirmed(property, expected) {
        if (property === "NoiseControlMode" && state.NoiseControlKnown !== true) return false;
        if (property === "ConversationalAwareness" && state.ConversationKnown !== true) return false;
        return state[property] === expected;
    }
    function request(command, value, property, expected) {
        if (busy || !connected || confirmed(property, expected)) return;
        error = "";
        pendingProperty = property;
        pendingValue = expected;
        setter.command = [executable, command].concat(value === null ? [] : [String(value)]);
        expiry.restart();
        setter.running = true;
    }
    function accept(snapshot) {
        state = snapshot;
        if (!connected) {
            if (pendingProperty !== "") error = "Headphones disconnected. Reconnect to try again.";
            pendingProperty = "";
            expiry.stop();
        } else if (pendingProperty !== "" && confirmed(pendingProperty, pendingValue)) {
            pendingProperty = "";
            expiry.stop();
        }
    }
    Process {
        id: watcher
        command: [root.executable, "watch"]
        running: true
        stdout: SplitParser {
            onRead: data => {
                try { root.accept(JSON.parse(data)); }
                catch (error) { console.warn("OpenPodsMax: invalid status update"); }
            }
        }
        onExited: {
            root.accept({ Connected: false });
            reconnect.restart();
        }
    }
    Timer { id: reconnect; interval: 4000; onTriggered: watcher.running = true }
    Timer {
        id: expiry
        interval: 6000
        onTriggered: {
            root.pendingProperty = "";
            root.error = "No confirmation from headphones. Try again.";
        }
    }
    Process {
        id: setter
        stderr: StdioCollector { id: failure }
        onExited: (code, status) => {
            if (code !== 0) {
                root.error = failure.text.trim() || "Could not apply setting. Try again.";
                root.pendingProperty = "";
                expiry.stop();
            }
        }
    }
}
