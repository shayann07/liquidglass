"""Apache-2.0. The LiquidGlass Qt Quick example, from Python (PySide6).

    python main.py                 open the window
    python main.py --grab DIR      render, scroll, check the glass's pixels, save PNGs to DIR, exit

The QML module is ports/qt/LiquidGlass; its shaders must be compiled to .qsb first (CI does it on
Linux: see ports/qt/README.md). --grab is the offscreen check CI runs under Xvfb: it fails if a shader
did not compile, or if the glass does not change the pixels under it the way glass does.
"""
import sys
from pathlib import Path

from PySide6.QtCore import QObject, QTimer, QUrl
from PySide6.QtGui import QGuiApplication, QImage
from PySide6.QtQml import QQmlApplicationEngine
from PySide6.QtQuick import QQuickWindow
import shiboken6

HERE = Path(__file__).resolve().parent
MODULES = HERE.parent.parent  # ports/qt, which holds the LiquidGlass module


def region_stats(image: QImage, x: int, y: int, w: int, h: int):
    """Mean grey level and its standard deviation over a rectangle, from every second pixel."""
    values = []
    for j in range(y, y + h, 2):
        for i in range(x, x + w, 2):
            c = image.pixelColor(i, j)
            values.append(0.2126 * c.red() + 0.7152 * c.green() + 0.0722 * c.blue())
    mean = sum(values) / len(values)
    var = sum((v - mean) ** 2 for v in values) / len(values)
    return mean, var ** 0.5


def mean_difference(a: QImage, b: QImage, x: int, y: int, w: int, h: int) -> float:
    total, n = 0.0, 0
    for j in range(y, y + h, 2):
        for i in range(x, x + w, 2):
            ca, cb = a.pixelColor(i, j), b.pixelColor(i, j)
            total += (abs(ca.red() - cb.red()) + abs(ca.green() - cb.green()) + abs(ca.blue() - cb.blue())) / 3
            n += 1
    return total / n


def main() -> int:
    grab = sys.argv.index("--grab") if "--grab" in sys.argv else -1
    out = Path(sys.argv[grab + 1]) if grab >= 0 else None
    app = QGuiApplication(sys.argv)
    engine = QQmlApplicationEngine()
    engine.addImportPath(str(MODULES))
    engine.load(QUrl.fromLocalFile(str(HERE / "main.qml")))
    if not engine.rootObjects():
        print("main.qml did not load", file=sys.stderr)
        return 1
    if out is None:
        return app.exec()

    # PySide wraps the QML Window as a QWindow; re-wrap it to reach QQuickWindow.grabWindow.
    root = engine.rootObjects()[0]
    window = shiboken6.wrapInstance(shiboken6.getCppPointer(root)[0], QQuickWindow)
    # By objectName: PySide cannot convert a QML-defined type returned through a property.
    card, bar, view = (window.findChild(QObject, name) for name in ("card", "bar", "list"))
    out.mkdir(parents=True, exist_ok=True)
    failures = []
    shots = {}

    def check(ok: bool, what: str):
        print(("ok   " if ok else "FAIL ") + what)
        if not ok:
            failures.append(what)

    def step():
        try:
            advance()
        except Exception as e:  # a harness error fails now, not at the watchdog
            print(f"FAIL {type(e).__name__}: {e}", file=sys.stderr)
            app.exit(3)

    def advance(n=[0]):
        n[0] += 1
        if n[0] == 1:  # first frames rendered
            shots["glass"] = window.grabWindow()
            card.setProperty("visible", False)
            bar.setProperty("visible", False)
        elif n[0] == 2:
            shots["bare"] = window.grabWindow()
            card.setProperty("visible", True)
            bar.setProperty("visible", True)
            view.setProperty("contentY", view.property("contentY") + 150)  # scroll: the glass must follow
        elif n[0] == 3:
            shots["scrolled"] = window.grabWindow()
            # Shader status, after several frames (compilation is lazy): Compiled 0, Uncompiled 1, Error 2.
            for name, glass in (("card", card), ("bar", bar)):
                status = glass.property("shaderStatus")
                # The pixel checks below prove it draws; a status can read Uncompiled while it does.
                check(status != 2, f"{name} shader has no error (status {status}) {glass.property('shaderLog') or ''}".strip())
            dpr = shots["glass"].devicePixelRatio()
            px = lambda v: int(round(v * dpr))
            regions = {
                "card": (px(card.property("x")) + 8, px(card.property("y")) + 8, px(card.property("width")) - 16, px(card.property("height")) - 16),
                "bar": (px(bar.property("x")) + 8, px(bar.property("y")) + 8, px(bar.property("width")) - 16, px(bar.property("height")) - 16),
            }
            for name, r in regions.items():
                diff = mean_difference(shots["glass"], shots["bare"], *r)
                check(diff > 3, f"{name} changes the pixels under it (mean difference {diff:.1f} levels)")
                mean, sd = region_stats(shots["glass"], *r)
                check(sd > 2, f"{name} is not a flat fill (grey {mean:.0f}, spread {sd:.1f})")
            # The dark bar darkens a light page; the light card stays light.
            check(region_stats(shots["glass"], *regions["bar"])[0] < region_stats(shots["bare"], *regions["bar"])[0] - 10,
                  "the dark bar is darker than the page under it")
            check(region_stats(shots["glass"], *regions["card"])[0] > 150, "the light card stays light")
            # After scrolling, the glass shows different content: it follows the backdrop.
            moved = mean_difference(shots["glass"], shots["scrolled"], *regions["bar"])
            check(moved > 2, f"the bar follows the scrolled page (mean difference {moved:.1f} levels)")
            for key, image in shots.items():
                image.save(str(out / f"qt-{key}.png"))
            print(f"saved {', '.join(f'qt-{k}.png' for k in shots)} to {out}")
            app.exit(1 if failures else 0)
            return
        QTimer.singleShot(400, step)

    def watchdog():
        print("FAIL the example did not finish within 180 s", file=sys.stderr)
        app.exit(2)

    QTimer.singleShot(180_000, watchdog)
    QTimer.singleShot(800, step)
    return app.exec()


if __name__ == "__main__":
    sys.exit(main())
