# Isolated native visual experiment

These Java files are **not in a product sourceSet**. They show original assets in a clearly labelled diagnostic overlay; the two foxes are client-only EntityMaid previews, never spawned into or persisted by the server. This is not a playable port and must not be published as one.

Use separate detached clean worktrees at the audited commits; never use a player's world:

```bash
git worktree add --detach /tmp/cb-forge-audit d1429a46492783c4e773d6ccb17b606ca48f5113
git worktree add --detach /tmp/cb-neo-audit 2955432290cb6475e8dfceb960e9a0c388be085f
python tools/porting/audit/visual/prepare.py --master /tmp/cb-forge-audit --target /tmp/cb-neo-audit --platform neo --disposable-checkout
```

Prepare Forge with another clean master source checkout and `--platform forge` (the source and target must differ). Preserve original credits/licenses; this is a local experiment copying already present project assets, not permission to redistribute them under a different license.

Neo: JDK21, project pinned NeoForge21.1.219/TLM1.5.3-neoforge+mc1.21.1; run `./gradlew --no-daemon runClient`. Forge: JDK17, pinned Forge47.4.10, native TLM1.5.3 and SlashBladeResharped1.9.65; use the repository's existing `libs` and `tmp/compat-test` dependency setup, then `./gradlew --no-daemon runClient -Pci=true -PtlmRuntimeTest=true -PcompatTest=slashblade`. The Forge sample calls the original production BlackFoxEnergyShader and therefore requires its SlashBlade registration path. Do not invent new dependency coordinates.

Load a new/disposable world. This run used a copied Forge native fixture (`AuditForgeBaseline`) only on 1.20.1 and a disposable Neo dedicated test world only on 1.21.1; this did not test world migration. Set test avatar creative/peaceful/day using local world controls. GUI scale2, resolution1180×812, software OpenGL on the cloud desktop. The overlay logs shader/model status, creates previews after a world loads and requests one normal resource-pack reload after400 rendered frames. It draws the original native animated item, Manka wheel base texture, original energy shader and TLM fox models.

Observe motion for at least a complete4sec icon cycle; capture before/after reload and check `VISUAL_AUDIT_RELOAD_COMPLETE`. For a world-space production renderer, use the original RenderType state semantics; the Neo overlay's direct RenderSystem sample is only an API/GLSL feasibility test. Move/attack/equipment/voice/full skill animations need separate real gameplay tests, not just setting a preview's tick count.

For auto-connect in the disposable Neo checkout, this audit appended the following local build configuration (not shipped):

```groovy
neoForge { runs { client {
    programArgument '--quickPlayMultiplayer'
    programArgument '127.0.0.1'
} } }
```

The local Neo server was a **non-overlay** #38 checkout running `./gradlew runServer` against an already configured test world. Forge auto-open used `-PakatsukiVisualWorld=AuditForgeBaseline`; avoid spaces in this property's value because the current wrapper's argument handling split them in this environment. No real save or downloaded private world is required.

After observing, save/leave the test world and stop the client/server. Keep logs/screenshots separately. Discard only the disposable checkout when no longer needed; do not copy its modified source/resource tree back into a release. The prepared prototype deliberately does not include the full resource set, permissions, registration, gameplay or client networking.
