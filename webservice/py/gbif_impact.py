#!/usr/bin/env python3
"""
Impact report comparing GBIF's species matching (matching-ws, as stored in the interpreted occurrences)
with the CLB usage matcher behind the GBIF compatible /v2/species/match of a WsMatchingServer.
Modelled on GBIF's pre-backbone-release impact reports (gbif/pipelines gbif/ingestion/pre-backbone-release).

Name matching only: no taxonID, scientificNameID or taxonConceptID is ever sent to either service.

  match   <download.zip|tsv> --out proposed.tsv   match every verbatim row >= --min-occurrences against CLB,
                                                  with --gbif also against a GBIF matching-ws, live and without ids
  report  proposed.tsv --out DIR [--verify v.tsv]  categorise, write DIR/diff.tsv and DIR/report.html
  verify  DIR/diff.tsv --out verify.tsv            re-ask live api.gbif.org for the top differing rows

The download is a GBIF SQL_TSV_ZIP, see docs/GBIF-MATCH.md. Python 3.9+, standard library only.
"""
import argparse
import concurrent.futures
import csv
import heapq
import html
import io
import json
import os
import subprocess
import sys
import threading
import time
import urllib.parse
import zipfile
from collections import Counter, defaultdict
from http.client import HTTPConnection, HTTPSConnection

csv.field_size_limit(sys.maxsize)

COL_CHECKLIST = "7ddf754f-d193-4cc9-b351-99906754a03b"
GBIF_API = "https://api.gbif.org"
RANKS = ["kingdom", "phylum", "class", "order", "family", "genus", "species"]

# verbatim download column -> GBIF v2 match parameter. Ids are deliberately absent.
PARAMS = [
  ("v_scientificname", "scientificName"),
  ("v_scientificnameauthorship", "scientificNameAuthorship"),
  ("v_taxonrank", "taxonRank"),
  ("v_verbatimtaxonrank", "verbatimTaxonRank"),
  ("v_genericname", "genericName"),
  ("v_specificepithet", "specificEpithet"),
  ("v_infraspecificepithet", "infraspecificEpithet"),
  ("v_kingdom", "kingdom"),
  ("v_phylum", "phylum"),
  ("v_class", "class"),
  ("v_order", "order"),
  ("v_superfamily", "superfamily"),
  ("v_family", "family"),
  ("v_subfamily", "subfamily"),
  ("v_tribe", "tribe"),
  ("v_subtribe", "subtribe"),
  ("v_genus", "genus"),
  ("v_subgenus", "subgenus"),
]
VERBATIM = [c for c, _ in PARAMS] + ["v_nomenclaturalcode"]
IDS = ["v_taxonid", "v_scientificnameid", "v_taxonconceptid"]
CURRENT = ["taxonkey", "acceptedtaxonkey"] + [r + "key" for r in RANKS] + \
          ["scientificname", "acceptedscientificname", "taxonrank", "taxonomicstatus", "issues"]
MATCH_FIELDS = ["matchType", "confidence", "note", "usageKey", "usageName", "usageRank", "usageStatus",
                "acceptedKey", "acceptedName"] + [r + "Key" for r in RANKS] + [r + "Name" for r in RANKS]
PROPOSED = ["clb_" + f for f in MATCH_FIELDS] + ["clb_error"]
LIVE = ["gbif_" + f for f in MATCH_FIELDS] + ["gbif_error"]
OUT_COLS = ["row", "occurrences", "id_flag"] + VERBATIM + IDS + ["cur_" + c for c in CURRENT] + PROPOSED + LIVE

# categories, most severe first
CATEGORIES = [
  ("DIFF_KINGDOM", "different kingdom"),
  ("DIFF_PHYLUM", "same kingdom, different phylum"),
  ("DIFF_CLASS", "same phylum, different class"),
  ("DIFF_ORDER", "same class, different order"),
  ("DIFF_FAMILY", "same order, different family"),
  ("DIFF_GENUS", "same family, different genus"),
  ("DIFF_SPECIES", "same genus, different species"),
  ("DIFF_OTHER", "different usage, all shared ranks agree"),
  ("CLB_NONE", "GBIF matched exactly or to a higher rank, CLB found nothing"),
  ("CLB_NONE_FUZZY", "GBIF matched fuzzily, CLB found nothing - the known gap"),
  ("CLB_COARSER", "CLB matched an ancestor of GBIF's match"),
  ("CLB_FINER", "CLB matched a descendant of GBIF's match"),
  ("GBIF_NONE", "CLB matched, GBIF found nothing"),
  ("CLB_ERROR", "the CLB request failed, e.g. no name at all"),
  ("SAME_ACCEPTED", "another usage of the same accepted taxon"),
  ("SAME", "same usage"),
  ("SAME_NONE", "neither matched"),
]
AGREE = {"SAME", "SAME_NONE"}


