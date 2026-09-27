# Contract interior visual asset candidates

The gameplay implementation does **not** depend on an external map. The
built-in programmatic home remains the safe fallback while visual templates are
evaluated and converted to Minecraft 1.20.1.

## Preferred candidates

### CedricD0812 — Japanese House

- Source: Planet Minecraft project `japanese-house-6691798`
- Declared license: **CC BY 4.0**
- Source build: Minecraft 1.21
- Shape: detailed Japanese exterior with an intentionally empty interior
- Use in Contract Blade: strong candidate for a future default shell because the
  empty interior leaves room for level/favorability-driven rooms and decoration.
- Integration requirement: convert/downgrade all unsupported 1.21 blocks to
  1.20.1 equivalents and retain attribution in the mod credits/resources.

### Kill_Roy1218 — Japanese House

- Source: Planet Minecraft project `japanese-house-6821703`
- Author statement: projects may be copied, modified and used for any purpose;
  no credit requirement is stated.
- Use in Contract Blade: visual/scale reference and possible alternate template.
- Integration requirement: preserve a copy of the author's permission statement
  with any redistributed derivative even though the page does not name a formal
  SPDX-style license.

## Reference-only candidates

### PvPWarriorBuilds — Japanese House [FREE DOWNLOAD] 1.20+

The page explicitly provides a free 1.20+ schematic, but the surfaced project
text does not clearly grant redistribution inside another mod. Treat it as a
reference unless the author provides explicit redistribution permission.

## Import policy

1. Never make gameplay depend on a downloaded world or schematic.
2. Convert selected builds into a mod-owned template format after license review.
3. Strip containers/loot/command blocks/entities during conversion.
4. Normalize blocks to the 1.20.1 vanilla palette.
5. Keep attribution/permission text in this document and release credits.
6. The template is presentation only; binding allocation and progression remain
   owned by the Java interior services.
