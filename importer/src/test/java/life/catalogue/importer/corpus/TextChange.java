package life.catalogue.importer.corpus;

import java.text.Normalizer;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

/**
 * Classifies how much a string changed between two runs: through increasingly lenient normalisations, and for a real
 * change of its letters or digits by a heuristic cause, so that thousands of changes fall into a few buckets to read.
 */
public class TextChange {
  private static final Pattern WS = Pattern.compile("\\s+");
  private static final Pattern NON_ALNUM = Pattern.compile("[^\\p{L}\\p{N}]+");
  private static final Pattern MARKS = Pattern.compile("\\p{M}+");
  private static final Pattern DIGITS = Pattern.compile("\\p{N}+");
  private static final Set<String> SEPARATORS = Set.of("and", "et", "und", "y");
  private static final Set<String> NOTES = Set.of("sensu", "non", "nec", "auct", "auctt", "auctorum", "emend", "nom",
    "comb", "stat", "ined", "sic", "corrig", "nomen", "nud", "nudum", "illeg", "inval", "nov", "fide", "sec", "excl",
    "pro", "parte", "syn", "hort", "ms", "orth");
  private static final Set<String> RANK_MARKERS = Set.of("subsp", "ssp", "var", "subvar", "f", "fo", "forma", "subf",
    "sect", "subsect", "ser", "subser", "subg", "subgen", "cv", "agg", "sp", "spp", "nothosubsp", "nothovar", "convar",
    "gx", "grex", "group", "morph", "ab", "aberration", "trib", "subtrib", "tr");

  /**
   * How lenient a comparison it takes for two strings to be the same, in increasing order.
   */
  public enum Level {
    SAME,
    /** differ in whitespace only */
    WHITESPACE,
    /** differ in whitespace and punctuation */
    PUNCTUATION,
    /** also differ in case */
    CASE,
    /** also differ in diacritics */
    DIACRITICS,
    /** a value appeared */
    ADDED,
    /** a value disappeared */
    REMOVED,
    /** the letters or digits differ */
    TEXT;

    /**
     * @return true for changes beyond spelling out the same letters differently
     */
    public boolean isSignificant() {
      return ordinal() >= ADDED.ordinal();
    }
  }

  private TextChange() {
  }

  public static Level level(@Nullable String before, @Nullable String after) {
    if (Objects.equals(before, after)) return Level.SAME;
    if (before == null) return Level.ADDED;
    if (after == null) return Level.REMOVED;
    String b = WS.matcher(before).replaceAll("");
    String a = WS.matcher(after).replaceAll("");
    if (b.equals(a)) return Level.WHITESPACE;
    b = NON_ALNUM.matcher(b).replaceAll("");
    a = NON_ALNUM.matcher(a).replaceAll("");
    if (b.equals(a)) return Level.PUNCTUATION;
    b = b.toLowerCase(Locale.ROOT);
    a = a.toLowerCase(Locale.ROOT);
    if (b.equals(a)) return Level.CASE;
    if (fold(b).equals(fold(a))) return Level.DIACRITICS;
    return Level.TEXT;
  }

  private static String fold(String x) {
    return MARKS.matcher(Normalizer.normalize(x, Normalizer.Form.NFD)).replaceAll("");
  }

  /**
   * @return the lower case, diacritic free words of a string
   */
  static List<String> tokens(@Nullable String x) {
    if (x == null) return List.of();
    List<String> tokens = new ArrayList<>();
    for (String t : NON_ALNUM.split(fold(x.toLowerCase(Locale.ROOT)))) {
      if (!t.isEmpty()) {
        tokens.add(t);
      }
    }
    return tokens;
  }

  /**
   * The likely cause of a TEXT change, the first that explains it of:
   * <ul>
   *   <li>separator: only the words and, et, und, y joining authors differ</li>
   *   <li>et-al: only "et al." differs</li>
   *   <li>initials: only single letters differ</li>
   *   <li>year: only numbers differ</li>
   *   <li>rank-marker: only rank markers differ</li>
   *   <li>in-citation: an "in" citation came or went</li>
   *   <li>ex-author: an "ex" author came or went</li>
   *   <li>note: one side holds the words of a nomenclatural or taxonomic note the other does not</li>
   *   <li>order: the same words in a different order</li>
   *   <li>abbreviation: as many words, each the start of the other's, e.g. Mill. and Miller</li>
   *   <li>tail-dropped, tail-added, head-dropped, head-added: the words of one start or end the other's</li>
   *   <li>words-dropped, words-added: the words of one are a subset of the other's</li>
   *   <li>other</li>
   * </ul>
   */
  public static String cause(@Nullable String before, @Nullable String after) {
    List<String> b = tokens(before);
    List<String> a = tokens(after);
    if (same(b, a, t -> !SEPARATORS.contains(t))) return "separator";
    if (same(b, a, t -> !t.equals("et") && !t.equals("al"))) return "et-al";
    if (same(b, a, t -> t.length() > 1 || DIGITS.matcher(t).matches())) return "initials";
    if (same(b, a, t -> !DIGITS.matcher(t).matches())) return "year";
    if (same(b, a, t -> !RANK_MARKERS.contains(t))) return "rank-marker";
    if (b.contains("in") != a.contains("in")) return "in-citation";
    if (b.contains("ex") != a.contains("ex")) return "ex-author";
    if (hasNote(b) != hasNote(a) || !notes(b).equals(notes(a))) return "note";
    if (new HashSet<>(b).equals(new HashSet<>(a))) return "order";
    if (abbreviated(b, a)) return "abbreviation";
    if (startsWith(b, a)) return "tail-dropped";
    if (startsWith(a, b)) return "tail-added";
    if (startsWith(b.reversed(), a.reversed())) return "head-dropped";
    if (startsWith(a.reversed(), b.reversed())) return "head-added";
    if (new HashSet<>(b).containsAll(a)) return "words-dropped";
    if (new HashSet<>(a).containsAll(b)) return "words-added";
    return "other";
  }

  /**
   * @return true if both have the same number of words and of each pair of words one starts with the other
   */
  private static boolean abbreviated(List<String> b, List<String> a) {
    if (b.size() != a.size()) return false;
    for (int i = 0; i < b.size(); i++) {
      if (!b.get(i).startsWith(a.get(i)) && !a.get(i).startsWith(b.get(i))) return false;
    }
    return true;
  }

  private static boolean startsWith(List<String> x, List<String> prefix) {
    return prefix.size() < x.size() && x.subList(0, prefix.size()).equals(prefix);
  }

  private static boolean same(List<String> b, List<String> a, Predicate<String> keep) {
    return b.stream().filter(keep).toList().equals(a.stream().filter(keep).toList());
  }

  private static boolean hasNote(List<String> tokens) {
    return tokens.stream().anyMatch(NOTES::contains);
  }

  private static Set<String> notes(List<String> tokens) {
    Set<String> notes = new TreeSet<>();
    tokens.stream().filter(NOTES::contains).forEach(notes::add);
    return notes;
  }
}
