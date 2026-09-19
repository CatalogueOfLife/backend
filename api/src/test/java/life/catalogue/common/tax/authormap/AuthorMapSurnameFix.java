package life.catalogue.common.tax.authormap;

import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.common.tax.authormap.IpniAuthorLookup.IpniAuthor;

import org.gbif.nameparser.util.UnicodeUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

/**
 * Developer tool (NOT part of the app or test suite) that repairs the canonicals of the IPNI derived base of
 * api/src/main/resources/authorship/authormap.txt in place.
 *
 * That base (ported from GBIF's checklistbank in 2018, generated in 2015 from an IPNI author export nobody
 * kept) spells a canonical as initials plus the LAST word of the full name. The first part of a compound
 * surname therefore became an invented initial ("A A F von Waldheim" for Fischer von Waldheim, "H R López"
 * for Ruiz López), and a generational suffix became the surname ("B L T Sr." for B.L.Turner).
 * See https://github.com/CatalogueOfLife/backend/issues/1597
 *
 * Every base row still carries IPNI's standard form and the full name, so only the start of the surname is
 * missing. IPNI's own surname field decides it, looked up by standard form through {@link IpniAuthorLookup}:
 * <ul>
 *   <li>a word collapsed into an initial is spelled out again when IPNI's surname starts with it -
 *   the canonical only ever grows leftward, as IPNI's surname is inconsistent the other way
 *   ("Bory", "Kerner", "Rochebrune" hold just one part)</li>
 *   <li>a generational suffix is dropped, and the younger of a father/son pair that would then share a
 *   canonical gets the botanical filius "f." IPNI uses itself ("J.Kickx f.")</li>
 *   <li>anything else doubtful only goes to the review report</li>
 * </ul>
 * Only the canonical column of the changed lines is rewritten, the file is not resorted.
 *
 * Run from the repository root:
 *   mvn -q -pl api exec:java -Dexec.classpathScope=test \
 *       -DmainClass=life.catalogue.common.tax.authormap.AuthorMapSurnameFix \
 *       -Dexec.args="api/src/main/resources/authorship/authormap.txt api/target/authormap-surname-report.txt api/target/ipni-authors.tsv"
 */
public class AuthorMapSurnameFix {
  private static final Set<String> SUFFIXES = Set.of("jr", "sr", "ii", "iii", "iv", "frs", "the elder", "the younger");
  private static final Set<String> YOUNGER = Set.of("jr", "ii", "iii", "iv", "the younger");

  enum Kind {
    /** not an initials canonical, nothing to decide */
    SKIP,
    UNCHANGED,
    /** a collapsed first part of the surname spelled out again */
    EXTENDED,
    /** a generational suffix dropped */
    SUFFIX,
    /** left alone, but worth a human look */
    REVIEW,
    /** IPNI does not know the standard form */
    NOT_FOUND
  }

  record Decision(Kind kind, String canonical, @Nullable String suffix, String note) {
    boolean changed() {
      return kind == Kind.EXTENDED || kind == Kind.SUFFIX;
    }
  }

  /**
   * An initials canonical split into its initials and the rest, with the full name alias it was built from.
   * @param full the full name words, as many as initials plus rest
   */
  record Parsed(List<String> initials, List<String> rest, @Nullable List<String> full, @Nullable String standardForm) {
    boolean suffixed() {
      return SUFFIXES.contains(key(String.join(" ", rest)));
    }
  }

