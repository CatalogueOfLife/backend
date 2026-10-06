# profiles

Verifies that sector profiles cascade in position order and that profile blocklists reach the merge.

- `all merges` (position 1) lets subspecies through.
- `this source` (position 2) selects source dataset 100, narrows the ranks to genus and species and blocks
  `Amara aenea`.

The later profile wins for ranks, so the subspecies `Bembidion properans alpinum` is not merged. The blocklist reaches
the merge, so `Amara aenea` is not merged while its genus and `Amara familiaris` are.
