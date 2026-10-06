#!/usr/bin/env python3
"""
Condenses a release job log into a digest that can be read whole - a Python port of
core/src/main/java/life/catalogue/release/review/ReleaseLogDigest.java, which the backend's
ReleaseReviewJob mounts for its agent. Keep the two in step.

A release log is several GB unpacked, nearly all of it one line per identifier. A logger's lines at one level
are quoted only while there are few of them; a logger that floods a level is summarised by its count, its
message patterns and its first and last lines. DEBUG lines are only counted. Credentials are redacted.

Usage:
  digest_log.py job.log.gz > digest.md
  curl -sS https://download.checklistbank.org/releases/3/632/job.log.gz | digest_log.py - > digest.md
"""
import gzip
import re
import sys
from collections import deque

QUOTE_LIMIT = 300
HEAD = 20
TAIL = 10
MAX_PATTERNS = 50
RARE = 3
MAX_TIMELINE = 5000
MAX_LINE_LENGTH = 500
MAX_STACK_LINES = 20
PATTERN_TOKENS = 8
LEVELS = ["ERROR", "WARN", "INFO", "DEBUG", "TRACE"]

# %d %-5level %-25logger{0} %6X{source} %msg, see JobAppender
LINE = re.compile(r"^(\d{4}-\d\d-\d\d \d\d:\d\d:\d\d),\d{3} (TRACE|DEBUG|INFO|WARN|ERROR) +(\S+) +(.*)$")
TIMESTAMP = re.compile(r"^\d{4}-\d\d-\d\d \d\d:\d\d:\d\d,\d{3} ")
CREDENTIAL = re.compile(r"(?i)\b((?:token|key|apikey|api_key|access_token|secret|password|pwd)=)[^&\s\"',;]+")
BEARER = re.compile(r"(?i)\b(Bearer )\S+")


def clean(line):
  s = BEARER.sub(r"\1REDACTED", CREDENTIAL.sub(r"\1REDACTED", line))
  return s[:MAX_LINE_LENGTH] + " [...]" if len(s) > MAX_LINE_LENGTH else s


def is_variable(tok):
  return any(c.isdigit() or c == "_" or (i > 0 and c.isupper()) for i, c in enumerate(tok))


def pattern(msg):
  """The first words of a message before any colon, words carrying an identifier or number replaced by #."""
  colon = msg.find(": ")
  head = msg[:colon] if colon > 0 else msg
  toks = head.split()[:PATTERN_TOKENS]
  return clean(" ".join("#" if is_variable(t) else t for t in toks))


def num(x):
  return f"{x:,}"


class Event:
  __slots__ = ("seq", "line", "stack", "stack_lines")

  def __init__(self, seq, line):
    self.seq = seq
    self.line = line
    self.stack = None
    self.stack_lines = 0

  def continued(self, line):
    if self.stack_lines < MAX_STACK_LINES:
      self.stack = (self.stack or []) + [clean(line)]
    self.stack_lines += 1

  def render(self):
    if self.stack is None:
      return self.line
    s = self.line + "\n" + "\n".join(self.stack)
    if self.stack_lines > MAX_STACK_LINES:
      s += f"\n\t... {self.stack_lines - MAX_STACK_LINES} more lines"
    return s


class Bucket:
  def __init__(self, level, logger):
    self.level = level
    self.logger = logger
    self.count = 0
    self.quoted = []
    self.tail = deque()
    self.patterns = {}
    self.examples = {}
    self.other_patterns = 0

  def flooded(self):
    return self.count > QUOTE_LIMIT

  def timeline(self):
    if not self.flooded():
      return list(self.quoted)
    return [e for p, n in self.patterns.items() if n <= RARE for e in self.examples[p]]

  def add(self, e, msg):
    self.count += 1
    p = pattern(msg)
    if p in self.patterns or len(self.patterns) < MAX_PATTERNS:
      self.patterns[p] = self.patterns.get(p, 0) + 1
      ex = self.examples.setdefault(p, [])
      if len(ex) < RARE:
        ex.append(e)
    else:
      self.other_patterns += 1
    if self.count <= QUOTE_LIMIT:
      self.quoted.append(e)
      return
    if self.count == QUOTE_LIMIT + 1:
      # just started flooding: keep the head quoted and move the latest lines to the tail
      for i in range(max(HEAD, len(self.quoted) - TAIL), len(self.quoted)):
        self.tail.append(self.quoted[i])
      del self.quoted[HEAD:]
    self.tail.append(e)
    if len(self.tail) > TAIL:
      self.tail.popleft()