  /**
   * @return the canonical split into initials and rest, or null if it does not start with an initial
   */
  static @Nullable Parsed parse(AuthorEntry e) {
    List<String> words = List.of(e.canonical().trim().split("\\s+"));
    int n = 0;
    while (n < words.size() && isInitial(words.get(n))) {
      n++;
    }
    if (n == 0 || n == words.size()) return null;
    List<String> initials = words.subList(0, n);
    List<String> rest = words.subList(n, words.size());

    List<String> full = null;
    String fullAlias = null;
    for (String a : e.aliases()) {
      List<String> aw = List.of(a.trim().split("\\s+"));
      if (aw.size() == words.size() && matchesInitials(aw, initials) && matchesRest(aw, rest)) {
        full = aw.stream().map(w -> w.replaceAll(",$", "")).toList();
        fullAlias = a;
        break;
      }
    }
    String std = e.aliases().isEmpty() || e.aliases().get(0).equals(fullAlias) ? null : e.aliases().get(0);
    return new Parsed(initials, rest, full, std);
  }

  /** @return true if the row may need IPNI's surname to be decided */
  static boolean needsLookup(@Nullable Parsed p) {
    return p != null && p.full() != null && p.standardForm() != null && p.initials().size() >= 2;
  }

  static Decision decide(AuthorEntry e, @Nullable IpniAuthor ipni) {
    Parsed p = parse(e);
    if (p == null) {
      return new Decision(Kind.SKIP, e.canonical(), null, null);
    }
    if (p.initials().size() < 2) {
      return new Decision(Kind.UNCHANGED, e.canonical(), null, null);
    }
    if (p.full() == null) {
      return new Decision(Kind.REVIEW, e.canonical(), null, "no full name alias to spell the initials out");
    }
    final List<String> in = p.initials();
    final boolean suffixed = p.suffixed();
    final String suffix = suffixed ? key(String.join(" ", p.rest())) : null;

    // where IPNI's surname starts among the words collapsed into initials, never at the first given name.
    // All collapsed words from there on must be part of it: "George Arnott Walker Arnott" is no "Arnott Walker Arnott"
    int start = -1;
    if (ipni != null && !ipni.surname().isBlank()) {
      List<String> surname = Arrays.stream(ipni.surname().trim().split("\\s+")).map(AuthorMapSurnameFix::key).toList();
      for (int i = 1; i < in.size() && start < 0; i++) {
        if (in.size() - i <= surname.size() && matchesFrom(p.full(), i, in.size(), surname)) {
          start = i;
        }
      }
    }
    if (suffixed) {
      // the surname precedes the suffix, whatever IPNI says
      int s = start > 0 ? start : in.size() - 1;
      String note = ipni == null ? "not in IPNI, surname taken from before the suffix" : ipniNote(ipni);
      return new Decision(Kind.SUFFIX, join(in.subList(0, s), p.full().subList(s, in.size())), suffix, note);
    }
    if (start > 0) {
      List<String> surname = new ArrayList<>(p.full().subList(start, in.size()));
      surname.addAll(p.rest());
      return new Decision(Kind.EXTENDED, join(in.subList(0, start), surname), null, ipniNote(ipni));
    }
    if (ipni == null) {
      return new Decision(Kind.NOT_FOUND, e.canonical(), null, null);
    }
    Set<String> restKeys = p.rest().stream().map(AuthorMapSurnameFix::key).collect(Collectors.toSet());
    String first = key(ipni.surname().trim().split("\\s+")[0]);
    if (!ipni.surname().isBlank() && !restKeys.contains(first) && !key(p.full().get(0)).equals(first)) {
      return new Decision(Kind.REVIEW, e.canonical(), null, "IPNI surname not in the full name: " + ipniNote(ipni));
    }
    return new Decision(Kind.UNCHANGED, e.canonical(), null, null);
  }

  /**
   * @return true if the canonical carries more initials than IPNI has forenames, i.e. an initial was
   *         invented from something else - a nickname like "Braam" in "A E B van Wyk"
   */
  static boolean moreInitialsThanForenames(String canonical, IpniAuthor ipni) {
    // IPNI writes forenames as dotted initials ("A.C.") and in parentheses ("(Charles) David") too
    long forenames = Arrays.stream(ipni.forename().split("[\\s.,()`'\"-]+"))
      .filter(w -> !w.isEmpty() && Character.isUpperCase(w.codePointAt(0)))
      .count();
    long initials = Arrays.stream(canonical.split("\\s+")).takeWhile(AuthorMapSurnameFix::isInitial).count();
    return forenames > 0 && initials > forenames;
  }

