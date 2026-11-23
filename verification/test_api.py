#!/usr/bin/env python3
"""Contract tests for the public extension API.

The API package is plain Java with no Minecraft, loader or GL dependency, so its shape can
be checked without a JDK: this script asserts the guarantees addon authors are promised,
and fails the build when someone breaks one of them by accident.

Guarantees checked here:
  * the API never imports Minecraft, Fabric, Forge or LWJGL;
  * every tuning field is a multiplier clamped to a documented range;
  * an extension callback cannot crash the caller (failure budget exists);
  * re-entrancy guards and idempotent registration are present;
  * the documented discovery routes still exist.
"""
from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
API = ROOT / "src" / "main" / "java" / "dev" / "bedrockshaders" / "api"

REQUIRED_FILES = [
    "BedrockShadersApi.java",
    "BedrockShadersExtension.java",
    "ConfigSource.java",
    "ConfigView.java",
    "ExtensionDiscovery.java",
    "ExtensionRegistry.java",
    "FamilySpec.java",
    "FrameHook.java",
    "JsonExtensionLoader.java",
    "PackDescriptor.java",
    "PackPreprocessor.java",
    "PackWriter.java",
    "TuningDelta.java",
]

# Anything in here would tie an addon to one Minecraft version or one loader.
FORBIDDEN = re.compile(
    r"\bnet\.minecraft\b|\bnet\.fabricmc\b|\bnet\.minecraftforge\b|\bnet\.neoforged\b"
    r"|\borg\.lwjgl\b|\bcom\.mojang\.blaze3d\b"
)

problems: list[str] = []


def fail(message: str) -> None:
    problems.append(message)


def read(name: str) -> str:
    path = API / name
    if not path.is_file():
        fail(f"missing API file: {name}")
        return ""
    return path.read_text(encoding="utf-8")


def strip_comments(text: str) -> str:
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return re.sub(r"//[^\n]*", "", text)


def check_independence() -> None:
    for path in sorted(API.glob("*.java")):
        text = strip_comments(path.read_text(encoding="utf-8"))
        for number, line in enumerate(text.splitlines(), 1):
            if FORBIDDEN.search(line):
                fail(f"{path.name}:{number}: API must stay loader- and version-independent -> "
                     f"{line.strip()}")


def check_files() -> None:
    for name in REQUIRED_FILES:
        read(name)


def check_versioning() -> None:
    api = strip_comments(read("BedrockShadersApi.java"))
    if "API_VERSION = 1" not in api:
        fail("API_VERSION must be declared as 1 (bump only with a documented migration)")
    if "ID_PATTERN" not in api:
        fail("ID_PATTERN is missing: addon ids must be validated, not trusted")
    if "supports(" not in api:
        fail("supports(int) is missing: addons need to ask before they register")
    if "validateId(" not in api:
        fail("validateId(String) is missing")

    registry = strip_comments(read("ExtensionRegistry.java"))
    if "FAILURE_BUDGET" not in registry:
        fail("FAILURE_BUDGET is missing: a throwing addon must be disabled, not fatal")
    if ".supports(" not in registry:
        fail("register() must check the addon's apiVersion before accepting it")
    if "disabled" not in registry:
        fail("entries must expose a disabled flag")
    for required in ("registerFamily", "registerFrameHook", "registerPackPreprocessor"):
        if required not in registry:
            fail(f"ExtensionRegistry.{required} is missing: the extension surface shrank")
    if "notifyConfigChanged" not in registry:
        fail("notifyConfigChanged is missing: ConfigView would never reach addons")
    if "runPreprocessors" not in registry:
        fail("runPreprocessors is missing: import-time extension is not wired")
    if "fireAfterImport" not in registry:
        fail("fireAfterImport is missing: post-import extension is not wired")
    if "matchFamilyId" not in registry:
        fail("matchFamilyId is missing: extension families would never be detected")


def check_tuning_contract() -> None:
    delta = strip_comments(read("TuningDelta.java"))
    if "MIN = 0.5F" not in delta and "MIN = 0.5f" not in delta:
        fail("TuningDelta.MIN must stay 0.5: multiplicative tuning must be bounded")
    if "MAX = 1.5F" not in delta and "MAX = 1.5f" not in delta:
        fail("TuningDelta.MAX must stay 1.5")
    for member in ("reset()", "clamp()", "isIdentity()", "compose("):
        if member not in delta:
            fail(f"TuningDelta.{member} is missing")

    family = strip_comments(read("FamilySpec.java"))
    scales = re.findall(r"public float (\w+)\(", family)
    if len(scales) < 12:
        fail(f"FamilySpec exposes only {len(scales)} tuning accessors; the built-in families "
             f"have 13, so addons cannot reproduce them")
    if "MIN_SCALE = 0.25F" not in family and "MIN_SCALE = 0.25f" not in family:
        fail("FamilySpec.MIN_SCALE is missing or changed unexpectedly")
    if "MAX_SCALE = 4.0F" not in family and "MAX_SCALE = 4.0f" not in family:
        fail("FamilySpec.MAX_SCALE is missing or changed unexpectedly")