def log(msg):
  print(time.strftime("%H:%M:%S"), msg, file=sys.stderr, flush=True)


def clean(v):
  if v is None or v == "\\N":
    return ""
  return v.replace("\t", " ").replace("\n", " ").replace("\r", " ").strip()


def open_text(path):
  """The first .csv/.tsv/.txt member of a zip, or a plain file."""
  if path.endswith(".zip"):
    z = zipfile.ZipFile(path)
    member = next(n for n in z.namelist() if not n.endswith("/") and not n.startswith("__"))
    log(f"reading {member} from {path}")
    return io.TextIOWrapper(z.open(member), encoding="utf-8", newline="")
  return open(path, encoding="utf-8", newline="")


def read_tsv(path, lower=False):
  """Rows as dicts. GBIF downloads need lower=True, their header case is not guaranteed."""
  with open_text(path) as f:
    reader = csv.reader(f, delimiter="\t", quoting=csv.QUOTE_NONE)
    header = [h.strip().lower() if lower else h.strip() for h in next(reader)]
    for row in reader:
      yield {h: clean(row[i]) if i < len(row) else "" for i, h in enumerate(header)}


def match_params(rec):
  return [(p, rec[c]) for c, p in PARAMS if rec.get(c)]


def url(base, params, gbif=False):
  q = list(params)
  if gbif:
    q.append(("checklistKey", COL_CHECKLIST))
  q.append(("verbose", "true"))
  return base.rstrip("/") + "/v2/species/match?" + urllib.parse.urlencode(q)


class Client:
  """One keep-alive connection per thread."""

  def __init__(self, base):
    self.parsed = urllib.parse.urlparse(base)
    self.local = threading.local()

  def conn(self, fresh=False):
    c = getattr(self.local, "conn", None)
    if c is None or fresh:
      cls = HTTPSConnection if self.parsed.scheme == "https" else HTTPConnection
      c = cls(self.parsed.hostname or "localhost", self.parsed.port, timeout=60)
      self.local.conn = c
    return c

  def get(self, path):
    err = "retries exhausted"
    for attempt in range(3):
      try:
        c = self.conn(fresh=attempt > 0)
        c.request("GET", path, headers={"Accept": "application/json", "User-Agent": "clb-gbif-impact"})
        r = c.getresponse()
        body = r.read()
      except Exception as e:  # connection reset, timeout
        err = f"{type(e).__name__}: {e}"
        time.sleep(1 + attempt)
        continue
      if r.status == 200:
        return json.loads(body), None
      err = f"HTTP {r.status}: {body[:200].decode('utf-8', 'replace')}"
      if r.status not in (429, 502, 503, 504):
        break
      time.sleep(2 ** attempt)
    return None, clean(err)


def parse_match(resp):
  """GBIF v2 match response -> MATCH_FIELDS dict. Works for both services."""
  d = {f: "" for f in MATCH_FIELDS}
  if not resp:
    return d
  diag = resp.get("diagnostics") or {}
  d["matchType"] = diag.get("matchType") or ""
  d["confidence"] = str(diag.get("confidence") or "")
  d["note"] = clean(diag.get("note") or "")
  u = resp.get("usage")
  if u:
    d["usageKey"] = str(u.get("key") or "")
    d["usageName"] = clean(u.get("name") or "")
    d["usageRank"] = u.get("rank") or ""
    d["usageStatus"] = u.get("status") or ""
    acc = resp.get("acceptedUsage") or u
    d["acceptedKey"] = str(acc.get("key") or "")
    d["acceptedName"] = clean(acc.get("name") or "")
    for rn in resp.get("classification") or []:
      r = (rn.get("rank") or "").lower()
      if r in RANKS:
        d[r + "Key"] = str(rn.get("key") or "")
        d[r + "Name"] = clean(rn.get("name") or "")
  return d


