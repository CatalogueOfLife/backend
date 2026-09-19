package life.catalogue.release.review;

import life.catalogue.common.util.LoggingUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

/**
 * Condenses the job log of a release into a digest an agent can read whole.
 *
 * A release log is several GB unpacked, and nearly all of it is one line per identifier from the IdProvider -
 * information the ID reports hold in a far better shape. What a reviewer needs from the log is the rest: the
 * steps the job went through with their timings, and every warning and error. So a logger's lines at one level
 * are quoted only while there are few of them. A logger that floods a level is summarised instead, by its
 * count, its message patterns and its first and last lines - only its rare messages, like the IdProvider's
 * final counts, still make it into the timeline. DEBUG lines are only counted.
 *
 * Release logs are public already, but credentials a URL in them carries are still redacted before the digest
 * leaves the server - logs written before ReleaseAction redacted its URLs carry a build token.
 */
public class ReleaseLogDigest {
  /** a logger with more lines than this at one level is summarised instead of quoted */
  static final int QUOTE_LIMIT = 300;
  /** lines quoted from the start and the end of a summarised logger */
  static final int HEAD = 20;
  static final int TAIL = 10;
  /** distinct message patterns kept per summarised logger, the rest are counted together */
  static final int MAX_PATTERNS = 50;
  /** a message pattern seen this often or less in a summarised logger is still quoted in the timeline */
  static final int RARE = 3;
  /** a bound for a log whose loggers all stay just below the quote limit */
  static final int MAX_TIMELINE = 5000;
  static final int MAX_LINE_LENGTH = 500;
  static final int MAX_STACK_LINES = 20;
  private static final int PATTERN_TOKENS = 8;
  private static final List<String> LEVELS = List.of("ERROR", "WARN", "INFO", "DEBUG", "TRACE");

  // %d %-5level %-25logger{0} %6X{source} %msg, see JobAppender
  private static final Pattern LINE = Pattern.compile(
    "^(\\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d),\\d{3} (TRACE|DEBUG|INFO|WARN|ERROR) +(\\S+) +(.*)$");
  private static final Pattern TIMESTAMP = Pattern.compile("^\\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d,\\d{3} ");

  private static class Event {
    final long seq;
    final String line;
    StringBuilder stack;
    int stackLines;

    Event(long seq, String line) {
      this.seq = seq;
      this.line = line;
    }

    void continued(String line) {
      if (stackLines++ < MAX_STACK_LINES) {
        if (stack == null) {
          stack = new StringBuilder();
        }
        stack.append('\n').append(clean(line));
      }
    }

    String render() {
      if (stack == null) {
        return line;
      }
      String s = line + stack;
      return stackLines > MAX_STACK_LINES ? s + "\n\t... " + (stackLines - MAX_STACK_LINES) + " more lines" : s;
    }
  }

  private static class Bucket {
    final String level;
    final String logger;
    long count;
    /** every line while the logger stays below the quote limit, only its head once it floods */
    final List<Event> quoted = new ArrayList<>();
    final ArrayDeque<Event> tail = new ArrayDeque<>();
    final Map<String, Long> patterns = new LinkedHashMap<>();
    /** the first lines of each pattern, enough to quote all lines of a rare one */
    final Map<String, List<Event>> examples = new HashMap<>();
    long otherPatterns;

    Bucket(String level, String logger) {
      this.level = level;
      this.logger = logger;
    }

    boolean flooded() {
      return count > QUOTE_LIMIT;
    }

    /** every line this logger quotes in the timeline */
    Stream<Event> timeline() {
      if (!flooded()) {
        return quoted.stream();
      }
      return patterns.entrySet().stream()
        .filter(e -> e.getValue() <= RARE)
        .flatMap(e -> examples.get(e.getKey()).stream());
    }

