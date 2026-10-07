#!/usr/bin/env python3
"""
Counts original species descriptions per year and taxGroup for one ChecklistBank dataset.

Reads the dataset's extended ColDP archive - names, authorship, references and the clb:taxGroup the server
assigns to every usage - and writes a wide TSV (year x taxGroup), an HTML report with one chart per group and
the same charts as SVG files.

  python3 -I species_descriptions.py 3LXR
  python3 -I species_descriptions.py 1027 --years 2000-2025 --infraspecific --out ./scarabs

Credentials come from $CLB_TOKEN, or $CLB_USER + $CLB_PASSWORD. They are needed to request a missing export,
to read private releases and - as an admin - to force a new export when the existing one predates the
clb:taxGroup column (2026-10-07). Without them only an existing export of the dataset's current import attempt
that has the column can be used. See SKILL.md next to this file for the counting rules.
"""
import argparse
import base64
import csv
import datetime
import html
import http.cookiejar
import io
import json
import math
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile
from collections import Counter, defaultdict

MIN_YEAR = 1750
THIS_YEAR = datetime.date.today().year
UA = "Mozilla/5.0 (CLB species-descriptions skill)"
ACTIVE = {"waiting", "blocked", "running"}

STATUSES_ALL = {"accepted", "provisionally accepted", "synonym", "ambiguous synonym"}
STATUSES_ACCEPTED = {"accepted", "provisionally accepted"}
# not a valid description of a name: nomen nudum & co, unpublished, a misapplication
BAD_NOM_STATUS = {"not established", "manuscript", "chresonym"}

csv.field_size_limit(2 ** 31 - 1)


def log(msg):
  print(msg, file=sys.stderr, flush=True)


# --------------------------------------------------------------------------------------------------
# API access
# --------------------------------------------------------------------------------------------------

class Api:
  def __init__(self, base, token=None):
    self.base = base.rstrip("/")
    self.token = token
    # the download host sits behind a cookie challenge: a cookie jar and a browser like UA pass it
    self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
    self.opener.addheaders = [("User-Agent", UA)]

  def _request(self, path, body=None, method=None, accept="application/json", auth=True):
    url = path if path.startswith("http") else self.base + path
    headers = {"Accept": accept}
    data = None
    if body is not None:
      data = json.dumps(body).encode("utf-8")
      headers["Content-Type"] = "application/json"
    if auth and self.token and url.startswith(self.base):
      headers["Authorization"] = "Bearer " + self.token
    return urllib.request.Request(url, data=data, method=method, headers=headers)

  def json(self, path, body=None, method=None):
    req = self._request(path, body, method)
    try:
      with self.opener.open(req, timeout=300) as r:
        return json.load(r)
    except urllib.error.HTTPError as e:
      detail = e.read().decode("utf-8", "replace")[:300]
      raise SystemExit(f"{req.get_method()} {req.full_url} failed with HTTP {e.code}: {detail}")

  def download(self, url, dest):
    tmp = dest + ".part"
    req = self._request(url, accept="application/zip", auth=False)
    with self.opener.open(req, timeout=600) as r, open(tmp, "wb") as out:
      total = int(r.headers.get("Content-Length") or 0)
      done, last = 0, time.time()
      while True:
        chunk = r.read(1 << 20)
        if not chunk:
          break
        out.write(chunk)
        done += len(chunk)
        if time.time() - last > 15:
          log(f"  {done >> 20} of {total >> 20} MB")
          last = time.time()
    with open(tmp, "rb") as f:
      if f.read(2) != b"PK":
        raise SystemExit(f"{url} did not return a zip archive (cookie challenge or error page?), see {tmp}")
    os.replace(tmp, dest)


def login(api_base):
  token = os.environ.get("CLB_TOKEN")
  if token:
    return token.strip()
  user, pwd = os.environ.get("CLB_USER"), os.environ.get("CLB_PASSWORD")
  if not (user and pwd):
    return None
  basic = base64.b64encode(f"{user}:{pwd}".encode()).decode()
  req = urllib.request.Request(api_base.rstrip("/") + "/user/login",
                               headers={"Authorization": "Basic " + basic, "User-Agent": UA})
  try:
    with urllib.request.urlopen(req, timeout=60) as r:
      return r.read().decode().strip().strip('"')
  except urllib.error.HTTPError as e:
    raise SystemExit(f"Login as {user} failed with HTTP {e.code}")


EXTENDED = {"format": "COLDP", "extended": True, "synonyms": True}
TAXGROUP_COLUMN = "clb:taxGroup"


class RemoteFile(io.RawIOBase):
  """
  A read only, seekable view of a remote file through HTTP range requests. zipfile only needs the central
  directory and the first compressed block of one member to read a header line, so a stale multi GB archive
  can be checked with a handful of small requests instead of being downloaded.
  """
  CHUNK = 1 << 18

  def __init__(self, api, url):
    self.api = api
    self.buf, self.buf_start, self.pos = b"", 0, 0
    self.buf, self.url, self.size = self._fetch(url, 0, 0)

  def _fetch(self, url, start, end):
    req = urllib.request.Request(url, headers={"Range": f"bytes={start}-{end}", "Accept": "application/zip"})
    with self.api.opener.open(req, timeout=120) as r:
      if r.status != 206:
        raise OSError(f"{url} answered HTTP {r.status} instead of a partial range")
      return r.read(), r.url, int(r.headers["Content-Range"].rsplit("/", 1)[1])

  def readable(self):
    return True

  def seekable(self):
    return True

  def tell(self):
    return self.pos

  def seek(self, offset, whence=io.SEEK_SET):
    self.pos = offset if whence == io.SEEK_SET else self.pos + offset if whence == io.SEEK_CUR else self.size + offset
    return self.pos

  def readinto(self, b):
    want = min(len(b), self.size - self.pos)
    if want <= 0:
      return 0
    if not (self.buf_start <= self.pos and self.pos + want <= self.buf_start + len(self.buf)):
      self.buf, _, _ = self._fetch(self.url, self.pos, min(self.pos + max(want, self.CHUNK), self.size) - 1)
      self.buf_start = self.pos
    i = self.pos - self.buf_start
    b[:want] = self.buf[i:i + want]
    self.pos += want
    return want


