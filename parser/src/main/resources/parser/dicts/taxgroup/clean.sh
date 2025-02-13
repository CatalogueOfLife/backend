#!/bin/bash
for fn in *.txt; do
  mv "$fn" "$fn.orig"
  cat "$fn.orig" | sort | uniq | sed -E '/^(.*N\.N\..*)?$/d' > "$fn"
done
rm *.orig
