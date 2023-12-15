#!/bin/bash
for fn in *.txt; do
  mv "$fn" "$fn.orig"
  cat "$fn.orig" | sort | uniq > "$fn"
done
rm *.orig