def id_flag(rec):
  """1 if GBIF may have matched this row by one of its ids rather than by name."""
  if not any(rec.get(c) for c in IDS):
    return "0"
  issues = rec.get("issues", "")
  ignored = any(f in issues for f in ("_ID_IGNORED", "_ID_NOT_FOUND", "TAXON_MATCH_NAME_AND_ID_AMBIGUOUS"))
  return "0" if ignored else "1"


# ---------------------------------------------------------------- match
def cmd_match(a):
  done = 0
  if os.path.exists(a.out) and os.path.getsize(a.out) > 0:
    with open(a.out, encoding="utf-8") as f:
      last = None
      for line in f:
        last = line
      if last and not last.startswith("row\t"):
        done = int(last.split("\t", 1)[0])
    log(f"resuming after row {done}")
  client = Client(a.base)
  base_path = urllib.parse.urlparse(a.base).path.rstrip("/")
  gbif = Client(a.gbif) if a.gbif else None
  gbif_path = urllib.parse.urlparse(a.gbif).path.rstrip("/") if a.gbif else ""

  def work(item):
    rownum, rec = item
    params = match_params(rec)
    if a.checklist_key:
      params.append(("checklistKey", a.checklist_key))
    query = urllib.parse.urlencode(params)
    resp, err = client.get(base_path + "/v2/species/match?" + query) if params else (None, "no name")
    m = parse_match(resp)
    gm, gerr = {f: "" for f in MATCH_FIELDS}, ""
    if gbif:
      gresp, gerr = gbif.get(gbif_path + "/v2/species/match?" + query) if params else (None, "no name")
      gm = parse_match(gresp)
    out = [str(rownum), rec.get("occurrences", ""), id_flag(rec)]
    out += [rec.get(c, "") for c in VERBATIM + IDS]
    out += [rec.get(c, "") for c in CURRENT]
    out += [m[f] for f in MATCH_FIELDS] + [clean(err or "")]
    out += [gm[f] for f in MATCH_FIELDS] + [clean(gerr or "")]
    return "\t".join(out)

  def todo():
    for i, rec in enumerate(read_tsv(a.input, lower=True), start=1):
      if i <= done:
        continue
      if a.limit and i > a.limit:
        return
      try:
        occ = int(rec.get("occurrences") or 0)
      except ValueError:
        occ = 0
      if occ >= a.min_occurrences:
        yield i, rec

  new = done == 0
  start, n = time.time(), 0
  with open(a.out, "a", encoding="utf-8") as out, concurrent.futures.ThreadPoolExecutor(a.threads) as ex:
    if new:
      out.write("\t".join(OUT_COLS) + "\n")
    batch = []
    for item in todo():
      batch.append(item)
      if len(batch) >= a.threads * 50:
        for line in ex.map(work, batch):  # keeps the input order, so a resume can trust the last row
          out.write(line + "\n")
        n += len(batch)
        batch = []
        out.flush()
        log(f"{n} rows matched, {n / (time.time() - start):.0f}/s")
    for line in ex.map(work, batch):
      out.write(line + "\n")
    n += len(batch)
  log(f"done: {n} rows matched in {time.time() - start:.0f}s")


# ---------------------------------------------------------------- report
def none_current(r):
  if "_live_type" in r:  # a live answer: a match to a domain or realm has no kingdom
    return not r["cur_taxonkey"]
  return not r["cur_taxonkey"] or "TAXON_MATCH_NONE" in r["cur_issues"] or r["cur_kingdomkey"] == "0"


def gbif_type(r):
  if none_current(r):
    return "NONE"
  if r.get("_live_type"):
    return r["_live_type"]
  issues = r["cur_issues"]
  for t in ("FUZZY", "HIGHERRANK", "AGGREGATE"):
    if "TAXON_MATCH_" + t in issues:
      return t
  return "EXACT"


