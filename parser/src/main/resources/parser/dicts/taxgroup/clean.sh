#!/bin/bash
for fn in *.txt; do
  mv "$fn" "$fn.orig"
  awk '
      {
        split($0, a, "#");      # remove comment part
        split(a[1], b, " ");    # get first word
        key = tolower(b[1]);
        if (!seen[key]++) {
          print $0;
        }
      }
    ' "$fn.orig" | sed -E '/^(.*N\.N\..*)?$/d' | sort > "$fn"
done
rm *.orig
