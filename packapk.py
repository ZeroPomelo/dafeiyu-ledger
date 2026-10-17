#!/usr/bin/env python3
"""把 d8 产出的 classes.dex 塞进 aapt2 生成的 APK 里。"""
import shutil
import sys
import zipfile

src, dst, dex = sys.argv[1], sys.argv[2], sys.argv[3]
shutil.copyfile(src, dst)
with zipfile.ZipFile(dst, "a", zipfile.ZIP_DEFLATED) as z:
    z.write(dex, "classes.dex")
print("packed classes.dex ->", dst)