def categorise(r):
  if r["clb_error"]:
    return "CLB_ERROR"
  cur, clb = not none_current(r), bool(r["clb_usageKey"])
  if not cur and not clb:
    return "SAME_NONE"
  if not clb:
    return "CLB_NONE_FUZZY" if gbif_type(r) == "FUZZY" else "CLB_NONE"
  if not cur:
    return "GBIF_NONE"
  if r["cur_taxonkey"] == r["clb_usageKey"]:
    return "SAME"
  if r["cur_acceptedtaxonkey"] and r["cur_acceptedtaxonkey"] == r["clb_acceptedKey"]:
    return "SAME_ACCEPTED"
  cur_keys = {r["cur_" + rk + "key"] for rk in RANKS} - {""}
  clb_keys = {r["clb_" + rk + "Key"] for rk in RANKS} - {""}
  if r["cur_taxonkey"] in clb_keys or r["cur_acceptedtaxonkey"] in clb_keys:
    return "CLB_FINER"
  if r["clb_usageKey"] in cur_keys or r["clb_acceptedKey"] in cur_keys:
    return "CLB_COARSER"
  for rk in RANKS:
    a, b = r["cur_" + rk + "key"], r["clb_" + rk + "Key"]
    if a and b and a != b:
      return "DIFF_" + rk.upper()
  return "DIFF_OTHER"


def read_verify(path):
  live = {}
  if path:
    for rec in read_tsv(path):
      live[rec["row"]] = rec
  return live


def live_answer(r, prefix="gbif_"):
  """The live GBIF answer stored in a match result row, None if GBIF was not asked or failed."""
  if not r.get(prefix + "matchType") or r.get(prefix + "error"):
    return None
  return {f: r[prefix + f] for f in MATCH_FIELDS}


def apply_live(r, v):
  """Replaces the stored GBIF interpretation by the live, id free GBIF answer v (MATCH_FIELDS keys)."""
  r["_stored_taxonkey"] = r["cur_taxonkey"]
  r["cur_taxonkey"] = v["usageKey"]
  r["cur_acceptedtaxonkey"] = v["acceptedKey"]
  for rk in RANKS:
    r["cur_" + rk + "key"] = v[rk + "Key"]
  r["cur_scientificname"] = v["usageName"]
  r["cur_acceptedscientificname"] = v["acceptedName"]
  r["cur_taxonrank"] = v["usageRank"]
  r["cur_issues"] = "" if v["usageKey"] else "TAXON_MATCH_NONE"
  r["_live_type"] = v["matchType"] if v["usageKey"] else ""


def verbatim_label(r):
  name = r["v_scientificname"] or " ".join(x for x in (r["v_genericname"], r["v_specificepithet"], r["v_infraspecificepithet"]) if x)
  if r["v_scientificnameauthorship"] and r["v_scientificnameauthorship"] not in name:
    name += " " + r["v_scientificnameauthorship"]
  cl = " > ".join(x for x in (r["v_kingdom"], r["v_phylum"], r["v_class"], r["v_order"], r["v_family"], r["v_genus"]) if x)
  return name, cl


def cmd_report(a):
  os.makedirs(a.out, exist_ok=True)
  live = read_verify(a.verify)
  rows = Counter()
  occ = Counter()
  occ_id = Counter()
  cross = Counter()  # (gbif type, clb type) -> occurrences
  by_kingdom = defaultdict(Counter)  # kingdom -> category -> occurrences
  names = {}  # COL id -> name, both sides use the same release
  top = defaultdict(list)  # category -> heap of (occ, row)
  names_by_cat = defaultdict(Counter)  # category -> verbatim name -> occurrences
  total_occ, verified = 0, 0
  stale = Counter()  # id_flag -> occurrences whose stored interpretation differs from live GBIF
  diff_path = os.path.join(a.out, "diff.tsv")
  with open(diff_path, "w", encoding="utf-8") as diff:
    diff.write("\t".join(["category", "verified_live"] + OUT_COLS + ["gbif_url", "clb_url"]) + "\n")
    for r in read_tsv(a.proposed):
      n = int(r["occurrences"] or 0)
      v = live_answer(r) or live_answer(live.get(r["row"]) or {}, prefix="")
      if v:
        stored_none = none_current(r)
        stored = r["cur_taxonkey"]
        apply_live(r, v)
        verified += 1
        if (stored_none and r["cur_taxonkey"]) or (not stored_none and stored != r["cur_taxonkey"]):
          stale[r["id_flag"]] += n
      cat = categorise(r)
      rows[cat] += 1
      occ[cat] += n
      total_occ += n
      if r["id_flag"] == "1":
        occ_id[cat] += n
      cross[(gbif_type(r), r["clb_matchType"] or ("ERROR" if r["clb_error"] else "NONE"))] += n
      for rk in RANKS:
        if r["clb_" + rk + "Key"]:
          names[r["clb_" + rk + "Key"]] = r["clb_" + rk + "Name"]
      kingdom = r["clb_kingdomName"] or names.get(r["cur_kingdomkey"]) or r["cur_kingdomkey"] or r["v_kingdom"] or "?"
      by_kingdom[kingdom][cat] += n
      if cat in AGREE:
        continue
      params = match_params(r)
      diff.write("\t".join([cat, "1" if v else "0"] + [r.get(c, "") for c in OUT_COLS]
                           + [url(GBIF_API, params, gbif=True), url(a.base, params)]) + "\n")
      name, cl = verbatim_label(r)
      names_by_cat[cat][name] += n
      h = top[cat]
      item = (n, int(r["row"]), {k: r[k] for k in r} | {"_name": name, "_cl": cl, "_params": params, "_live": bool(v)})
      if len(h) < a.top:
        heapq.heappush(h, item)
      elif n > h[0][0]:
        heapq.heapreplace(h, item)
  sort_tsv(diff_path)
  write_html(a, rows, occ, occ_id, total_occ, cross, by_kingdom, top, names_by_cat, names, verified, stale)
  log(f"wrote {diff_path} and {os.path.join(a.out, 'report.html')}")


