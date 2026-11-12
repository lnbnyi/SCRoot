#!/usr/bin/env python3

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
GLUE = ROOT / "corresponding-source/KernelSU-Next-SCR01/kernel/ksu_glue.c"


def function_body(source: str, signature: str) -> str:
    start = source.index(signature)
    brace = source.index("{", start)
    depth = 0
    for index in range(brace, len(source)):
        char = source[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return source[start:index + 1]
    raise AssertionError(f"unclosed function: {signature}")


class KernelUmountIdentityTest(unittest.TestCase):
    def setUp(self) -> None:
        self.source = GLUE.read_text("utf-8")

    def test_kernel_umount_stays_enabled_by_default(self) -> None:
        self.assertEqual(self.source.count("static bool enable_kernel_umount = true;"), 1)
        self.assertNotIn("static bool enable_kernel_umount = false;", self.source)

    def test_umount_detaches_only_an_overlay_mount_root(self) -> None:
        gate = function_body(self.source, "static bool ksu_path_is_overlay_mount(")
        umount = function_body(self.source, "static void ksu_umount_for_uid(")
        self.assertIn('strcmp(sb->s_type->name, "overlay")', gate)
        self.assertIn("path.dentry != path.mnt->mnt_root", gate)
        self.assertLess(
            umount.index("ksu_path_is_overlay_mount("),
            umount.index("orig_umount("),
        )
        self.assertIn("stat_kernel_umount_skip++;", umount)
        self.assertIn("continue;", umount[umount.index("stat_kernel_umount_skip++;"):])


if __name__ == "__main__":
    unittest.main()
