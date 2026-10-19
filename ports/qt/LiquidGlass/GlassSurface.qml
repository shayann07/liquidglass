// Apache-2.0. A rounded piece of Liquid Glass with children on top. It refracts `backdrop` live, every
// frame: a ShaderEffectSource renders the part of the backdrop under the glass, and GlassEffect runs the
// production material over it. Put it over the backdrop (siblings in the same parent, say), never inside it.
import QtQuick
import "material.js" as Material

Item {
    id: root

    /** The item the glass refracts: the page, a ListView, an Image. Keep the glass outside it. */
    property Item backdrop
    /** What the glass sees where the backdrop is transparent (a ListView's empty margins, say): the
        window's colour, usually. The material needs an opaque backdrop. */
    property color backdropColor: "white"
    /** Corner radius, logical pixels. */
    property real radius: 20
    /** The dark material. */
    property bool dark: false
    /** 0 to 100. */
    property real tintAmount: 50
    /** Toward the light (y up is negative); Qt.point(0, -1) is iOS 27's measured overhead light. */
    property point light: Qt.point(-0.35, -0.94)
    /** [r, g, b, a] in sRGB 0..1 replacing the tint colour and opacity, or null. */
    property var tint: null
    /** 0 to 1: glass materializes in and out rather than fading. */
    property real materialize: 1
    /** 0 to 1, the accessibility settings. */
    property real reducedTransparency: 0
    property real increasedContrast: 0
    /** Re-map the glass onto the backdrop every frame, for glass or a backdrop that moves without
        changing its geometry properties (inside an animated parent, say). A backdrop that scrolls its
        own content, like a ListView, needs none of this: the glass stays where it is over it. */
    property bool followsMovement: false
    /** Children are drawn on top of the glass. */
    default property alias content: contentItem.data
    /** The shader's status (ShaderEffect.Compiled 0, Uncompiled 1, Error 2) and its log, for diagnostics.
        An int, so hosts that cannot convert the enum (PySide) can read it. */
    readonly property int shaderStatus: effect.status
    readonly property string shaderLog: effect.log

    // Two device pixels around the glass for the outer contour, as in every port.
    readonly property real dpr: Screen.devicePixelRatio > 0 ? Screen.devicePixelRatio : 1
    readonly property real padUnits: 2 / dpr

    function updateSourceRect() {
        if (!backdrop) return
        const r = backdrop.mapFromItem(root, -padUnits, -padUnits, width + 2 * padUnits, height + 2 * padUnits)
        if (r.x !== backdropTexture.sourceRect.x || r.y !== backdropTexture.sourceRect.y ||
                r.width !== backdropTexture.sourceRect.width || r.height !== backdropTexture.sourceRect.height)
            backdropTexture.sourceRect = r
    }

    function updateUniforms() {
        if (width <= 0 || height <= 0) return
        const values = Material.uniforms({
            radius: radius, dark: dark, tintAmount: tintAmount, light: [light.x, light.y], tint: tint,
            materialize: materialize, reducedTransparency: reducedTransparency, increasedContrast: increasedContrast,
        }, width * dpr, height * dpr, dpr)
        values.uBase = [backdropColor.r, backdropColor.g, backdropColor.b]
        effect.setUniforms(values)
    }

    ShaderEffectSource {
        id: backdropTexture
        sourceItem: root.backdrop
        live: true
        hideSource: false
        visible: false
        smooth: true
        textureSize: Qt.size(Math.ceil(effect.width * root.dpr), Math.ceil(effect.height * root.dpr))
    }

    GlassEffect {
        id: effect
        x: -root.padUnits
        y: -root.padUnits
        width: root.width + 2 * root.padUnits
        height: root.height + 2 * root.padUnits
        source: backdropTexture
        field: backdropTexture   // the shape is analytic; the field is bound but unread
        uLayerSize: Qt.point(width * root.dpr, height * root.dpr)
    }

    Item {
        id: contentItem
        anchors.fill: parent
    }

    FrameAnimation {
        running: root.followsMovement && root.visible && root.backdrop !== null
        onTriggered: root.updateSourceRect()
    }
    onXChanged: updateSourceRect()
    onYChanged: updateSourceRect()
    Connections {
        target: root.backdrop
        ignoreUnknownSignals: true
        function onXChanged() { root.updateSourceRect() }
        function onYChanged() { root.updateSourceRect() }
        function onWidthChanged() { root.updateSourceRect() }
        function onHeightChanged() { root.updateSourceRect() }
    }

    onWidthChanged: { Qt.callLater(updateUniforms); updateSourceRect() }
    onHeightChanged: { Qt.callLater(updateUniforms); updateSourceRect() }
    onRadiusChanged: Qt.callLater(updateUniforms)
    onDarkChanged: Qt.callLater(updateUniforms)
    onTintAmountChanged: Qt.callLater(updateUniforms)
    onLightChanged: Qt.callLater(updateUniforms)
    onTintChanged: Qt.callLater(updateUniforms)
    onMaterializeChanged: Qt.callLater(updateUniforms)
    onReducedTransparencyChanged: Qt.callLater(updateUniforms)
    onIncreasedContrastChanged: Qt.callLater(updateUniforms)
    onBackdropColorChanged: Qt.callLater(updateUniforms)
    onDprChanged: Qt.callLater(updateUniforms)
    onBackdropChanged: updateSourceRect()
    Component.onCompleted: { updateUniforms(); updateSourceRect() }
}
