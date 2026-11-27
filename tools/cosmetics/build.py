"""Builds cosmetic models: blender -b --factory-startup --python tools/cosmetics/build.py -- [model names]

With no names, builds every module in tools/cosmetics/models. Each model module has build(out_path) and writes one
.tmdl into src/client/resources/assets/templenz/models.
"""

import importlib
import os
import sys
import traceback

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
OUT = os.path.join(ROOT, "src", "client", "resources", "assets", "templenz", "models")
sys.path.insert(0, HERE)

import tmdl

FAR = 0.35

def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    names = argv or sorted(f[:-3] for f in os.listdir(os.path.join(HERE, "models")) if f.endswith(".py") and not f.startswith("_"))
    os.makedirs(OUT, exist_ok=True)
    failed = []
    for name in names:
        for scale, suffix in ((1.0, ""), (FAR, "_far")):
            try:
                tmdl.clear_scene()
                tmdl.LOD_SCALE = scale
                module = importlib.import_module("models." + name)
                module.build(os.path.join(OUT, name + suffix + ".tmdl"))
            except Exception:
                traceback.print_exc()
                failed.append(name + suffix)
    if failed:
        print("TMDL FAILED: " + ", ".join(failed))
        sys.exit(1)
    print("TMDL DONE: %d models" % len(names))

main()