def has_taxgroup_column(source):
  """True if NameUsage.tsv of the archive - a local path or a RemoteFile - has the clb:taxGroup column."""
  with zipfile.ZipFile(source) as zf:
    if "NameUsage.tsv" not in zf.namelist():
      return False
    with zf.open("NameUsage.tsv") as f:
      header = f.readline().decode("utf-8").rstrip("\r\n").split("\t")
  return TAXGROUP_COLUMN in header


def same_request(have):
  """True if an existing export was made from exactly the plain extended ColDP request we would submit."""
  return (bool(have.get("extended")) and not have.get("classification") and have.get("synonyms", True)
          and not have.get("root") and not have.get("minRank") and have.get("extinct") is None
          and not have.get("excel") and (have.get("tabFormat") or "tsv").lower() == "tsv")


def await_export(api, info):
  """Waits for an export job. Returns (job key, download url, created, fresh) - fresh if it was built just now."""
  job = info["key"]
  fresh = (info.get("status") or "").lower() in ACTIVE
  while (info.get("status") or "").lower() in ACTIVE:
    step = f" ({info['step']})" if info.get("step") else ""
    log(f"Export {job} is {info.get('status')}{step}, waiting ...")
    time.sleep(20)
    info = api.json(f"/job/{job}")
  if (info.get("status") or "").lower() != "finished":
    raise SystemExit(f"Export {job} ended {info.get('status')}: {info.get('error')}")
  return job, f"{api.base}/job/{job}", (info.get("created") or "")[:16], fresh


def find_export(api, ds):
  """The extended ColDP export of the dataset's current import attempt, requesting one if there is none and we can."""
  key, attempt = ds["key"], ds.get("attempt")
  if api.token:
    # the server hands back the existing export of the current import attempt, or starts building a new one
    return await_export(api, api.json(f"/dataset/{key}/export", body=EXTENDED, method="POST"))
  params = {"datasetKey": key, "format": "COLDP", "extended": "true", "status": "FINISHED", "limit": 100}
  hits = [e for e in api.json("/export?" + urllib.parse.urlencode(params)).get("result", [])
          if same_request(e.get("request", {})) and e.get("attempt") == attempt]
  if not hits:
    raise SystemExit(f"No extended ColDP export of import attempt {attempt} exists for dataset {key}. "
                     f"Set CLB_TOKEN or CLB_USER/CLB_PASSWORD so one can be requested, or pass --coldp-zip.")
  hit = max(hits, key=lambda e: e.get("created") or "")
  return hit["key"], hit.get("download") or f"{api.base}/job/{hit['key']}", (hit.get("created") or "")[:16], False


def server_version(api):
  try:
    with api.opener.open(api._request("/version", accept="text/plain"), timeout=30) as r:
      return r.read().decode().strip()
  except (urllib.error.URLError, OSError):
    return "an unknown version"


def archive_state(api, job, url, work):
  """'ok' if the export's archive has the clb:taxGroup column, 'stale' if it predates it, 'missing' if its file is gone."""
  cached = os.path.join(work, f"{job}.zip")
  if os.path.exists(cached):
    return "ok" if has_taxgroup_column(cached) else "stale"
  try:
    return "ok" if has_taxgroup_column(RemoteFile(api, url)) else "stale"
  except urllib.error.HTTPError as e:
    if e.code in (404, 410):
      return "missing"
    raise SystemExit(f"Reading export {job} failed with HTTP {e.code}")
  except OSError as e:
    log(f"Cannot peek into export {job} ({e}), downloading it whole")
    download_export(api, job, url, work)
    return "ok" if has_taxgroup_column(cached) else "stale"


PROBLEM = {
  "stale": f"predates the {TAXGROUP_COLUMN} column",
  "missing": "is recorded, but its archive is gone from the download host",
}


def obtain_export(api, ds, work):
  """
  Returns (local zip, description) of an extended ColDP export of the current import attempt that carries
  clb:taxGroup. An archive built before the column existed, or one whose file has disappeared, is replaced by a
  forced new export, which only an admin may request - the server silently drops force for anyone else.
  """
  job, url, created, fresh = find_export(api, ds)
  state = archive_state(api, job, url, work)
  if state != "ok":
    if fresh:
      raise SystemExit(f"Export {job} was built just now and {PROBLEM[state]}: {api.base} runs {server_version(api)}, "
                       f"the column needs backend commit 0b42ef87a (2026-10-07) or later.")
    if not api.token:
      raise SystemExit(f"Extended ColDP export {job} of {created} {PROBLEM[state]}. A new one has to be forced, which "
                       f"needs an admin login in CLB_TOKEN or CLB_USER/CLB_PASSWORD.")
    log(f"Extended ColDP export {job} of {created} {PROBLEM[state]}, forcing a new one ...")
    info = api.json(f"/dataset/{ds['key']}/export", body={**EXTENDED, "force": True}, method="POST")
    if info["key"] == job:
      raise SystemExit(f"Extended ColDP export {job} of {created} {PROBLEM[state]}, and the server ignored force and "
                       f"returned it again: forcing a new export needs an admin login.")
    job, url, created, _ = await_export(api, info)
    state = archive_state(api, job, url, work)
    if state != "ok":
      raise SystemExit(f"The forced export {job} {PROBLEM[state]} too: {api.base} runs {server_version(api)}, "
                       f"the column needs backend commit 0b42ef87a (2026-10-07) or later.")
  return download_export(api, job, url, work), f"job {job}, created {created}"


