# protected

Verifies `XReleaseConfig.protectedGroups`: an accepted project taxon and its whole subtree are
shielded from merge syncs.

The genus `Carabus` is configured as a protected group in `xcfg.yaml`. The `src` merge sector tries to:

- add a new species `Carabus nemoralis` inside the protected genus → must be **blocked**
- add authorship `Linnaeus, 1761` to the existing, author-less `Carabus auratus` → must be **blocked**
- add a new species `Bembidion properans` under the non-protected sibling genus `Bembidion` → must be **merged**

`expected.txtree` therefore keeps `Carabus` untouched while `Bembidion` gains the new species.
See `protectedValidate()` in `SectorSyncMergeIT` for the explicit assertions.
