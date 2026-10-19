// Apache-2.0. A Qt Quick page with glass over its own scrolling content: a light card and a dark bar.
import QtQuick
import LiquidGlass

Window {
    id: window
    width: 412
    height: 860
    visible: true
    color: "#f6f3f8"
    title: "LiquidGlass for Qt Quick"

    readonly property var swatches: ["#e35d6a", "#4c8df6", "#3cc47c", "#f2b33d", "#9b6cf6"]

    // The backdrop: an ordinary ListView. The glass sits beside it in the window, over it.
    ListView {
        id: list
        objectName: "list"
        anchors.fill: parent
        topMargin: 190
        bottomMargin: 140
        spacing: 14
        model: 24
        header: Text {
            x: 20; height: 60
            text: "Your Qt Quick page"; font.pixelSize: 32; font.bold: true; color: "black"
        }
        delegate: Item {
            width: ListView.view.width; height: 56
            Rectangle { x: 20; width: 56; height: 56; radius: 14; color: window.swatches[index % 5] }
            Column {
                x: 90; anchors.verticalCenter: parent.verticalCenter
                Text { text: "Row " + (index + 1) + ": a Qt Quick item"; font.pixelSize: 18; font.bold: true; color: "black" }
                Text { text: "Scrolls under the glass, refracted live"; font.pixelSize: 14; color: "#3c3c43" }
            }
        }
    }

    GlassSurface {
        id: card
        objectName: "card"
        backdrop: list
        backdropColor: window.color   // the list's empty top margin shows the window
        x: 20; y: 56; width: parent.width - 40; height: 116
        radius: 28
        light: Qt.point(0, -1)
        Column {
            x: 22; y: 22
            Text { text: "Glass card"; font.pixelSize: 24; font.bold: true; color: "black" }
            Text { text: "Qt Quick text on LiquidGlass"; font.pixelSize: 16; color: "#3c3c43" }
        }
    }

    GlassSurface {
        id: bar
        objectName: "bar"
        backdrop: list
        backdropColor: window.color
        x: 20; y: parent.height - 72 - 28; width: parent.width - 40; height: 72
        radius: 36
        dark: true
        Row {
            anchors.fill: parent
            Repeater {
                model: ["Home", "Search", "Library", "Profile"]
                Text {
                    width: bar.width / 4; height: bar.height
                    horizontalAlignment: Text.AlignHCenter; verticalAlignment: Text.AlignVCenter
                    text: modelData; font.pixelSize: 15; font.bold: index === 0
                    color: index === 0 ? "#369cfe" : "white"
                }
            }
        }
    }
}