    void add(Event e, String msg) {
      count++;
      String p = pattern(msg);
      if (patterns.containsKey(p) || patterns.size() < MAX_PATTERNS) {
        patterns.merge(p, 1L, Long::sum);
        var ex = examples.computeIfAbsent(p, k -> new ArrayList<>(RARE));
        if (ex.size() < RARE) {
          ex.add(e);
        }
      } else {
        otherPatterns++;
      }
      if (count <= QUOTE_LIMIT) {
        quoted.add(e);
        return;
      }
      if (count == QUOTE_LIMIT + 1) {
        // just started flooding: keep the head quoted and move the latest lines to the tail
        for (int i = Math.max(HEAD, quoted.size() - TAIL); i < quoted.size(); i++) {
          tail.addLast(quoted.get(i));
        }
        quoted.subList(HEAD, quoted.size()).clear();
      }
      tail.addLast(e);
      if (tail.size() > TAIL) {
        tail.removeFirst();
      }
    }
  }

  private final String source;
  private final Map<String, Bucket> buckets = new HashMap<>();
  private final Map<String, Long> debug = new HashMap<>();
  private long seq;
  private Event last;
  private String first;
  private String latest;

  private ReleaseLogDigest(String source) {
    this.source = source;
  }

  /**
   * @param gzLog the gzipped job log, as the JobAppender copies it into the release report directory
   */
  public static String digest(File gzLog) throws IOException {
    try (var reader = new BufferedReader(new InputStreamReader(
      new GZIPInputStream(new FileInputStream(gzLog), 1 << 16), StandardCharsets.UTF_8), 1 << 16)) {
      return digest(reader, gzLog.getName());
    }
  }

  static String digest(BufferedReader reader, String source) throws IOException {
    var d = new ReleaseLogDigest(source);
    String line;
    while ((line = reader.readLine()) != null) {
      d.read(line);
    }
    return d.render();
  }

  private void read(String line) {
    // the DEBUG flood is by far the biggest part of a release log - count it without running the regex
    if (line.startsWith("DEBUG", 24) && TIMESTAMP.matcher(line).lookingAt()) {
      debug.merge(logger(line), 1L, Long::sum);
      seq++;
      last = null;
      return;
    }
    Matcher m = LINE.matcher(line);
    if (!m.matches()) {
      // a stack trace or a message spanning several lines belongs to the line before
      if (last != null && !line.isBlank()) {
        last.continued(line);
      }
      return;
    }
    if (first == null) {
      first = m.group(1);
    }
    latest = m.group(1);
    last = new Event(seq++, clean(line));
    buckets.computeIfAbsent(m.group(2) + " " + m.group(3), k -> new Bucket(m.group(2), m.group(3)))
      .add(last, m.group(4));
  }

  private static String logger(String line) {
    int start = 30;
    while (start < line.length() && line.charAt(start) == ' ') {
      start++;
    }
    int end = line.indexOf(' ', start);
    return end < 0 ? line.substring(start) : line.substring(start, end);
  }

  private static String clean(String line) {
    String s = LoggingUtils.redactCredentials(line);
    return s.length() > MAX_LINE_LENGTH ? s.substring(0, MAX_LINE_LENGTH) + " [...]" : s;
  }

  /**
   * The first words of a message before any colon, with every word that carries an identifier or a number
   * replaced by #. Messages usually put their payload - a name, a list - after a colon.
   */
  static String pattern(String msg) {
    int colon = msg.indexOf(": ");
    String head = colon > 0 ? msg.substring(0, colon) : msg;
    StringBuilder sb = new StringBuilder();
    int n = 0;
    for (String tok : head.trim().split("\\s+")) {
      if (tok.isEmpty()) {
        continue;
      }
      if (n++ == PATTERN_TOKENS) {
        break;
      }
      if (!sb.isEmpty()) {
        sb.append(' ');
      }
      sb.append(isVariable(tok) ? "#" : tok);
    }
    return clean(sb.toString());
  }