def sort_tsv(path):
  """Sorts the diff by occurrences, descending, keeping the header. Uses unix sort for big files."""
  tmp = path + ".sorted"
  col = OUT_COLS.index("occurrences") + 3  # 1 based, after category and verified_live
  env = dict(os.environ, LC_ALL="C")
  try:
    with open(tmp, "wb") as out:
      subprocess.run(["head", "-n", "1", path], stdout=out, check=True)
      tail = subprocess.Popen(["tail", "-n", "+2", path], stdout=subprocess.PIPE)
      subprocess.run(["sort", "-t", "\t", f"-k{col},{col}nr", "-S", "1G"], stdin=tail.stdout, stdout=out, env=env, check=True)
      tail.wait()
    os.replace(tmp, path)
  except Exception as e:
    log(f"could not sort {path}: {e}")


def write_html(a, rows, occ, occ_id, total_occ, cross, by_kingdom, top, names_by_cat, names, verified, stale):
  e = html.escape
  total_rows = sum(rows.values())

  def pct(x, total):
    return f"{100.0 * x / total:.2f}%" if total else ""

  p = []
  p.append("""<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>GBIF Match Impact</title>
<style>
:root{--bg:#fff;--fg:#1d1d1f;--muted:#6e6e73;--line:#e5e5ea;--accent:#0a5cad;--bad:#b3261e;--card:#f6f6f8}
@media (prefers-color-scheme: dark){:root:not([data-theme="light"]){--bg:#141416;--fg:#ececf0;--muted:#a0a0a8;
--line:#2c2c30;--accent:#7fb2ff;--bad:#ff8a80;--card:#1d1d21}}
:root[data-theme="dark"]{--bg:#141416;--fg:#ececf0;--muted:#a0a0a8;--line:#2c2c30;--accent:#7fb2ff;--bad:#ff8a80;--card:#1d1d21}
body{background:var(--bg);color:var(--fg);font:14px/1.45 -apple-system,system-ui,sans-serif;margin:0 auto;
max-width:1200px;padding:16px}
h1{font-size:22px}h2{font-size:18px;margin-top:32px;border-bottom:1px solid var(--line)}h3{font-size:15px}
table{border-collapse:collapse;width:100%;margin:8px 0}td,th{border-bottom:1px solid var(--line);padding:4px 6px;
text-align:left;vertical-align:top}th{color:var(--muted);font-weight:600}td.n{text-align:right;font-variant-numeric:tabular-nums}
.wrap{overflow-x:auto}code{font-size:12px;color:var(--muted)}a{color:var(--accent)}.bad{color:var(--bad)}
.muted{color:var(--muted)}details{background:var(--card);border-radius:6px;padding:6px 10px;margin:8px 0}
</style></head><body>
""")
  p.append("<h1>GBIF matching-ws vs CLB matcher</h1>\n")
  p.append(f"<p class=muted>{total_rows:,} distinct verbatim inputs with {total_occ:,} occurrences, "
           f"compared by name only against COL checklist <code>{COL_CHECKLIST}</code>. "
           f"GBIF's answer is live and id free for {verified:,} rows, the stored occurrence interpretation for the rest. "
           f"For {sum(stale.values()):,} occurrences the stored interpretation differs from live GBIF, "
           f"{stale['1']:,} of them possibly matched by id. "
           f"Generated {time.strftime('%Y-%m-%d %H:%M')}.</p>\n")

  p.append("<h2>Categories</h2>\n<div class=wrap><table>\n<tr><th>Category</th><th>Meaning</th><th>Inputs</th>"
           "<th>Occurrences</th><th>Share</th><th>Possibly id matched by GBIF</th></tr>\n")
  for cat, meaning in CATEGORIES:
    if rows[cat]:
      cls = " class=bad" if cat.startswith("DIFF_KINGDOM") else ""
      p.append(f"<tr><td{cls}><a href='#{cat}'>{cat}</a></td><td>{e(meaning)}</td><td class=n>{rows[cat]:,}</td>"
               f"<td class=n>{occ[cat]:,}</td><td class=n>{pct(occ[cat], total_occ)}</td>"
               f"<td class=n>{occ_id[cat]:,}</td></tr>\n")
  p.append("</table></div>\n")

  p.append("<h2>Match types (occurrences)</h2>\n")
  gtypes = sorted({g for g, _ in cross})
  ctypes = sorted({c for _, c in cross})
  p.append("<div class=wrap><table>\n<tr><th>GBIF \\ CLB</th>" + "".join(f"<th>{e(c)}</th>" for c in ctypes) + "</tr>\n")
  for g in gtypes:
    p.append(f"<tr><th>{e(g)}</th>" + "".join(f"<td class=n>{cross[(g, c)]:,}</td>" for c in ctypes) + "</tr>\n")
  p.append("</table></div>\n")

  p.append("<h2>Disagreement by kingdom (occurrences)</h2>\n")
  shown = [c for c, _ in CATEGORIES if c not in AGREE and rows[c]]
  p.append("<div class=wrap><table>\n<tr><th>Kingdom</th><th>Total</th>" + "".join(f"<th>{c}</th>" for c in shown) + "</tr>\n")
  for k, cnt in sorted(by_kingdom.items(), key=lambda kv: -sum(kv[1].values())):
    tot = sum(cnt.values())
    p.append(f"<tr><td>{e(k)}</td><td class=n>{tot:,}</td>"
             + "".join(f"<td class=n>{cnt[c]:,}</td>" for c in shown) + "</tr>\n")
  p.append("</table></div>\n")

  for cat, meaning in CATEGORIES:
    if cat in AGREE or not top.get(cat):
      continue
    p.append(f"<h2 id='{cat}'>{cat}</h2>\n<p class=muted>{e(meaning)}. {rows[cat]:,} inputs, {occ[cat]:,} occurrences."
             f" Top {len(top[cat])} inputs by occurrences.</p>\n")
    p.append("<div class=wrap><table>\n<tr><th>Occ.</th><th>Verbatim name</th><th>Verbatim classification</th>"
             "<th>GBIF</th><th>CLB</th><th>Links</th></tr>\n")
    for n, _, r in sorted(top[cat], key=lambda t: -t[0]):
      gb = (f"{e(r['cur_scientificname'] or '-')} <code>{e(r['cur_taxonkey'])}</code> "
            f"<span class=muted>{e(gbif_type(r))} {e(r['cur_taxonrank'])}</span>")
      if r["cur_acceptedtaxonkey"] and r["cur_acceptedtaxonkey"] != r["cur_taxonkey"]:
        gb += f"<br><span class=muted>= {e(r['cur_acceptedscientificname'])}</span>"
      if r["id_flag"] == "1":
        gb += "<br><span class=muted>possibly id matched</span>"
      if r["_live"]:
        gb += "<br><span class=muted>live</span>"
        if r.get("_stored_taxonkey") and r["_stored_taxonkey"] != r["cur_taxonkey"]:
          gb += f"<br><span class=muted>stored: <code>{e(r['_stored_taxonkey'])}</code></span>"
      cb = (f"{e(r['clb_usageName'] or '-')} <code>{e(r['clb_usageKey'])}</code> "
            f"<span class=muted>{e(r['clb_matchType'])} {e(r['clb_usageRank'])} {e(r['clb_confidence'])}</span>")
      if r["clb_acceptedKey"] and r["clb_acceptedKey"] != r["clb_usageKey"]:
        cb += f"<br><span class=muted>= {e(r['clb_acceptedName'])}</span>"
      if r["clb_note"]:
        cb += f"<br><span class=muted>{e(r['clb_note'])}</span>"
      if r["clb_error"]:
        cb += f"<br><span class=bad>{e(r['clb_error'])}</span>"
      links = (f"<a href='{e(url(GBIF_API, r['_params'], gbif=True))}'>gbif</a> "
               f"<a href='{e(url(a.base, r['_params']))}'>clb</a>")
      p.append(f"<tr><td class=n>{n:,}</td><td>{e(r['_name'])}</td><td class=muted>{e(r['_cl'])}</td>"
               f"<td>{gb}</td><td>{cb}</td><td>{links}</td></tr>\n")
    p.append("</table></div>\n")
    common = names_by_cat[cat].most_common(30)
    p.append("<details><summary>Most frequent verbatim names</summary><table>\n")
    for name, n in common:
      p.append(f"<tr><td>{e(name)}</td><td class=n>{n:,}</td></tr>\n")
    p.append("</table></details>\n")
  p.append("</body></html>\n")
  with open(os.path.join(a.out, "report.html"), "w", encoding="utf-8") as f:
    f.writelines(p)  # one line per table row, the download host chokes on very long lines