def download_export(api, job, url, work):
  dest = os.path.join(work, f"{job}.zip")
  if os.path.exists(dest):
    log(f"Extended ColDP export {job} already downloaded: {dest}")
  else:
    log(f"Downloading extended ColDP export {job} ...")
    api.download(url, dest)
  return dest


# --------------------------------------------------------------------------------------------------
# reading the archives
# --------------------------------------------------------------------------------------------------

def tsv_rows(zf, member):
  """Returns a column index keyed by the term name without its col:/clb: prefix, and a reader over the rows."""
  f = io.TextIOWrapper(zf.open(member), encoding="utf-8", newline="")
  reader = csv.reader(f, delimiter="\t", quoting=csv.QUOTE_NONE)
  header = next(reader)
  idx = {h.split(":", 1)[-1]: i for i, h in enumerate(header)}
  return idx, reader


def getter(idx):
  def get(row, term):
    i = idx.get(term)
    return row[i].strip() if i is not None and i < len(row) else ""
  return get


YEAR_ANY = re.compile(r"(?<!\d)(1[5-9]\d\d|20\d\d)(?!\d)")
YEAR_PAREN = re.compile(r"\((1[5-9]\d\d|20\d\d)[a-z]?\)")


def first_year(s):
  m = YEAR_ANY.search(s or "")
  return int(m.group(1)) if m else None


def citation_year(s):
  """Botanical citations put the year last, often in brackets: 'Sp. Pl. 2: 1000 (1753)', 'Pinetum 180. 1858'."""
  if not s:
    return None
  paren = YEAR_PAREN.findall(s)
  if paren:
    return int(paren[-1])
  years = YEAR_ANY.findall(s)
  return int(years[-1]) if years else None


def infraspecific_ranks(api):
  # cultivated plant ranks and strains are no nomenclatural descriptions
  return {r["name"] for r in api.json("/vocab/rank")
          if r.get("infraspecific") and r.get("code") != "cultivars" and r["name"] != "strain"}


# --------------------------------------------------------------------------------------------------
# counting
# --------------------------------------------------------------------------------------------------

def read_relations(zf):
  """Usages that are a recombination or a replacement name (nomen novum) according to NameRelation.tsv."""
  combos, replacements = set(), set()
  if "NameRelation.tsv" not in zf.namelist():
    return combos, replacements
  idx, rows = tsv_rows(zf, "NameRelation.tsv")
  g = getter(idx)
  for row in rows:
    rel, nid, related = g(row, "type").lower().replace("_", " "), g(row, "nameID"), g(row, "relatedNameID")
    if rel == "basionym" and nid != related:
      combos.add(nid)
    elif rel == "replacement name":
      replacements.add(nid)
  return combos, replacements


def read_names(zf, ranks, statuses, skipped, considered):
  """
  Pass 1: original names of the requested ranks.
  Returns usage id -> (code, authorship year, published year, ref id, rank, taxGroup).
  """
  if "NameUsage.tsv" not in zf.namelist():
    raise SystemExit("The extended ColDP archive has no NameUsage.tsv")
  combos, replacements = read_relations(zf)
  idx, rows = tsv_rows(zf, "NameUsage.tsv")
  if "taxGroup" not in idx:
    raise SystemExit(f"NameUsage.tsv has no {TAXGROUP_COLUMN} column: the archive predates it")
  g = getter(idx)
  cands, seen = {}, set()
  for n, row in enumerate(rows, 1):
    if n % 1_000_000 == 0:
      log(f"  {n:,} name usages read")
    rank = g(row, "rank")
    if rank not in ranks:
      continue
    considered[0] += 1
    status = g(row, "status")
    if status not in statuses:
      skipped[f"status {status or 'missing'}"] += 1
      continue
    if g(row, "nameStatus") in BAD_NOM_STATUS:
      skipped[f"nomenclatural status {g(row, 'nameStatus')}"] += 1
      continue
    uid, authorship = g(row, "ID"), g(row, "authorship")
    if g(row, "basionymAuthorship") or g(row, "basionymExAuthorship") or g(row, "basionymAuthorshipYear"):
      skipped["recombination: basionym authorship"] += 1
      continue
    if authorship.startswith("("):
      skipped["recombination: bracketed authorship"] += 1
      continue
    bid = g(row, "basionymID")
    if bid and bid != uid or uid in combos:
      skipped["recombination: basionym relation to another name"] += 1
      continue
    if uid in replacements:
      skipped["replacement name (nomen novum)"] += 1
      continue
    if not authorship and not g(row, "combinationAuthorship"):
      skipped["no authorship: original or recombination cannot be told"] += 1
      continue
    key = hash((g(row, "scientificName"), authorship, rank))  # a hash keeps millions of names small
    if key in seen:
      skipped["same name counted already (pro parte, duplicate)"] += 1
      continue
    seen.add(key)
    cands[uid] = (sys.intern(g(row, "code")), first_year(g(row, "combinationAuthorshipYear")),
                  first_year(g(row, "publishedInYear")), g(row, "nameReferenceID") or None, sys.intern(rank),
                  sys.intern(g(row, "taxGroup")) or None)
  return cands


