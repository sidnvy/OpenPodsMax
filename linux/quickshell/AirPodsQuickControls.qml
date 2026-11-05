// THESIS: headphone controls belong beside the desktop's other quick toggles.
// OWN-WORLD: inherit ii's Material theme, typography, icons and interaction widgets.
// STORY: identify headphones, see current sound, expand for less frequent policies.
// FIRST VIEWPORT: one compact inline header; no new window or navigation.
// FORM: observed state only, one headphone battery, four listening modes.
// FINISH: actual readback, bounded pending state, keyboard access and honest errors.
import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import Quickshell
import Quickshell.Io
import qs.services
import qs.modules.common
import qs.modules.common.widgets

ColumnLayout {
    id: root
    IpcHandler {
        target: "openpodsmax"
        function expand(): void { root.expanded = true; }
        function collapse(): void { root.expanded = false; }
        function focusControls(): void {
            root.expanded = true;
            headerButton.forceActiveFocus(Qt.TabFocusReason);
        }
    }
    property real controlsMaxHeight: 400
    property bool expanded: false
    readonly property var device: OpenPodsMax.state
    readonly property var modes: ["anc", "transparency", "adaptive", "off"]
    readonly property var modeLabels: [Translation.tr("Noise cancellation"), Translation.tr("Transparency"), Translation.tr("Adaptive"), Translation.tr("Off")]
    readonly property string sourceLabel: {
        switch (device.AudioSource) {
            case "computer": return Translation.tr("Playing on this computer");
            case "other-device": return Translation.tr("Playing on another device");
            case "call": return Translation.tr("Call in progress");
            case "idle": return Translation.tr("No audio playing");
            default: return Translation.tr("Audio source unavailable");
        }
    }
    readonly property int battery: device.BatteryLeft >= 0 ? device.BatteryLeft : (device.BatteryRight ?? -1)
    visible: OpenPodsMax.connected
    spacing: 8
    Layout.minimumWidth: 0
    onVisibleChanged: if (!visible) expanded = false

    RippleButton {
        id: headerButton
        Layout.fillWidth: true
        implicitHeight: 68
        buttonRadius: Appearance.rounding.small
        colBackground: Appearance.colors.colLayer1
        Rectangle {
            parent: headerButton.background
            anchors.fill: parent
            radius: headerButton.buttonRadius
            color: "transparent"
            border.width: headerButton.activeFocus ? 2 : 0
            border.color: Appearance.colors.colPrimary
            z: 10
        }
        Accessible.name: (root.device.DisplayName || "AirPods Max") + ", " + root.sourceLabel
        Accessible.description: root.expanded ? Translation.tr("Collapse headphone controls") : Translation.tr("Expand headphone controls")
        onClicked: root.expanded = !root.expanded
        contentItem: RowLayout {
            spacing: 10
            Image {
                Layout.preferredWidth: 44
                Layout.preferredHeight: 50
                source: Quickshell.shellPath("assets/openpodsmax/headphones.png")
                fillMode: Image.PreserveAspectFit
                smooth: true
            }
            ColumnLayout {
                Layout.fillWidth: true
                spacing: 2
                StyledText {
                    Layout.fillWidth: true
                    text: root.device.DisplayName || root.device.DeviceName || "AirPods Max"
                    font.bold: true
                    elide: Text.ElideRight
                }
                StyledText {
                    Layout.fillWidth: true
                    text: (root.device.NoiseControlKnown ? root.modeLabels[root.modes.indexOf(root.device.NoiseControlMode)] + " · " : "") + root.sourceLabel
                    color: Appearance.colors.colSubtext
                    font.pixelSize: Appearance.font.pixelSize.small
                    elide: Text.ElideRight
                }
            }
            StyledText {
                text: root.battery < 0 ? "—" : root.battery + "%"
                Accessible.name: root.battery < 0 ? Translation.tr("Battery unavailable") : Translation.tr("Battery") + " " + text
            }
            MaterialSymbol {
                text: root.expanded ? "expand_less" : "expand_more"
                iconSize: 22
            }
        }
    }
    ScrollView {
        id: expandedScroll
        visible: root.expanded
        Layout.fillWidth: true
        Layout.minimumWidth: 0
        Layout.maximumWidth: root.width
        leftPadding: 12
        rightPadding: 12
        topPadding: 4
        bottomPadding: 8
        Layout.preferredHeight: Math.min(expandedContent.implicitHeight + topPadding + bottomPadding, root.controlsMaxHeight)
        contentWidth: availableWidth
        clip: true
        ScrollBar.horizontal.policy: ScrollBar.AlwaysOff
        ColumnLayout {
        id: expandedContent
        width: Math.max(0, expandedScroll.availableWidth)
        spacing: 8
        StyledText {
            text: Translation.tr("Listening mode")
            color: Appearance.colors.colSubtext
            font.pixelSize: Appearance.font.pixelSize.small
        }
        GridLayout {
            Layout.fillWidth: true
            Layout.minimumWidth: 0
            columns: expandedScroll.availableWidth < 320 ? 1 : 2
            uniformCellWidths: true
            columnSpacing: 6
            rowSpacing: 6
            Repeater {
                model: root.modes
                delegate: RippleButton {
                    id: modeButton
                    required property string modelData
                    required property int index
                    Layout.fillWidth: true
                    Layout.minimumWidth: 0
                    Layout.preferredWidth: 1
                    implicitHeight: 36
                    Rectangle {
                        parent: modeButton.background
                        anchors.fill: parent
                        anchors.margins: 2
                        radius: modeButton.buttonRadius
                        color: "transparent"
                        border.width: modeButton.activeFocus ? 2 : 0
                        border.color: modeButton.toggled ? Appearance.colors.colOnPrimary : Appearance.colors.colPrimary
                        z: 10
                    }
                    visible: modelData !== "adaptive" || root.device.SupportsAdaptive === true
                    enabled: !OpenPodsMax.busy && root.device.SupportsANC === true
                    toggled: root.device.NoiseControlKnown === true && root.device.NoiseControlMode === modelData
                    Accessible.name: root.modeLabels[index]
                    Accessible.role: Accessible.RadioButton
                    Accessible.checkable: true
                    Accessible.checked: toggled
                    onClicked: OpenPodsMax.request("noise", modelData, "NoiseControlMode", modelData)
                    contentItem: StyledText {
                        text: root.modeLabels[parent.index]
                        horizontalAlignment: Text.AlignHCenter
                        verticalAlignment: Text.AlignVCenter
                        color: parent.toggled ? Appearance.colors.colOnPrimary : Appearance.colors.colOnSurface
                        font.pixelSize: Appearance.font.pixelSize.small
                        elide: Text.ElideRight
                    }
                }
            }
        }
        RowLayout {
            Layout.fillWidth: true
            StyledText { Layout.fillWidth: true; Layout.minimumWidth: 0; text: Translation.tr("Conversation awareness"); wrapMode: Text.WordWrap }
            StyledSwitch {
                checkable: false
                checked: root.device.ConversationKnown === true && root.device.ConversationalAwareness === true
                enabled: !OpenPodsMax.busy && root.device.SupportsAdaptive === true && root.device.ConversationKnown === true
                Accessible.name: Translation.tr("Conversation awareness")
                onClicked: OpenPodsMax.request("conversation", !root.device.ConversationalAwareness, "ConversationalAwareness", !root.device.ConversationalAwareness)
            }
        }
        RowLayout {
            Layout.fillWidth: true
            StyledText { text: Translation.tr("Pause when removed"); Layout.fillWidth: true; Layout.minimumWidth: 0; wrapMode: Text.WordWrap }
            StyledComboBox {
                Layout.fillWidth: false
                Layout.preferredWidth: Math.min(154, expandedScroll.availableWidth * 0.46)
                implicitHeight: 34
                model: [Translation.tr("Off"), Translation.tr("Either earcup"), Translation.tr("Both earcups")]
                currentIndex: root.device.EarPauseMode ?? 0
                enabled: !OpenPodsMax.busy
                Accessible.name: Translation.tr("Pause when removed")
                onActivated: index => OpenPodsMax.request("ear-pause", index, "EarPauseMode", index)
            }
        }
        RowLayout {
            Layout.fillWidth: true
            StyledText { Layout.fillWidth: true; Layout.minimumWidth: 0; text: Translation.tr("Automatic switching"); wrapMode: Text.WordWrap }
            StyledSwitch {
                checkable: false
                checked: root.device.HandoffEnabled === true
                enabled: !OpenPodsMax.busy
                Accessible.name: Translation.tr("Automatic switching")
                onClicked: OpenPodsMax.request("handoff", !root.device.HandoffEnabled, "HandoffEnabled", !root.device.HandoffEnabled)
            }
        }
        DialogButton {
            Layout.fillWidth: true
            buttonText: Translation.tr("Use on this computer")
            enabled: !OpenPodsMax.busy && root.device.HandoffEnabled === true && root.device.AudioSource !== "call" && root.device.AudioSource !== "computer"
            onClicked: OpenPodsMax.request("take", null, "AudioSource", "computer")
        }
        StyledText {
            Layout.fillWidth: true
            visible: OpenPodsMax.busy || OpenPodsMax.error !== ""
            text: OpenPodsMax.error || Translation.tr("Waiting for headphones…")
            color: OpenPodsMax.error ? Appearance.colors.colError : Appearance.colors.colSubtext
            wrapMode: Text.Wrap
            font.pixelSize: Appearance.font.pixelSize.small
            Accessible.role: Accessible.AlertMessage
        }
    }
    }
}