  private static boolean isVariable(String tok) {
    for (int i = 0; i < tok.length(); i++) {
      char c = tok.charAt(i);
      if (Character.isDigit(c) || c == '_' || (i > 0 && Character.isUpperCase(c))) {
        return true;
      }
    }
    return false;
  }

  private static String num(long x) {
    return String.format(Locale.ENGLISH, "%,d", x);
  }

  private static int severity(String level) {
    return LEVELS.indexOf(level);
  }

  private String render() {
    StringBuilder sb = new StringBuilder("# Release job log digest\n\n");
    long lines = seq;
    sb.append("Digest of `").append(source).append("`: ").append(num(lines)).append(" lines");
    if (first != null) {
      sb.append(", from ").append(first).append(" to ").append(latest);
    }
    sb.append(".\n\n");
    sb.append("DEBUG lines are counted but never quoted. A logger with more than ").append(QUOTE_LIMIT)
      .append(" lines at one level is summarised under *Summarised loggers* instead of being quoted in the")
      .append(" timeline; only its messages seen ").append(RARE).append(" times or less are still quoted there.")
      .append(" Credentials in URLs are redacted.\n\n");

    // counts per level and logger, worst level first
    List<Bucket> all = new ArrayList<>(buckets.values());
    all.sort(Comparator.comparingInt((Bucket b) -> severity(b.level))
      .thenComparing(Comparator.comparingLong((Bucket b) -> b.count).reversed())
      .thenComparing(b -> b.logger));
    sb.append("## Lines per level and logger\n\n| level | logger | lines | |\n|---|---|---:|---|\n");
    for (Bucket b : all) {
      sb.append("| ").append(b.level).append(" | ").append(b.logger).append(" | ").append(num(b.count))
        .append(" | ").append(b.flooded() ? "summarised" : "quoted").append(" |\n");
    }
    debug.entrySet().stream()
      .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
      .forEach(e -> sb.append("| DEBUG | ").append(e.getKey()).append(" | ").append(num(e.getValue()))
        .append(" | counted only |\n"));

    // the timeline of everything not summarised
    List<Event> timeline = all.stream()
      .flatMap(Bucket::timeline)
      .sorted(Comparator.comparingLong(e -> e.seq))
      .toList();
    sb.append("\n## Timeline\n\nEvery line of the loggers that were not summarised, and the rare messages of those")
      .append(" that were, in log order.\n\n```\n");
    timeline.stream().limit(MAX_TIMELINE).forEach(e -> sb.append(e.render()).append('\n'));
    sb.append("```\n");
    if (timeline.size() > MAX_TIMELINE) {
      sb.append("\n").append(num(timeline.size() - MAX_TIMELINE)).append(" more lines omitted.\n");
    }

    sb.append("\n## Summarised loggers\n");
    var flooded = all.stream().filter(Bucket::flooded).toList();
    if (flooded.isEmpty()) {
      sb.append("\nNone - every logger is quoted in full in the timeline.\n");
    }
    for (Bucket b : flooded) {
      sb.append("\n### ").append(b.level).append(' ').append(b.logger).append(" - ").append(num(b.count))
        .append(" lines\n\n| lines | message pattern, `#` for a variable part |\n|---:|---|\n");
      b.patterns.entrySet().stream()
        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
        .forEach(e -> sb.append("| ").append(num(e.getValue())).append(" | `").append(e.getKey()).append("` |\n"));
      if (b.otherPatterns > 0) {
        sb.append("| ").append(num(b.otherPatterns)).append(" | other patterns |\n");
      }
      sb.append("\nFirst ").append(b.quoted.size()).append(" lines:\n\n```\n");
      b.quoted.forEach(e -> sb.append(e.render()).append('\n'));
      sb.append("```\n\nLast ").append(b.tail.size()).append(" lines:\n\n```\n");
      b.tail.forEach(e -> sb.append(e.render()).append('\n'));
      sb.append("```\n");
    }
    return sb.toString();
  }
}