def read_reference_years(zf, ref_ids):
  """Pass 3: (CSL issued year, citation year) per reference."""
  if "Reference.tsv" not in zf.namelist() or not ref_ids:
    return {}
  idx, rows = tsv_rows(zf, "Reference.tsv")
  g = getter(idx)
  years = {}
  for row in rows:
    rid = g(row, "ID")
    if rid in ref_ids:
      years[rid] = (first_year(g(row, "issued")), citation_year(g(row, "citation")))
  return years


def botanical(code, group, vocab):
  if code:
    return code == "botanical"
  codes = vocab.get(group, {}).get("codes") or []
  return codes == ["botanical"]


def resolve_year(cand, group, refs, vocab):
  """Botany dates a name by its publication, zoology (and anything else) by the year in its authorship."""
  code, auth_year, pub_year, ref_id = cand[:4]
  issued, cited = refs.get(ref_id, (None, None))
  bot = botanical(code, group, vocab)
  if bot:
    order = (("publishedInYear", pub_year), ("reference issued", issued), ("reference citation", cited),
             ("authorship", auth_year))
  else:
    order = (("authorship", auth_year), ("publishedInYear", pub_year), ("reference issued", issued),
             ("reference citation", cited))
  for source, year in order:
    if year:
      return year, ("botanical" if bot else "other codes") + ": " + source
  return None, None


def ancestors(group, vocab):
  """
  The primary parents up to the root. Algae and pseudofungi have two parents each; following only the primary
  one keeps the groups a tree, so a pseudofungus is not also counted as a plant via algae.
  """
  out, g = [], vocab.get(group, {}).get("primaryParent")
  while g and g not in out:
    out.append(g)
    g = vocab.get(g, {}).get("primaryParent")
  return out


def count(cands, refs, vocab, year_range, skipped, sources, assigned, by_rank):
  """An explicit year range replaces the default MIN_YEAR - THIS_YEAR window that drops bad data."""
  first = year_range[0] if year_range[0] is not None else MIN_YEAR
  last = year_range[1] if year_range[1] is not None else THIS_YEAR
  per_name = []
  for uid, cand in cands.items():
    group = cand[5]
    year, source = resolve_year(cand, group, refs, vocab)
    if year is None:
      skipped["no year"] += 1
      continue
    if year < first or year > last:
      skipped[f"year before {first}" if year < first else f"year after {last}"] += 1
      continue
    per_name.append((year, group, cand[4]))
    sources[source] += 1
  if not per_name:
    raise SystemExit("No original descriptions with a usable year found")
  # by default the table spans the years found, an explicit range is kept as given
  lo = year_range[0] if year_range[0] is not None else min(y for y, _, _ in per_name)
  hi = year_range[1] if year_range[1] is not None else max(y for y, _, _ in per_name)
  counts = defaultdict(Counter)
  for year, group, rank in per_name:
    counts["total"][year] += 1
    assigned[group or "unknown"] += 1
    by_rank[rank] += 1
    if group:
      counts[group][year] += 1
      for a in ancestors(group, vocab):
        counts[a][year] += 1
    else:
      counts["unknown"][year] += 1
  return lo, hi, counts


# --------------------------------------------------------------------------------------------------
# output
# --------------------------------------------------------------------------------------------------

def panel_columns(columns, series, vocab):
  """All columns but the groups whose series equals that of one of their subgroups - same chart twice."""
  def parent(g):
    return vocab.get(g, {}).get("primaryParent") or ("total" if g in vocab else None)
  out = []
  for c in columns:
    children = [g for g in columns if parent(g) == c]
    if not any(series[g] == series[c] for g in children):
      out.append(c)
  return out


def label(col):
  if col == "total":
    return "All groups"
  if col == "unknown":
    return "No taxGroup"
  if col.startswith("other"):
    return "Other " + col[5:]
  return col[:1].upper() + col[1:]


def lineage(col, vocab):
  path, g = [], vocab.get(col, {}).get("primaryParent")
  while g:
    path.insert(0, label(g))
    g = vocab.get(g, {}).get("primaryParent")
  return " › ".join(path)


def nice_ceiling(v):
  """A round axis maximum whose half, the middle gridline, is a whole number too."""
  if v <= 10:
    return max(2, v + v % 2)
  exp = 10 ** math.floor(math.log10(v))
  return next(m * exp for m in (1, 2, 3, 4, 5, 6, 8, 10) if m * exp >= v)


def x_ticks(lo, hi):
  span = max(hi - lo, 1)
  step = next(s for s in (1, 2, 5, 10, 20, 25, 50, 100, 200) if span / s <= 6)
  first = lo if lo % step == 0 else lo + step - lo % step
  return list(range(first, hi + 1, step))


W, H = 360, 168
PAD_L, PAD_R, PAD_T, PAD_B = 46, 14, 14, 24

SVG_LIGHT_STYLE = """
.bg{fill:#fcfcfb}.gl{stroke:#e1e0d9;stroke-width:1}.axis{stroke:#c3c2b7;stroke-width:1}
.tick{fill:#898781;font:11px system-ui,-apple-system,"Segoe UI",sans-serif;font-variant-numeric:tabular-nums}
.line{fill:none;stroke:#2a78d6;stroke-width:2;stroke-linejoin:round;stroke-linecap:round}
.area{fill:#2a78d6;fill-opacity:.1;stroke:none}.peak{fill:#2a78d6;stroke:#fcfcfb;stroke-width:2}
.title{fill:#0b0b0b;font:600 13px system-ui,-apple-system,"Segoe UI",sans-serif}
"""


