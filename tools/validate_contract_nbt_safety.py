import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


lifecycle = read("src/main/java/com/maidweapon/forge/compat/tlm/ContractMaidLifecycleService.java")
storage = read("src/main/java/com/maidweapon/forge/compat/tlm/ContractMaidStorage.java")
guard = read("src/main/java/com/maidweapon/forge/system/ContractNbtGuard.java")
codec = read("src/main/java/com/maidweapon/forge/system/MaidEntityDataCodec.java")
audit = read("src/main/java/com/maidweapon/forge/system/ContractNbtAudit.java")
weapon = read("src/main/java/com/maidweapon/forge/item/MaidWeaponItem.java")
config = read("src/main/java/com/maidweapon/common/MaidWeaponConfig.java")
intrinsic = read("src/main/java/com/maidweapon/forge/api/IntrinsicSpiritApi.java")
deployment = read("src/main/java/com/maidweapon/forge/system/InfusedMaidDeploymentSystem.java")
transfer = read("src/main/java/com/maidweapon/forge/system/deployment/ContractTransferSafetyService.java")
command = read("src/main/java/com/maidweapon/forge/event/MaidWeaponCommand.java")

# Recall is fail-safe: encode and verify a candidate before mutating the stack,
# and never discard the live maid until that commit succeeds.
for needle, source in [
    ("CompoundTag originalWeaponTag", lifecycle),
    ("ContractMaidStorage.commit(player, weaponStack, maidEntityTag)", lifecycle),
    ("weaponStack.setTag(originalWeaponTag)", lifecycle),
    ("MaidEntityDataCodec.write(candidate, maidData)", storage),
    ("weapon.setTag(candidate)", storage),
    ("contract_nbt_encode_failed", storage),
    ("contract_nbt_decode_failed", lifecycle),
]:
    if needle not in source:
        raise SystemExit(f"Missing atomic NBT safety invariant: {needle}")

capture = lifecycle.split(
    "public static boolean capture(", 1
)[1].split("public static boolean manifest(", 1)[0]
if capture.index("ContractMaidStorage.commit") > capture.index("entity.discard()"):
    raise SystemExit("Maid entity can be discarded before its encoded data is accepted")

# The codec is versioned, checksummed, bounded during decompression, and verifies
# the complete encoded candidate before removing the legacy representation.
for needle in [
    'COMPRESSED_DATA = "MaidEntityDataCompressed"',
    'FORMAT = "MaidEntityDataFormat"',
    'FORMAT_VERSION = 1',
    "CRC32",
    "new NbtAccounter(limit)",
    "actualSize != expectedSize",
    "ContractNbtGuard.depth(maidData)",
    "read(verified);",
    "container.remove(LEGACY_DATA)",
]:
    if needle not in codec:
        raise SystemExit(f"Compressed maid codec is incomplete: {needle}")
if codec.index("read(verified);") > codec.index("container.remove(LEGACY_DATA)"):
    raise SystemExit("Legacy data can be removed before compressed data is verified")

# Active intrinsic spirits use the root projection as their only full contract.
# Old duplicate saves prefer a usable root and retain the archive on migration failure.
for needle in [
    "migrateProjectedAuthority(weapon)",
    "removeArchivedContract(weapon, spiritId)",
    "restoreContract(weapon, archived)",
    "if (!usableContract(current))",
    "!MaidEntityDataCodec.migrate(root)) return",
    "entry.remove(CONTRACT)",
    "storeIntrinsic(weapon, spiritId, displayName, updatedIntrinsic)",
    "restoreExternalArchive(weapon)",
]:
    if needle not in intrinsic:
        raise SystemExit(f"Single-authority projection invariant is missing: {needle}")
activation = intrinsic.split("if (active) {", 1)[1].split(
    "if (!spiritId.equals(projected))", 1
)[0]
if activation.index("restoreContract(weapon, intrinsic)") > activation.index(
    "removeArchivedContract(weapon, spiritId)"
):
    raise SystemExit("Intrinsic archive is removed before its root projection is restored")

# Model the intended state machine for 1,000 ensure/activate/sleep/reactivate cycles.
# This catches accidental append-style schemas and duplicate active authority.
state = {"archive": {"spirit": {"Contract": "maid"}}, "root": None, "projection": None}
for _ in range(1000):
    state["archive"].setdefault("spirit", {"Contract": "maid"})
    state["root"] = state["archive"]["spirit"].pop("Contract")
    state["projection"] = "spirit"
    assert state["root"] == "maid" and "Contract" not in state["archive"]["spirit"]
    state["archive"]["spirit"]["Contract"] = state["root"]
    state["root"] = None
    state["projection"] = None
    state["root"] = state["archive"]["spirit"].pop("Contract")
    state["projection"] = "spirit"
    assert len(state["archive"]) == 1 and "Contract" not in state["archive"]["spirit"]
    state["archive"]["spirit"]["Contract"] = state["root"]
    state["root"] = None
    state["projection"] = None

# Warnings are diagnostic only. Full world SavedData externalization and hard item
# rejection are intentionally absent from the formal format.
for forbidden in ["hardExceeded()", "CONTRACT_NBT_HARD", "ContractMaidSavedData"]:
    if forbidden in lifecycle + storage + guard + weapon + intrinsic + config:
        raise SystemExit(f"Obsolete hard/external storage path remains: {forbidden}")
if (ROOT / "src/main/java/com/maidweapon/forge/system/ContractMaidStorage.java").exists():
    raise SystemExit("Whole-contract world SavedData storage must not be present")

for needle in ["NbtIo.write", "warningLevel()", "largestItems", "CountingOutputStream"]:
    if needle not in guard:
        raise SystemExit(f"NBT size guard is incomplete: {needle}")
for needle in ["duplicateProjection", "intrinsicArchiveBytes", "externalArchiveBytes"]:
    if needle not in audit or needle not in command:
        raise SystemExit(f"NBT audit is incomplete: {needle}")
for needle in [
    "warningBytes", "highWarningBytes", "criticalWarningBytes",
    "maxDecompressedMaidBytes", "maxDepth", "maxIntrinsicSpirits",
]:
    if needle not in config:
        raise SystemExit(f"Missing NBT server configuration: {needle}")
if "CONTRACT_NBT_MAX_INTRINSIC_SPIRITS" not in intrinsic or "spiritId.length() <= 64" not in intrinsic:
    raise SystemExit("Intrinsic spirits remain an unbounded NBT growth path")

# The anti-recursion rescue must remain in every serialization-sensitive path.
if deployment.count("rescueSelfStoredContract(player);") < 3:
    raise SystemExit("Self-stored contract rescue is no longer applied to all required paths")
for needle in ["boolean exactBinding", "boolean legacyBinding", "if (!exactBinding && !legacyBinding) continue"]:
    if needle not in transfer:
        raise SystemExit(f"Self-contract physical identity check is missing: {needle}")

for language in ["en_us", "zh_cn"]:
    path = ROOT / f"src/main/resources/assets/maid_weapon/lang/{language}.json"
    payload = json.loads(path.read_text(encoding="utf-8"))
    for suffix in ["warning", "high", "critical", "largest", "encode_failed", "decode_failed"]:
        key = f"maid_weapon.message.contract_nbt_{suffix}"
        if key not in payload:
            raise SystemExit(f"{language} is missing {key}")

print("Validated compressed single-authority contracts, atomic recovery, 1000-cycle stability, diagnostics, and anti-recursion rescue")
