# Distribution audit inventory

Run against the audited master source checkout and actual built/downloaded JARs:

```bash
python tools/porting/audit/assets.py \
  --master /path/to/master-checkout \
  --master-jar /path/to/contract-blade-forge-1.20.1-1.2.1.jar \
  --release-jar /path/to/contract-blade-forge-1.20.1-1.0.6.jar \
  --neo-jar build/libs/contract-blade-1.21.1-PORT-PROBE-NOT-FOR-PLAY-0.0.0-port-dev.jar \
  --output /tmp/contract-blade-audit
```

The audit walks resources and authoring files directly, including files hidden by search ignore rules. It opens the actual JARs and all model-pack ZIP entries. SHA256 hashes identify bytes; a path being present does not establish behavior, compatibility or permission to redistribute. The inventory includes non-art resources because registration/recipe/manual/world data participate in player workflows.

Literal string references are candidates, **not reachability analysis**: dynamic ResourceLocations, framework conventions and generated geometry require manual source tracing. Class-name based procedural visuals are likewise an index, not proof of completeness. The Markdown flow audit records reviewed runtime chains and explicit remaining evidence. Classification of history uses the master's art/README and known distribution exclusions, with unresolved source material retained as such rather than declared shipped.

`CONFIG_GAPS.csv` retains the original definition arguments and declaration comments from the eight real common config classes, plus field/key and old usage candidates. It does not evaluate Java expressions or infer config push/pop hierarchy. Deprecated compatibility keys need actual getter/call-site review; declaration alone is not a request to revive an inactive mechanic. Neo's shared defaults do not constitute a custom-config adapter.

`visual/` contains opt-in isolated native-client prototypes, not product source. See its README before running. No new art is generated and no whole art/source directory is packaged.
