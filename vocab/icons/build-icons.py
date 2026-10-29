#!/usr/bin/env python3
"""
Builds the TaxGroup icons served by /vocab/taxgroup/{name}/icon.svg and /icon/{px}.png
from the PhyloPic silhouettes listed in icons.tsv, which sits next to the icons in the vocab jar:
TaxGroup reads the depicted taxa from it.

A group with several comma separated images becomes a composite in a cluster layout:
the first image large on the left, the others stacked on the right.
Only public domain images (CC0 or the PD mark) are accepted, so the icons need no attribution.

Requires python3 and rsvg-convert (brew install librsvg). Run from anywhere:
  python3 vocab/icons/build-icons.py
"""
import csv
import json
import re
import subprocess
import sys
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUT = HERE.parent / "src/main/resources/life/catalogue/api/vocab/taxgroup"
SIZES = [64, 128, 192]  # TaxGroup.SIZE
PUBLIC_DOMAIN = ("creativecommons.org/publicdomain/zero/", "creativecommons.org/publicdomain/mark/")

# boxes (x, y, w, h) in a 100x100 viewBox, one per image
LAYOUTS = {
  1: [(0, 0, 100, 100)],
  2: [(0, 36, 64, 64), (36, 0, 64, 60)],
  3: [(0, 0, 56, 100), (58, 0, 42, 47), (58, 53, 42, 47)],
}


def fetch(url):
  with urllib.request.urlopen(url, timeout=60) as r:
    return r.read().decode("utf8")


def license_of(uuid):
  return json.loads(fetch(f"https://api.phylopic.org/images/{uuid}"))["_links"]["license"]["href"]


def silhouette(uuid, box):
  """The image as a nested svg element fitted into box"""
  svg = fetch(f"https://images.phylopic.org/images/{uuid}/vector.svg")
  if re.search(r"<script|<image|<foreignObject|\son\w+=", svg, re.I):
    raise ValueError(f"Unexpected active or external content in {uuid}")
  vb = re.search(r'viewBox="([^"]*)"', svg).group(1)
  body = re.search(r"<svg[^>]*>(.*)</svg>", svg, re.S).group(1)
  body = re.sub(r"<metadata>.*?</metadata>", "", body, flags=re.S).strip()
  x, y, w, h = box
  return f'<svg x="{x}" y="{y}" width="{w}" height="{h}" viewBox="{vb}" preserveAspectRatio="xMidYMid meet">\n{body}\n</svg>'


def main():
  OUT.mkdir(parents=True, exist_ok=True)
  with open(OUT / "icons.tsv", newline="") as f:
    rows = list(csv.DictReader(f, delimiter="\t"))
  for row in rows:
    group = row["group"]
    uuids = row["phylopic"].split(",")
    for u in uuids:
      lic = license_of(u)
      if not any(pd in lic for pd in PUBLIC_DOMAIN):
        sys.exit(f"{group}: image {u} is licensed {lic}, only public domain images are allowed")
    parts = "\n".join(silhouette(u, box) for u, box in zip(uuids, LAYOUTS[len(uuids)]))
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">\n'
           f'<title>{group}: {row["depicts"]} (PhyloPic, public domain)</title>\n{parts}\n</svg>\n')
    (OUT / f"{group}.svg").write_text(svg)
    for px in SIZES:
      subprocess.run(["rsvg-convert", "-w", str(px), "-h", str(px), "-o", str(OUT / f"{group}-{px}.png"), str(OUT / f"{group}.svg")],
                     check=True)
    print(group, ", ".join(uuids))


if __name__ == "__main__":
  main()