def logger_of(line):
  start = 30
  while start < len(line) and line[start] == " ":
    start += 1
  end = line.find(" ", start)
  return line[start:] if end < 0 else line[start:end]


def digest(reader, source):
  buckets, debug = {}, {}
  seq, last, first, latest = 0, None, None, None
  for line in reader:
    line = line.rstrip("\n")
    # the DEBUG flood is by far the biggest part of a release log - count it without running the regex
    if line.startswith("DEBUG", 24) and TIMESTAMP.match(line):
      lg = logger_of(line)
      debug[lg] = debug.get(lg, 0) + 1
      seq += 1
      last = None
      continue
    m = LINE.match(line)
    if not m:
      # a stack trace or a message spanning several lines belongs to the line before
      if last is not None and line.strip():
        last.continued(line)
      continue
    if first is None:
      first = m.group(1)
    latest = m.group(1)
    last = Event(seq, clean(line))
    seq += 1
    key = (m.group(2), m.group(3))
    if key not in buckets:
      buckets[key] = Bucket(*key)
    buckets[key].add(last, m.group(4))

  out = ["# Release job log digest\n\n", f"Digest of `{source}`: {num(seq)} lines"]
  if first:
    out.append(f", from {first} to {latest}")
  out.append(".\n\n")
  out.append(f"DEBUG lines are counted but never quoted. A logger with more than {QUOTE_LIMIT} lines at one level is"
             f" summarised under *Summarised loggers* instead of being quoted in the timeline; only its messages seen"
             f" {RARE} times or less are still quoted there. Credentials in URLs are redacted.\n\n")

  all_b = sorted(buckets.values(), key=lambda b: (LEVELS.index(b.level), -b.count, b.logger))
  out.append("## Lines per level and logger\n\n| level | logger | lines | |\n|---|---|---:|---|\n")
  for b in all_b:
    out.append(f"| {b.level} | {b.logger} | {num(b.count)} | {'summarised' if b.flooded() else 'quoted'} |\n")
  for lg, n in sorted(debug.items(), key=lambda kv: -kv[1]):
    out.append(f"| DEBUG | {lg} | {num(n)} | counted only |\n")

  timeline = sorted((e for b in all_b for e in b.timeline()), key=lambda e: e.seq)
  out.append("\n## Timeline\n\nEvery line of the loggers that were not summarised, and the rare messages of those"
             " that were, in log order.\n\n```\n")
  out.extend(e.render() + "\n" for e in timeline[:MAX_TIMELINE])
  out.append("```\n")
  if len(timeline) > MAX_TIMELINE:
    out.append(f"\n{num(len(timeline) - MAX_TIMELINE)} more lines omitted.\n")

  out.append("\n## Summarised loggers\n")
  flooded = [b for b in all_b if b.flooded()]
  if not flooded:
    out.append("\nNone - every logger is quoted in full in the timeline.\n")
  for b in flooded:
    out.append(f"\n### {b.level} {b.logger} - {num(b.count)} lines\n\n"
               "| lines | message pattern, `#` for a variable part |\n|---:|---|\n")
    for p, n in sorted(b.patterns.items(), key=lambda kv: -kv[1]):
      out.append(f"| {num(n)} | `{p}` |\n")
    if b.other_patterns:
      out.append(f"| {num(b.other_patterns)} | other patterns |\n")
    out.append(f"\nFirst {len(b.quoted)} lines:\n\n```\n")
    out.extend(e.render() + "\n" for e in b.quoted)
    out.append(f"```\n\nLast {len(b.tail)} lines:\n\n```\n")
    out.extend(e.render() + "\n" for e in b.tail)
    out.append("```\n")
  return "".join(out)


def main():
  if len(sys.argv) != 2:
    sys.exit(__doc__)
  src = sys.argv[1]
  raw = sys.stdin.buffer if src == "-" else open(src, "rb")
  with gzip.open(raw, "rt", encoding="utf-8", errors="replace") as reader:
    sys.stdout.write(digest(reader, "job.log.gz" if src == "-" else src.rsplit("/", 1)[-1]))


if __name__ == "__main__":
  main()