  /**
   * Final canonicals of all rows. Dropping a generational suffix can make father and son share a canonical
   * ("J Kickx" for Jean Kickx Sr. and Jr.): the younger then gets the filius "f.", which the author comparison
   * reads as a distinguishing suffix. A collision that cannot be told apart that way keeps the old canonical.
   */
  static List<String> resolveCollisions(List<AuthorEntry> rows, List<Decision> decisions, List<String> report) {
    List<String> canonicals = new ArrayList<>();
    Map<String, List<Integer>> byKey = new HashMap<>();
    for (int i = 0; i < rows.size(); i++) {
      AuthorEntry e = rows.get(i);
      canonicals.add(e == null ? null : decisions.get(i).canonical());
      if (e != null) {
        byKey.computeIfAbsent(AuthorshipNormalizer.normalize(canonicals.get(i)), k -> new ArrayList<>()).add(i);
      }
    }
    for (List<Integer> group : byKey.values()) {
      // identical duplicate rows are one author, not a collision
      Map<List<String>, Integer> distinct = new LinkedHashMap<>();
      for (int i : group) {
        distinct.putIfAbsent(rows.get(i).aliases(), i);
      }
      if (distinct.size() < 2 || group.stream().noneMatch(i -> decisions.get(i).changed())) continue;

      List<Integer> suffixed = group.stream().filter(i -> decisions.get(i).kind() == Kind.SUFFIX).toList();
      List<Integer> younger = suffixed.stream().filter(i -> YOUNGER.contains(decisions.get(i).suffix())).toList();
      String names = group.stream().map(i -> rows.get(i).canonical() + " -> " + canonicals.get(i)).collect(Collectors.joining(" | "));
      if (!suffixed.isEmpty() && distinct.size() == 2 && !younger.isEmpty()
          && younger.stream().map(i -> rows.get(i).aliases()).distinct().count() == 1) {
        for (int i : younger) {
          canonicals.set(i, canonicals.get(i) + " f.");
        }
        report.add("filius\t" + names + "\t-> younger marked f.");
      } else if (!suffixed.isEmpty()) {
        for (int i : suffixed) {
          canonicals.set(i, rows.get(i).canonical());
        }
        report.add("collision\t" + names + "\t-> suffix rows left unchanged");
      } else {
        report.add("collision\t" + names + "\t-> kept, shared canonical");
      }
    }
    return canonicals;
  }