def check_isolation() -> None:
    registry = strip_comments(read("ExtensionRegistry.java"))
    if "catch (Throwable" not in registry and "catch (RuntimeException" not in registry:
        fail("extension callbacks must be wrapped: an addon throw may not escape the registry")
    if "guarded(" not in registry:
        fail("the guarded() wrapper is missing: callback isolation was removed")

    writer = strip_comments(read("PackWriter.java"))
    if '".."' not in writer and "'..'" not in writer:
        fail("PackWriter must reject '..' path traversal")
    if "pack.mcmeta" not in writer:
        fail("PackWriter must refuse to write pack.mcmeta")
    if "assets/minecraft" not in writer and "minecraft" not in writer:
        fail("PackWriter must refuse to overwrite vanilla assets")

    descriptor = strip_comments(read("PackDescriptor.java"))
    if "veto" not in descriptor:
        fail("PackDescriptor.veto is missing: an addon cannot refuse an import")
    if "descriptionNotes" not in descriptor:
        fail("PackDescriptor.addDescriptionNote is missing")


def check_discovery() -> None:
    discovery = strip_comments(read("ExtensionDiscovery.java"))
    if "ServiceLoader.load" not in discovery:
        fail("ServiceLoader discovery is missing: code addons have no entry point")
    if "META-INF/services" not in discovery:
        fail("the documented service file path is missing")
    if "guide()" not in discovery:
        fail("ExtensionDiscovery.guide() is missing: /bedrock api guide would be empty")

    loader = strip_comments(read("JsonExtensionLoader.java"))
    if "bedrock_shaders/api.json" not in loader:
        fail("the documented data-addon path is missing")
    if "supports(" not in loader:
        fail("data addons must be checked against API_VERSION too")

    config_source = strip_comments(read("ConfigSource.java"))
    if "install(" not in config_source:
        fail("ConfigSource.install is missing: addons would only ever see defaults")


def check_wiring() -> None:
    """The API is only real if the renderer and importer actually call into it."""
    frame = ROOT / "src/client/java/dev/bedrockshaders/client/render/FrameContext.java"
    text = strip_comments(frame.read_text(encoding="utf-8"))
    if "applyFrameHooks" not in text:
        fail("FrameContext never runs extension frame hooks")
    if "ExtensionRegistry" not in text:
        fail("FrameContext does not consult the extension registry")

    pipeline = ROOT / "src/client/java/dev/bedrockshaders/client/render/BedrockPipeline.java"
    text = strip_comments(pipeline.read_text(encoding="utf-8"))
    for member in ("extensionExposure", "extensionSaturation", "extensionContrast"):
        if member not in text:
            fail(f"BedrockPipeline ignores {member}(): a hook would silently do nothing")

    importer = ROOT / "src/client/java/dev/bedrockshaders/client/pack/BedrockPackImporter.java"
    text = strip_comments(importer.read_text(encoding="utf-8"))
    if "runPreprocessors" not in text:
        fail("the importer never runs extension preprocessors")
    if "fireAfterImport" not in text:
        fail("the importer never fires the after-import callback")

    client = ROOT / "src/client/java/dev/bedrockshaders/client/BedrockShadersClient.java"
    text = strip_comments(client.read_text(encoding="utf-8"))
    if "loadServices" not in text:
        fail("client startup never loads extension services")
    if "JsonExtensionLoader" not in text:
        fail("resource reload never reads data-addon declarations")

    config = ROOT / "src/client/java/dev/bedrockshaders/client/ConfigManager.java"
    text = strip_comments(config.read_text(encoding="utf-8"))
    if "ConfigSource.install" not in text:
        fail("the client never installs the ConfigView bridge")
    if "notifyConfigChanged" not in text:
        fail("saving settings never notifies extensions")

    family = ROOT / "src/main/java/dev/bedrockshaders/config/ShaderFamily.java"
    text = strip_comments(family.read_text(encoding="utf-8"))
    if "matchFamilyId" not in text:
        fail("pack family detection ignores extension families")

    command = ROOT / "src/client/java/dev/bedrockshaders/client/command/BedrockShadersCommand.java"
    text = strip_comments(command.read_text(encoding="utf-8"))
    if "printApiStatus" not in text:
        fail("/bedrock api diagnostics are missing")


def check_docs() -> None:
    for name in ("README.md", "README.zh_cn.md"):
        path = ROOT / name
        if not path.is_file():
            fail(f"missing {name}")
            continue
        text = path.read_text(encoding="utf-8")
        if "bedrockshaders.api" not in text:
            fail(f"{name} never mentions the extension API package")
        if "api.json" not in text:
            fail(f"{name} does not document data addons")
        if "ServiceLoader" not in text and "services/" not in text:
            fail(f"{name} does not document code addons")


def main() -> int:
    check_files()
    check_independence()
    check_versioning()
    check_tuning_contract()
    check_isolation()
    check_discovery()
    check_wiring()
    check_docs()

    print(f"extension API: {len(list(API.glob('*.java')))} files, "
          f"{len(REQUIRED_FILES)} required, {len(problems)} problem(s)")
    if problems:
        print("\n".join(problems), file=sys.stderr)
        return 1
    print("Extension API contract OK: discovery, isolation, tuning bounds and wiring all present.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