def peak(values):
  return max(range(len(values)), key=lambda i: values[i])


def svg_chart(col, years, values, standalone=False, title=None, subtitle=None):
  n = len(years)
  vmax = nice_ceiling(max(values))
  top = PAD_T + (34 if standalone else 0)
  pw, ph = W - PAD_L - PAD_R, H - top - PAD_B

  def x(i):
    return PAD_L + (pw * i / (n - 1) if n > 1 else pw / 2)

  def y(v):
    return top + ph - ph * v / vmax

  out = []
  if standalone:
    out.append(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" width="{W * 2}" height="{H * 2}">')
    out.append(f"<style>{SVG_LIGHT_STYLE}</style>")
    out.append(f'<rect class="bg" width="{W}" height="{H}"/>')
    out.append(f'<text class="title" x="12" y="18">{html.escape(title or label(col))}</text>')
    out.append(f'<text class="tick" x="12" y="34">{html.escape(subtitle or "")}</text>')
  else:
    out.append(f'<svg viewBox="0 0 {W} {H}" class="chart" data-col="{html.escape(col)}" tabindex="0" role="img" '
               f'aria-label="{html.escape(title or label(col))}">')
  for v in (0, vmax / 2, vmax):
    yy = y(v)
    out.append(f'<line class="gl" x1="{PAD_L}" x2="{W - PAD_R}" y1="{yy:.1f}" y2="{yy:.1f}"/>')
    txt = f"{v:,.0f}" if v >= 1 or v == 0 else f"{v:g}"
    out.append(f'<text class="tick" x="{PAD_L - 6}" y="{yy + 4:.1f}" text-anchor="end">{txt}</text>')
  out.append(f'<line class="axis" x1="{PAD_L}" x2="{W - PAD_R}" y1="{y(0):.1f}" y2="{y(0):.1f}"/>')
  for t in x_ticks(years[0], years[-1]):
    i = t - years[0]
    out.append(f'<text class="tick" x="{x(i):.1f}" y="{H - 6}" text-anchor="middle">{t}</text>')
  pts = " L".join(f"{x(i):.1f},{y(v):.1f}" for i, v in enumerate(values))
  out.append(f'<path class="area" d="M{x(0):.1f},{y(0):.1f} L{pts} L{x(n - 1):.1f},{y(0):.1f} Z"/>')
  out.append(f'<path class="line" d="M{pts}"/>')
  # the peak gets a dot here, its value is labelled in the panel header where it cannot collide with the line
  pi = peak(values)
  out.append(f'<circle class="peak" cx="{x(pi):.1f}" cy="{y(values[pi]):.1f}" r="4"/>')
  if not standalone:
    out.append(f'<line class="xhair" x1="0" x2="0" y1="{top}" y2="{y(0):.1f}" visibility="hidden"/>')
    out.append('<circle class="hover-dot" r="4" visibility="hidden"/>')
    out.append(f'<g class="geom" data-l="{PAD_L}" data-w="{pw}" data-t="{top}" data-h="{ph}" data-max="{vmax}"></g>')
  out.append("</svg>")
  return "".join(out)


HTML_HEAD = """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Species description rates</title>
<style>
:root{color-scheme:light;--page:#f9f9f7;--surface:#fcfcfb;--ink:#0b0b0b;--ink-2:#52514e;--muted:#898781;
--grid:#e1e0d9;--axis:#c3c2b7;--series:#2a78d6;--ring:rgba(11,11,11,.10)}
@media (prefers-color-scheme:dark){:root:not([data-theme="light"]){color-scheme:dark;--page:#0d0d0d;--surface:#1a1a19;
--ink:#fff;--ink-2:#c3c2b7;--muted:#898781;--grid:#2c2c2a;--axis:#383835;--series:#3987e5;--ring:rgba(255,255,255,.10)}}
:root[data-theme="dark"]{color-scheme:dark;--page:#0d0d0d;--surface:#1a1a19;--ink:#fff;--ink-2:#c3c2b7;--muted:#898781;
--grid:#2c2c2a;--axis:#383835;--series:#3987e5;--ring:rgba(255,255,255,.10)}
*{box-sizing:border-box}
body{margin:0;background:var(--page);color:var(--ink);font:15px/1.5 system-ui,-apple-system,"Segoe UI",sans-serif}
main{max-width:1240px;margin:0 auto;padding:24px 16px 48px}
h1{font-size:24px;margin:0 0 4px}h2{font-size:17px;margin:32px 0 8px}
.sub{color:var(--ink-2);margin:0 0 16px}
.facts{display:flex;flex-wrap:wrap;gap:8px 24px;margin:16px 0;padding:0;list-style:none}
.facts li{color:var(--ink-2)}.facts b{color:var(--ink);font-weight:600}
.panels{display:grid;grid-template-columns:repeat(auto-fill,minmax(300px,1fr));gap:12px}
.panel{background:var(--surface);border:1px solid var(--ring);border-radius:10px;padding:12px 12px 6px}
.panel h3{font-size:14px;margin:0;display:flex;justify-content:space-between;gap:8px}
.panel h3 span{color:var(--ink-2);font-weight:400;font-variant-numeric:tabular-nums}
.panel p{margin:0;color:var(--muted);font-size:12px;min-height:18px;display:flex;justify-content:space-between;gap:8px}
.panel p span:last-child{color:var(--ink-2);white-space:nowrap}
svg.chart{display:block;width:100%;height:auto;outline:none}
svg.chart:focus-visible{outline:2px solid var(--series);outline-offset:2px;border-radius:4px}
.chart .gl{stroke:var(--grid);stroke-width:1}.chart .axis{stroke:var(--axis);stroke-width:1}
.chart .tick{fill:var(--muted);font-size:11px;font-variant-numeric:tabular-nums}
.chart .line{fill:none;stroke:var(--series);stroke-width:2;stroke-linejoin:round;stroke-linecap:round}
.chart .area{fill:var(--series);fill-opacity:.1}
.chart .peak,.chart .hover-dot{fill:var(--series);stroke:var(--surface);stroke-width:2}
.chart .xhair{stroke:var(--axis);stroke-width:1}
#tip{position:fixed;pointer-events:none;background:var(--surface);border:1px solid var(--ring);border-radius:8px;
padding:6px 10px;font-size:13px;box-shadow:0 4px 16px rgba(0,0,0,.12);display:none;z-index:10}
#tip b{display:block;font-size:15px;font-variant-numeric:tabular-nums}#tip span{color:var(--ink-2)}
table{border-collapse:collapse;font-size:13px;font-variant-numeric:tabular-nums}
th,td{padding:3px 8px;text-align:right;border-bottom:1px solid var(--grid);white-space:nowrap}
th:first-child,td:first-child{text-align:left;position:sticky;left:0;background:var(--surface)}
thead th{position:sticky;top:0;background:var(--surface)}
.table-wrap{max-height:70vh;overflow:auto;background:var(--surface);border:1px solid var(--ring);border-radius:10px}
table.kv td:first-child{white-space:normal;position:static;background:none;text-align:left}
details summary{cursor:pointer;color:var(--ink-2);margin:8px 0}
ul.method li{margin:2px 0}
</style>
</head>
<body>
<main>
"""

HTML_SCRIPT = """
<div id="tip" role="status"><b></b><span></span></div>
<script>
(() => {
  const data = JSON.parse(document.getElementById('series').textContent);
  const tip = document.getElementById('tip');
  const fmt = new Intl.NumberFormat();
  document.querySelectorAll('svg.chart').forEach(svg => {
    const vals = data.series[svg.dataset.col];
    const g = svg.querySelector('.geom').dataset;
    const L = +g.l, Wd = +g.w, T = +g.t, Hd = +g.h, M = +g.max, n = data.years.length;
    const xh = svg.querySelector('.xhair'), dot = svg.querySelector('.hover-dot');
    const vb = svg.viewBox.baseVal;
    let cur = -1;
    const xOf = i => L + (n > 1 ? Wd * i / (n - 1) : Wd / 2);
    function show(i, cx, cy) {
      cur = Math.max(0, Math.min(n - 1, i));
      const x = xOf(cur), y = T + Hd - Hd * vals[cur] / M;
      xh.setAttribute('x1', x); xh.setAttribute('x2', x); xh.setAttribute('visibility', 'visible');
      dot.setAttribute('cx', x); dot.setAttribute('cy', y); dot.setAttribute('visibility', 'visible');
      tip.querySelector('b').textContent = fmt.format(vals[cur]);
      tip.querySelector('span').textContent = data.years[cur] + ' · ' + svg.getAttribute('aria-label');
      tip.style.display = 'block';
      const r = svg.getBoundingClientRect();
      const px = cx ?? r.left + x / vb.width * r.width, py = cy ?? r.top + y / vb.height * r.height;
      const tw = tip.offsetWidth;
      tip.style.left = Math.min(window.innerWidth - tw - 8, px + 14) + 'px';
      tip.style.top = Math.max(8, py - 48) + 'px';
    }
    function hide() { xh.setAttribute('visibility', 'hidden'); dot.setAttribute('visibility', 'hidden'); tip.style.display = 'none'; }
    svg.addEventListener('pointermove', e => {
      const r = svg.getBoundingClientRect();
      const vx = (e.clientX - r.left) / r.width * vb.width;
      show(Math.round((vx - L) / Wd * (n - 1)), e.clientX, e.clientY);
    });
    svg.addEventListener('pointerleave', hide);
    svg.addEventListener('blur', hide);
    svg.addEventListener('focus', () => show(cur < 0 ? n - 1 : cur));
    svg.addEventListener('keydown', e => {
      const step = e.shiftKey ? 10 : 1;
      if (e.key === 'ArrowLeft') { show(cur - step); e.preventDefault(); }
      if (e.key === 'ArrowRight') { show(cur + step); e.preventDefault(); }
    });
  });
})();
</script>
</main>
</body>
</html>
"""


def data_version(ds):
  """Version and import date: a stale snapshot explains thin recent years better than publication lag does."""
  parts = [f"version {ds['version']}" if ds.get("version") else None,
           f"imported {ds['imported'][:10]}" if ds.get("imported") else None,
           f"issued {ds['issued']}" if ds.get("issued") else None]
  return ", ".join(p for p in parts if p) or "version unknown"


def write_outputs(out, ds, meta, years, columns, series, vocab, skipped, sources):
  os.makedirs(os.path.join(out, "svg"), exist_ok=True)
  key = ds["key"]
  tsv = os.path.join(out, f"species-descriptions-{key}.tsv")
  with open(tsv, "w", encoding="utf-8") as f:
    f.write("year\t" + "\t".join(columns) + "\n")
    for i, y in enumerate(years):
      f.write(f"{y}\t" + "\t".join(str(series[c][i]) for c in columns) + "\n")

  name = ds.get("alias") or ds.get("title") or str(key)
  panels = panel_columns(columns, series, vocab)
  for c in panels:
    pi = peak(series[c])
    short = name if len(name) <= 36 else name[:35] + "…"
    sub = f"{sum(series[c]):,} original descriptions, peak {series[c][pi]:,} in {years[pi]}"
    with open(os.path.join(out, "svg", f"{c}.svg"), "w", encoding="utf-8") as f:
      f.write(svg_chart(c, years, series[c], standalone=True, title=f"{label(c)} · {short}", subtitle=sub))

  e = html.escape
  parts = [HTML_HEAD]
  parts.append(f"<h1>Original species descriptions per year</h1>")
  parts.append(f'<p class="sub">{e(name)} · {e(ds.get("title") or "")} · dataset {key}'
               f'{" attempt " + str(ds["attempt"]) if ds.get("attempt") else ""} · {e(data_version(ds))}</p>')
  total = sum(series["total"])
  parts.append('<ul class="facts">')
  parts.append(f"<li><b>{total:,}</b> original descriptions</li>")
  parts.append(f"<li><b>{years[0]}–{years[-1]}</b></li>")
  parts.append(f"<li><b>{e(meta['ranks'])}</b></li>")
  parts.append(f"<li><b>{e(meta['statuses'])}</b></li>")
  parts.append("</ul>")
  parts.append('<p class="sub">Each panel has its own y scale. A group includes all its subgroups, so the panels '
               "do not add up to the total. Algae count under plants and pseudofungi under fungi, their primary "
               "parent groups. A group whose names all fall into one subgroup gets no panel of its own. "
               "Hover or focus a chart and use the arrow keys to read single years.</p>")
  for note in meta["notes"]:
    parts.append(f'<p class="sub">{e(note)}</p>')
  parts.append('<div class="panels">')
  for c in panels:
    sub = lineage(c, vocab) if c not in ("total", "unknown") else ""
    pi = peak(series[c])
    parts.append(f'<section class="panel"><h3>{e(label(c))}<span>{sum(series[c]):,}</span></h3>'
                 f'<p><span>{e(sub)}</span><span>peak {series[c][pi]:,} in {years[pi]}</span></p>')
    parts.append(svg_chart(c, years, series[c], title=label(c)))
    parts.append("</section>")
  parts.append("</div>")

  parts.append("<h2>How names were counted</h2><ul class=\"method\">")
  for line in meta["method"]:
    parts.append(f"<li>{e(line)}</li>")
  parts.append("</ul>")
  parts.append('<h2>Names left out</h2>')
  parts.append(f'<p class="sub">Of all {meta["considered"]:,} names of the requested ranks in the dataset, in any year.</p>')
  parts.append('<table class="kv"><tbody>')
  for reason, n in sorted(skipped.items(), key=lambda kv: -kv[1]):
    parts.append(f"<tr><td>{e(reason)}</td><td>{n:,}</td></tr>")
  parts.append("</tbody></table>")
  parts.append('<h2>Where the years came from</h2><table class="kv"><tbody>')
  for source, n in sorted(sources.items(), key=lambda kv: -kv[1]):
    parts.append(f"<tr><td>{e(source)}</td><td>{n:,}</td></tr>")
  parts.append("</tbody></table>")

  parts.append("<h2>Table</h2><details><summary>Show all counts per year and group</summary><div class=\"table-wrap\"><table>")
  parts.append("<thead><tr><th>year</th>" + "".join(f"<th>{e(label(c))}</th>" for c in columns) + "</tr></thead><tbody>")
  for i, y in enumerate(years):
    parts.append(f"<tr><td>{y}</td>" + "".join(f"<td>{series[c][i]:,}</td>" for c in columns) + "</tr>")
  parts.append("</tbody></table></div></details>")
  parts.append(f'<p class="sub">Sources: {e(meta["exports"])}. Generated {datetime.date.today().isoformat()}.</p>')
  payload = json.dumps({"years": years, "series": {c: series[c] for c in columns}}, separators=(",", ":"))
  payload = payload.replace("</", "<\\/")
  parts.append(f'<script type="application/json" id="series">{payload}</script>')
  parts.append(HTML_SCRIPT)
  report = os.path.join(out, f"species-descriptions-{key}.html")
  with open(report, "w", encoding="utf-8") as f:
    f.write("\n".join(parts))
  return tsv, report


# --------------------------------------------------------------------------------------------------

def parse_years(s):
  if not s:
    return (None, None)
  m = re.fullmatch(r"(\d{4})?(-)?(\d{4})?", s.replace(" ", ""))
  if not m or not (m.group(1) or m.group(3)) or (m.group(3) and not m.group(2)):
    raise SystemExit(f"--years must look like 2000-2025, 2000-, -1900 or 1999, not {s}")
  lo = int(m.group(1)) if m.group(1) else None
  hi = int(m.group(3)) if m.group(3) else (None if m.group(2) else lo)
  if lo and hi and lo > hi:
    raise SystemExit(f"--years {s} starts after it ends")
  return (lo, hi)


def main():
  ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
  ap.add_argument("dataset", help="dataset key or magic key, e.g. 3LXR, 3LR, COL26.9XR, 1027")
  ap.add_argument("--years", help=f"year range, e.g. 2000-2025 (default: all years found within {MIN_YEAR}-{THIS_YEAR})")
  ap.add_argument("--infraspecific", action="store_true", help="also count infraspecific names (subspecies, varieties, forms ...)")
  ap.add_argument("--accepted-only", action="store_true", help="count accepted names only, not synonyms")
  ap.add_argument("--out", help="output directory (default ./species-descriptions-<key>)")
  ap.add_argument("--work", default=os.path.expanduser("~/.cache/clb-exports"), help="download cache for export archives")
  ap.add_argument("--api", default=os.environ.get("CLB_API", "https://api.checklistbank.org"))
  ap.add_argument("--coldp-zip", help="use this local extended ColDP archive instead of downloading one")
  args = ap.parse_args()
  year_range = parse_years(args.years)

  api = Api(args.api, login(args.api))
  ds = api.json(f"/dataset/{urllib.parse.quote(args.dataset)}")
  log(f"Dataset {ds['key']} {ds.get('alias') or ''}: {ds.get('title')} (origin {ds.get('origin')}, attempt {ds.get('attempt')})")
  os.makedirs(args.work, exist_ok=True)
  out = args.out or f"species-descriptions-{ds['key']}"

  if args.coldp_zip:
    if not has_taxgroup_column(args.coldp_zip):
      raise SystemExit(f"{args.coldp_zip} has no {TAXGROUP_COLUMN} column in NameUsage.tsv")
    coldp, coldp_desc = args.coldp_zip, os.path.basename(args.coldp_zip)
  else:
    coldp, coldp_desc = obtain_export(api, ds, args.work)

  vocab = {g["name"]: g for g in api.json("/vocab/taxgroup")}
  ranks = {"species"} | (infraspecific_ranks(api) if args.infraspecific else set())
  statuses = STATUSES_ACCEPTED if args.accepted_only else STATUSES_ALL
  skipped, sources, considered = Counter(), Counter(), [0]

  log("Reading names ...")
  with zipfile.ZipFile(coldp) as zf:
    cands = read_names(zf, ranks, statuses, skipped, considered)
    log(f"  {len(cands):,} original names of the requested ranks")
    log("Reading references ...")
    refs = read_reference_years(zf, {c[3] for c in cands.values() if c[3]})

  assigned, by_rank = Counter(), Counter()
  lo, hi, counts = count(cands, refs, vocab, year_range, skipped, sources, assigned, by_rank)
  years = list(range(lo, hi + 1))
  ordered = [g for g in vocab if g in counts] + sorted(g for g in counts if g not in vocab and g not in ("total", "unknown"))
  columns = ["total"] + ordered + (["unknown"] if "unknown" in counts else [])
  series = {c: [counts[c][y] for y in years] for c in columns}

  rank_txt = "species and infraspecific names" if args.infraspecific else "species"
  meta = {
    "ranks": rank_txt,
    "considered": considered[0],
    "statuses": "accepted names only" if args.accepted_only else "accepted names and synonyms",
    "exports": f"extended ColDP export {coldp_desc}",
    "notes": [n for n in (
      "The most recent years are incomplete: new names take a while to reach the source databases"
      + (f", and {hi} is still running." if hi == THIS_YEAR else "."),
      f"{assigned['unknown']:,} names have no taxGroup." if assigned["unknown"] else None,
    ) if n],
    "method": [
      f"Ranks: {', '.join(sorted(ranks))}.",
      f"Taxonomic status: {', '.join(sorted(statuses))}. Misapplied and bare names are never counted.",
      "Only names in their original combination: no basionym authorship, no bracketed authorship and no basionym "
      "relation to another name. Replacement names (nomina nova) and names without any authorship are left out.",
      f"Nomenclatural status {', '.join(sorted(BAD_NOM_STATUS))} is not a valid description and is left out.",
      "A name with the same scientific name, authorship and rank is counted once.",
      "Botanical names are dated by their publication: the name's publishedInYear, then the year issued of its "
      "reference, then the last year in the reference citation, and only then the authorship year. All other "
      "names are dated by the year of their authorship first.",
      f"Years outside {lo}-{hi} are left out." if args.years else
      f"Years before {MIN_YEAR} or after {THIS_YEAR} are dropped as bad data; the table spans the years found.",
      "The taxGroup is the one ChecklistBank assigns to each usage from its classification.",
    ],
  }
  tsv, report = write_outputs(out, ds, meta, years, columns, series, vocab, skipped, sources)

  print(f"Dataset {ds['key']} {ds.get('alias') or ''} - {ds.get('title')} ({data_version(ds)})")
  print(f"{sum(series['total']):,} original descriptions of {rank_txt}, {lo}-{hi}, {meta['statuses']}")
  if len(by_rank) > 1:
    print("By rank: " + ", ".join(f"{r} {n:,}" for r, n in by_rank.most_common()))
  print("\nLargest groups as assigned (the TSV and charts include subgroups in their parents):")
  for g, n in assigned.most_common(12):
    print(f"  {label(g):<20} {n:>10,}")
  print(f"\nLeft out, of all {considered[0]:,} names of the requested ranks in the dataset, any year:")
  for reason, n in sorted(skipped.items(), key=lambda kv: -kv[1]):
    print(f"  {reason:<55} {n:>10,}")
  print("\nYear taken from:")
  for source, n in sorted(sources.items(), key=lambda kv: -kv[1]):
    print(f"  {source:<55} {n:>10,}")
  print(f"\nTSV:    {tsv}\nReport: {report}\nSVGs:   {os.path.join(out, 'svg')}/")


if __name__ == "__main__":
  main()