  public static void main(String[] args) throws Exception {
    Path map = Paths.get(args.length > 0 ? args[0] : "api/src/main/resources/authorship/authormap.txt");
    Path reportFile = Paths.get(args.length > 1 ? args[1] : "api/target/authormap-surname-report.txt");
    Path cache = Paths.get(args.length > 2 ? args[2] : "api/target/ipni-authors.tsv");

    List<String> lines = Files.readAllLines(map, StandardCharsets.UTF_8);
    List<AuthorEntry> rows = new ArrayList<>();
    for (String line : lines) {
      String[] c = line.split("\t");
      rows.add(c.length < 3 ? null : new AuthorEntry(c[0], AuthorCode.valueOf(c[1].trim().toUpperCase()), List.of(c).subList(2, c.length)));
    }

    List<Decision> decisions = new ArrayList<>();
    Map<Integer, IpniAuthor> ipniByRow = new HashMap<>();
    try (IpniAuthorLookup ipni = new IpniAuthorLookup(cache)) {
      for (int i = 0; i < rows.size(); i++) {
        AuthorEntry e = rows.get(i);
        if (e == null) {
          decisions.add(null);
          continue;
        }
        Parsed p = parse(e);
        IpniAuthor a = needsLookup(p) ? ipni.get(p.standardForm()).orElse(null) : null;
        if (a != null) ipniByRow.put(i, a);
        decisions.add(decide(e, a));
      }
    }

    List<String> collisions = new ArrayList<>();
    List<String> canonicals = resolveCollisions(rows, decisions, collisions);

    Map<String, List<String>> sections = new LinkedHashMap<>();
    for (String s : List.of("extended", "suffix dropped", "collisions", "review", "more initials than IPNI forenames", "not in IPNI")) {
      sections.put(s, new ArrayList<>());
    }
    sections.get("collisions").addAll(collisions);
    int changed = 0;
    List<String> out = new ArrayList<>(lines);
    for (int i = 0; i < rows.size(); i++) {
      AuthorEntry e = rows.get(i);
      if (e == null) continue;
      Decision d = decisions.get(i);
      String row = e.canonical() + "\t" + canonicals.get(i) + "\t" + e.aliases().get(0) + "\t" + Objects.toString(d.note(), "");
      switch (d.kind()) {
        case EXTENDED -> sections.get("extended").add(row);
        case SUFFIX -> sections.get("suffix dropped").add(row);
        case REVIEW -> sections.get("review").add(row);
        case NOT_FOUND -> sections.get("not in IPNI").add(row);
        default -> {}
      }
      IpniAuthor a = ipniByRow.get(i);
      if (a != null && moreInitialsThanForenames(canonicals.get(i), a)) {
        sections.get("more initials than IPNI forenames").add(e.canonical() + "\t" + canonicals.get(i) + "\t" + ipniNote(a));
      }
      if (!canonicals.get(i).equals(e.canonical())) {
        String[] c = lines.get(i).split("\t", 2);
        out.set(i, canonicals.get(i) + "\t" + c[1]);
        changed++;
      }
    }

    StringBuilder report = new StringBuilder();
    sections.forEach((name, list) -> {
      report.append("## ").append(name).append(" (").append(list.size()).append(")\n");
      list.forEach(r -> report.append(r).append('\n'));
      report.append('\n');
    });
    Files.writeString(reportFile, report.toString());
    Files.writeString(map, String.join("\n", out) + "\n", StandardCharsets.UTF_8);
    sections.forEach((name, list) -> System.out.printf("%-35s: %d%n", name, list.size()));
    System.out.printf("changed canonicals                 : %d -> %s%nreport: %s%n", changed, map, reportFile);
  }

  private static String ipniNote(@Nullable IpniAuthor a) {
    return a == null ? "" : "IPNI " + a.standardForm() + " = " + a.forename() + " | " + a.surname();
  }

  private static String join(List<String> initials, List<String> surname) {
    List<String> words = new ArrayList<>(initials);
    words.addAll(surname);
    return String.join(" ", words);
  }

  private static boolean isInitial(String w) {
    return w.codePointCount(0, w.length()) == 1 && Character.isUpperCase(w.codePointAt(0));
  }

  private static boolean matchesInitials(List<String> words, List<String> initials) {
    for (int i = 0; i < initials.size(); i++) {
      String k = key(words.get(i));
      if (k.isEmpty() || k.charAt(0) != key(initials.get(i)).charAt(0)) return false;
    }
    return true;
  }

  /** @return true if words [from, to) are the first words of surname */
  private static boolean matchesFrom(List<String> words, int from, int to, List<String> surname) {
    for (int i = from; i < to; i++) {
      if (!key(words.get(i)).equals(surname.get(i - from))) return false;
    }
    return true;
  }

  private static boolean matchesRest(List<String> words, List<String> rest) {
    int offset = words.size() - rest.size();
    for (int i = 0; i < rest.size(); i++) {
      if (!key(words.get(offset + i)).equals(key(rest.get(i)))) return false;
    }
    return true;
  }

  /** ascii folded, lower case letters and digits only */
  static String key(String x) {
    return UnicodeUtils.foldToAscii(x).toLowerCase().replaceAll("[^a-z0-9 ]", "").trim();
  }
}
