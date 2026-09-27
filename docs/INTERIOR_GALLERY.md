# Contract Interior Design Gallery

The gallery is a development workspace inside `maid_weapon:contract_interior`.
It is intentionally separate from real player contract plots.

## Commands

- `/maidweapon interior gallery`
  - enters the overview promenade;
  - generates any missing stage snapshots once;
  - never rebuilds an already generated stage.
- `/maidweapon interior gallery stage <1-5>`
  - jumps directly to one stage.
- `/maidweapon interior gallery rebuild <1-6>`
  - destructive developer command;
  - clears all five gallery plots and rebuilds them with the selected warmth stage.
- `/maidweapon interior gallery leave`
  - returns to the position from which the gallery was entered.

The Contract Heart Key also exits a gallery session because gallery state is
handled by the same return-position authority as normal interiors.

## Coordinates

Normal contract plots are allocated from non-negative coordinates.

The gallery lives at negative coordinates:

- base X: -8192
- base Z: -4096
- stage spacing: 144 blocks
- overview promenade: Z = -4024

This keeps the gallery permanently outside normal contract allocation.

## Current five-stage design

### Stage 1 — Core home

A compact Japanese-inspired room with a stone genkan, warm timber floor,
dark structural beams and a small amount of storage.

### Stage 2 — Daily life

Adds a kitchen/work wing and an engawa connecting the original home to the
new room. This is the first stage that clearly reads as a lived-in residence.

### Stage 3 — Garden

Adds a western planted garden, shallow pond, cherry trees, stepping stones and
a small torii. The contract interior begins to feel like a place rather than
only a room.

### Stage 4 — Quiet wing

Adds a study, tea room, covered northern corridor and a small karesansui court.
The interior now separates work, rest and private space.

### Stage 5 — Estate

Adds a guest pavilion, small shrine, second pond, bridge, cherry grove and a
far lookout pavilion. Paths connect late-stage landmarks back to the core home
so the final space reads as one estate rather than isolated structures.

## Editing workflow

1. Enter the gallery.
2. Use `gallery stage <n>` to jump to the stage you want to change.
3. Edit blocks normally in Creative mode.
4. Do **not** run `gallery rebuild` after manual edits unless you want to reset
   all five stages.
5. When the edited versions are ready, send the world save back for extraction
   into distributable structure templates.

For easiest handoff, zip the whole world save. If only the custom dimension is
needed, include the world's `dimensions/maid_weapon/contract_interior` data
plus the level metadata so the region files can be interpreted correctly.

## Offline render from the real save

After the gallery has been generated at least once, the repository can render
the actual Anvil region data without launching Minecraft:

```bash
python tools/render_contract_interior_world.py /path/to/world --stage 5
python tools/render_contract_interior_world.py /path/to/world --stage 5 --mode top
```

The renderer reads
`dimensions/maid_weapon/contract_interior/region/*.mca` directly, decodes the
1.20.x section palettes/block-state long arrays, and writes an SVG. It therefore
shows the blocks that are actually saved in the world rather than a conceptual
mockup of the Java builder.

The SVG renderer intentionally uses simplified block colors instead of Minecraft
textures. Block occupancy/coordinates come from the save, while complex block
models such as stairs, slabs, panes and fences are currently simplified for the
preview. For texture/model-accurate full-world rendering, a tool such as BlueMap
can be pointed at the same world save.

## Automated real-save preview

The `Contract Interior Preview` GitHub Actions workflow can generate a fresh
headless Forge world without opening the Minecraft client. Preview mode only
relaxes the TLM metadata dependency for that CI server; normal builds still
require Touhou Little Maid.

The workflow:

1. starts a headless Forge server;
2. runs `maidweapon interior gallery generate 4` through RCON;
3. flushes and stops the server cleanly;
4. reads the resulting `.mca` files with
   `tools/render_contract_interior_world.py`;
5. produces top-down and isometric PNGs for all five stages;
6. uploads them as the `contract-interior-preview` Actions artifact.

This gives a repeatable review image derived from the real saved world after
every structural change. It is still a simplified renderer: block placement is
authoritative, while Minecraft model/texture/lighting fidelity remains outside
the lightweight CI renderer.

## Visual review

The current layout was checked with an offline plan/isometric approximation
before implementation. That review caught a sparse late-stage composition, so
Stage 5 gained explicit path networks connecting the core home, guest pavilion,
shrine and lookout.

The authoritative visual review is still the in-game gallery because Minecraft
lighting, block states, TLM pathfinding and player eye level cannot be judged
perfectly from an offline schematic render.
