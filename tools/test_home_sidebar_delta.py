#!/usr/bin/env python3

import hashlib
import os
import shutil
import subprocess
import tempfile
import unittest
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
HOME_ZIP = ROOT / "app/src/main/assets/scr01-home-ui-1.7.28.zip"
STOCK_SHA256 = "d69adf660a95ff156b02ff2f08e52c0c4acf0bdbb5fa8c3b7c5dd48b96138d45"
PREVIOUS_PATCH_SHA256 = "bb226a5f4b7b2fe86b6f24870798a25c605fa123b4d578a69db51c4d50adf1bd"
SHORTCUTS = (
    ("Microsoft Launcher", "com.microsoft.launcher"),
    ("FlClash", "com.follow.clash"),
    ("Mika", "com.wangzan101.mika"),
    ("SurfingTile", "com.github.surfing"),
    ("Flip Clock", "com.zwh.flip.clock"),
    ("Alynx Clock", "one.alynx.flipclock"),
    ("Clock", "com.android.deskclock"),
)


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


class HomeSidebarDeltaTest(unittest.TestCase):
    def test_module_zip_does_not_carry_the_stock_apk(self) -> None:
        with zipfile.ZipFile(HOME_ZIP) as archive:
            names = archive.namelist()
        self.assertNotIn("MHSHome.apk", names)
        self.assertFalse(any(name.endswith(".apk") for name in names))
        self.assertIn("patch/MHSHome.bsdiff", names)

    def test_scripts_pin_the_new_patch_and_keep_the_previous_one(self) -> None:
        with zipfile.ZipFile(HOME_ZIP) as archive:
            for name in ("customize.sh", "boot-completed.sh", "uninstall.sh"):
                text = archive.read(name).decode("utf-8")
                self.assertEqual(text.count(STOCK_SHA256), 1, name)
                self.assertIn(f"EXPECTED_PATCH_SHA256=", text)
                self.assertNotIn(PREVIOUS_PATCH_SHA256, text) if name == "uninstall.sh" else self.assertEqual(
                    text.count(PREVIOUS_PATCH_SHA256), 1, name
                )
                self.assertNotIn("android.intent.action.SET_WALLPAPER", text)

    def test_stock_apk_round_trip_contains_shortcuts_when_supplied(self) -> None:
        stock = os.environ.get("SCROOT_STOCK_MHSHOME")
        if not stock:
            self.skipTest("stock MHSHome APK is a private build input and is not in the repo")
        stock_path = Path(stock)
        stock_bytes = stock_path.read_bytes()
        self.assertEqual(sha256(stock_bytes), STOCK_SHA256)
        with zipfile.ZipFile(HOME_ZIP) as archive:
            patch = archive.read("patch/MHSHome.bsdiff")
            expected = archive.read("boot-completed.sh").decode("utf-8")
        expected_patch = expected.split("EXPECTED_PATCH_SHA256=", 1)[1].splitlines()[0]
        patcher = ROOT / "tools/apk-delta/scbspatch.c"
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            old = directory / "stock.apk"
            delta = directory / "patch.bsdiff"
            new = directory / "patched.apk"
            tool = directory / "scbspatch"
            old.write_bytes(stock_bytes)
            delta.write_bytes(patch)
            bzip2 = shutil.which("bzip2")
            self.assertIsNotNone(bzip2)
            subprocess.run(
                ["cc", "-O2", f"-DBZIP2_PATH=\"{bzip2}\"", "-o", str(tool), str(patcher)],
                check=True,
            )
            subprocess.run([str(tool), str(old), str(new), str(delta)], check=True)
            patched = new.read_bytes()
            with zipfile.ZipFile(new) as archive:
                dex = archive.read("classes.dex")
        self.assertEqual(sha256(patched), expected_patch)
        for label, package_name in SHORTCUTS:
            self.assertIn(label.encode(), dex)
            self.assertIn(package_name.encode(), dex)
        self.assertIn(b"SCRoot", dex)
        self.assertIn(b"KSU Next", dex)
        self.assertIn(b"launchable", dex)
        self.assertIn(b"addIfLaunchable", dex)
        self.assertIn(b"getLaunchIntentForPackage", dex)
        self.assertNotIn(b"android.intent.category.HOME", dex)
        self.assert_menu_context_register(dex)

    def assert_menu_context_register(self, dex: bytes) -> None:
        """The string search above does not see register types. ART rejected
        MenuData.getData because v6 was a String passed as Context at code
        unit 0x168. This walks the generated dex invoke arguments."""
        jars = Path("/tmp/smali-jars")
        jars.mkdir(exist_ok=True)
        needed = {
            "dexlib2.jar": "https://repo1.maven.org/maven2/org/smali/dexlib2/2.5.2/dexlib2-2.5.2.jar",
            "util.jar": "https://repo1.maven.org/maven2/org/smali/util/2.5.2/util-2.5.2.jar",
            "guava.jar": "https://repo1.maven.org/maven2/com/google/guava/guava/27.1-android/guava-27.1-android.jar",
            "jsr305.jar": "https://repo1.maven.org/maven2/com/google/code/findbugs/jsr305/3.0.2/jsr305-3.0.2.jar",
            "failureaccess.jar": "https://repo1.maven.org/maven2/com/google/guava/failureaccess/1.0.1/failureaccess-1.0.1.jar",
        }
        for name, url in needed.items():
            target = jars / name
            if not target.exists() or target.stat().st_size == 0:
                subprocess.run(["curl", "-fsSL", "-o", str(target), url], check=True)
        source = ROOT / "tools/dex-verify/CheckInvokeTypes.java"
        classes = jars / "classes"
        classes.mkdir(exist_ok=True)
        classpath = ":".join(str(jars / name) for name in needed)
        subprocess.run(
            ["javac", "-cp", classpath, "-d", str(classes), str(source)],
            check=True,
        )
        with tempfile.NamedTemporaryFile(suffix=".dex") as handle:
            handle.write(dex)
            handle.flush()
            result = subprocess.run(
                [
                    "java",
                    "-cp",
                    classpath + ":" + str(classes),
                    "CheckInvokeTypes",
                    handle.name,
                    "MenuData;->getData",
                ],
                check=False,
                capture_output=True,
                text=True,
            )
        output = result.stdout + result.stderr
        self.assertNotIn(
            "Ljava/lang/String; expected Landroid/content/Context;",
            output,
            output,
        )
        self.assertIn("checked=1", output, output)


if __name__ == "__main__":
    unittest.main()
