#!/bin/bash

# sort input
sort -o "$1" "$1"

# remove content from input file from all other dicts
for fn in *.txt; do
  mv "$fn" "$fn.orig"
  comm -2 -3 "$fn.orig" "$1" > "$fn"
done
rm *.orig

# add content to given target dict
cat "$1" >> "$2"