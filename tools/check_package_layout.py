"""Validate Java package paths and NeoForge/Mixin metadata after package moves."""
from pathlib import Path
import json
import re
import tomllib

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java"
RESOURCES = ROOT / "src/main/resources"
classes = {}
for path in JAVA.rglob("*.java"):
    text = path.read_text()
    package = re.search(r"^package ([\w.]+);", text, re.M)
    assert package, f"Missing package declaration: {path}"
    assert package[1].replace(".", "/") == str(path.parent.relative_to(JAVA)), path
    if path.name != "package-info.java":
        classes[package[1] + "." + path.stem] = (path, text)

metadata = tomllib.loads((ROOT / "src/main/templates/META-INF/neoforge.mods.toml").read_text())
configured = set()
configs = metadata.get("mixins", [])
for item in configs:
    name = item["config"].replace("${mod_id}", "create_bioships")
    config = json.loads((RESOURCES / name).read_text())
    package = config["package"]
    for section in ("mixins", "client"):
        for relative in config.get(section, []):
            full = package + "." + relative
            assert full in classes, f"Missing Mixin class: {full}"
            assert full not in configured, f"Duplicate Mixin: {full}"
            assert re.search(r"@Mixin\b", classes[full][1]), full
            configured.add(full)
    # A Mixin package must not accidentally capture ordinary mod classes.
    for full, (_, text) in classes.items():
        if full.startswith(package + "."):
            assert re.search(r"@Mixin\b", text), f"Non-Mixin inside protected Mixin package: {full}"
actual = {full for full, (_, text) in classes.items() if re.search(r"@Mixin\b", text)}
assert configured == actual, f"Unconfigured Mixins: {actual - configured}"
print(f"Package paths valid: {len(classes)} Java types; {len(configs)} configs cover {len(actual)} Mixins.")