# ---------------------------------------------------------------- verify
def cmd_verify(a):
  client = Client(GBIF_API)
  todo = []
  for r in read_tsv(a.diff):
    if len(todo) < a.top or (a.id_rows and r["id_flag"] == "1"):
      todo.append(r)
  log(f"re-asking api.gbif.org for {len(todo)} rows")

  def work(r):
    params = match_params(r) + [("checklistKey", COL_CHECKLIST)]
    resp, err = client.get("/v2/species/match?" + urllib.parse.urlencode(params))
    m = parse_match(resp)
    return "\t".join([r["row"]] + [m[f] for f in MATCH_FIELDS] + [clean(err or "")])

  with open(a.out, "w", encoding="utf-8") as out, concurrent.futures.ThreadPoolExecutor(a.threads) as ex:
    out.write("\t".join(["row"] + MATCH_FIELDS + ["error"]) + "\n")
    for i, line in enumerate(ex.map(work, todo), start=1):
      out.write(line + "\n")
      if i % 500 == 0:
        log(f"{i} verified")
  log(f"wrote {a.out}")


def main():
  ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
  sub = ap.add_subparsers(dest="cmd", required=True)
  m = sub.add_parser("match", help="match the verbatim names of a GBIF download against a CLB matching server")
  m.add_argument("input", help="GBIF SQL_TSV_ZIP download, or its unzipped TSV")
  m.add_argument("--out", default="proposed.tsv")
  m.add_argument("--base", default="http://localhost:8080", help="CLB matching server")
  m.add_argument("--gbif", help="GBIF matching-ws to ask live as well, e.g. https://api.gbif.org or a local container")
  m.add_argument("--min-occurrences", type=int, default=1)
  m.add_argument("--threads", type=int, default=16)
  m.add_argument("--limit", type=int, help="stop after this many input rows, for trials")
  m.add_argument("--checklist-key", default=COL_CHECKLIST, help="sent as checklistKey, the CLB server rejects any other")
  r = sub.add_parser("report", help="categorise a match result and write diff.tsv and report.html")
  r.add_argument("proposed")
  r.add_argument("--out", default="report")
  r.add_argument("--base", default="http://localhost:8080", help="CLB matching server, for the debug links")
  r.add_argument("--verify", help="verify.tsv to replace the stored GBIF interpretation by live answers")
  r.add_argument("--top", type=int, default=50, help="inputs listed per category")
  v = sub.add_parser("verify", help="re-ask live api.gbif.org for the top differing rows, without ids")
  v.add_argument("diff", help="diff.tsv of a report, sorted by occurrences")
  v.add_argument("--out", default="verify.tsv")
  v.add_argument("--top", type=int, default=500)
  v.add_argument("--id-rows", action="store_true", help="also every row GBIF may have matched by id")
  v.add_argument("--threads", type=int, default=4)
  a = ap.parse_args()
  {"match": cmd_match, "report": cmd_report, "verify": cmd_verify}[a.cmd](a)


if __name__ == "__main__":
  main()
