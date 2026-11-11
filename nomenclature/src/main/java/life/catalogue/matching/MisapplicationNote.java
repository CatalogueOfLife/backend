package life.catalogue.matching;

import life.catalogue.api.model.Name;
import life.catalogue.api.model.ScientificName;
import life.catalogue.common.text.StringUtils;
import life.catalogue.matching.authorship.AuthorComparator;
import life.catalogue.parser.NameParser;

import org.gbif.nameparser.api.NomCode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

/**
 * The note of a misapplied name, read as the author whose sense it is used in and the authors of the names it is not:
 * <code>sensu F. W. Hutton, 1873</code>, <code>auct. non (DC.) Bunge</code>,
 * <code>sensu Leloup, 1981 not Gould, 1846</code>, <code>nec Hampson sensu Gaede, 1917</code>.
 *
 * Two misapplied names of one spelling are told apart by these authors, and sources cite them as differently as any
 * other authorship: <code>sensu Hutton, 1873</code> is <code>sensu F. W. Hutton, 1873</code>. Comparing the notes as
 * text made them two names, so the authors are compared with the {@link AuthorComparator} instead.
 */
public final class MisapplicationNote {
  private enum Marker {SENSU, AUCT, NON}

  // the sense of an author, not sensu lato or stricto. Lower case s. only, an upper case S. is an initial.
  private static final Pattern SENSU = Pattern.compile(
    "(?:\\b[Ss]ensu\\b|\\bsec\\.|\\bss?\\.)(?!\\s*(?:lat|str|l\\.|l\\b))");
  // of authors in general
  private static final Pattern AUCT = Pattern.compile("\\bauct(?:t|orum)?\\b\\.?", Pattern.CASE_INSENSITIVE);
  // a name it is not, usually the original author of the name
  private static final Pattern NON = Pattern.compile("\\b(?:non|nec|not)\\b", Pattern.CASE_INSENSITIVE);
  // in part, never an author
  private static final Pattern PRO_PARTE = Pattern.compile("\\bp\\.\\s?p\\.|\\bpro parte\\b", Pattern.CASE_INSENSITIVE);
  private static final Pattern EDGES = Pattern.compile("^[\\s,;:]+|[\\s,;:]+$");
  private static final MisapplicationNote EMPTY = new MisapplicationNote(false, null, List.of());

  private record Found(Marker marker, int start, int end) {}

  /**
   * An author as cited and as parsed, null if the parser finds no author in it.
   */
  private record Author(String text, @Nullable ScientificName name) {}

  private final boolean auct;
  private final Author sensu;
  private final List<Author> non;

  private MisapplicationNote(boolean auct, @Nullable Author sensu, List<Author> non) {
    this.auct = auct;
    this.sensu = sensu;
    this.non = non;
  }

  /**
   * Reads the note as a sequence of markers, each followed by its author up to the next marker. Text before the first
   * marker is the name's own authorship and ignored. Of several sensu authors the first counts.
   * @param note the name phrase, the note the parser found in the authorship or both
   * @param code used to parse the authors
   * @return the parts of the note, empty if it has none of them
   */
  public static MisapplicationNote parse(@Nullable String note, @Nullable NomCode code) {
    if (note == null || note.isBlank()) {
      return EMPTY;
    }
    note = PRO_PARTE.matcher(note).replaceAll(" ");
    List<Found> found = new ArrayList<>();
    find(found, Marker.SENSU, SENSU, note);
    find(found, Marker.AUCT, AUCT, note);
    find(found, Marker.NON, NON, note);
    found.sort(Comparator.comparingInt(Found::start));

    boolean auct = false;
    Author sensu = null;
    List<Author> non = new ArrayList<>();
    for (int i = 0; i < found.size(); i++) {
      var f = found.get(i);
      int end = i + 1 < found.size() ? found.get(i + 1).start() : note.length();
      String text = trim(note.substring(Math.min(f.end(), end), end));
      switch (f.marker()) {
        case AUCT -> auct = true;
        case SENSU -> {
          if (sensu == null && text != null) {
            sensu = author(text, code);
          }
        }
        case NON -> {
          if (text != null) {
            non.add(author(text, code));
          }
        }
      }
    }
    if (!auct && sensu == null && non.isEmpty()) {
      return EMPTY;
    }
    return new MisapplicationNote(auct, sensu, List.copyOf(non));
  }

  private static void find(List<Found> found, Marker marker, Pattern p, String note) {
    Matcher m = p.matcher(note);
    while (m.find()) {
      found.add(new Found(marker, m.start(), m.end()));
    }
  }

  // drops separators and a bracket left over from around the note, auct. (not Philippi, 1844)
  @Nullable
  private static String trim(String x) {
    x = EDGES.matcher(x).replaceAll("");
    if (x.endsWith(")") && x.indexOf('(') < 0) {
      x = x.substring(0, x.length() - 1);
    } else if (x.startsWith("(") && x.indexOf(')') < 0) {
      x = x.substring(1);
    }
    x = EDGES.matcher(x).replaceAll("");
    return x.isEmpty() ? null : x;
  }

  private static Author author(String text, @Nullable NomCode code) {
    var parsed = NameParser.PARSER.parseAuthorship(text, code);
    if (parsed.isPresent()) {
      var a = parsed.get();
      boolean comb = a.getCombinationAuthorship() != null && !a.getCombinationAuthorship().isEmpty();
      boolean bas = a.getBasionymAuthorship() != null && !a.getBasionymAuthorship().isEmpty();
      if (comb || bas) {
        Name n = new Name();
        n.setCode(code);
        n.setCombinationAuthorship(a.getCombinationAuthorship());
        n.setBasionymAuthorship(a.getBasionymAuthorship());
        return new Author(text, n);
      }
    }
    return new Author(text, null);
  }

  /**
   * True if the name is misapplied by authors in general, <code>auct.</code> or <code>sensu auct.</code>
   */
  public boolean isAuct() {
    return auct;
  }

  /**
   * @return the author the name is used in the sense of, null for none or authors in general
   */
  @Nullable
  public String getSensu() {
    return sensu == null ? null : sensu.text();
  }

  /**
   * @return the authors of the names it is not, <code>Bunge</code> of <code>auct. non Bunge</code>
   */
  public List<String> getNon() {
    return non.stream().map(Author::text).toList();
  }

  public boolean isEmpty() {
    return !auct && sensu == null && non.isEmpty();
  }

  /**
   * Compares the sense and the exclusions of two notes, each only if both notes have it: a note citing the name it
   * excludes is not a different misapplication from one leaving it out. Authors in general against a named author are
   * a different sense. Of two lists of excluded names the shorter one has to be part of the longer one, in any order.
   * @return EQUAL or DIFFERENT, possibly probable, or UNKNOWN if the two notes have no part in common
   */
  public Equality compare(MisapplicationNote other, AuthorComparator authComp) {
    Equality eq = Equality.UNKNOWN;
    final boolean sense = auct || sensu != null;
    final boolean otherSense = other.auct || other.sensu != null;
    if (sense && otherSense) {
      if (sensu != null && other.sensu != null) {
        eq = eq.and(compare(sensu, other.sensu, authComp));
      } else {
        // authors in general on both sides, or against a named author
        eq = eq.and(sensu == null && other.sensu == null ? Equality.EQUAL : Equality.DIFFERENT);
      }
    }
    if (!non.isEmpty() && !other.non.isEmpty()) {
      var shorter = non.size() <= other.non.size() ? non : other.non;
      var longer = shorter == non ? other.non : non;
      boolean contained = shorter.stream()
        .allMatch(a -> longer.stream().anyMatch(b -> compare(a, b, authComp).isEqual()));
      eq = eq.and(contained ? Equality.EQUAL : Equality.DIFFERENT);
    }
    return eq;
  }

  private static Equality compare(Author a, Author b, AuthorComparator authComp) {
    if (a.name() != null && b.name() != null) {
      Equality eq = authComp.compare(a.name(), b.name());
      if (eq != Equality.UNKNOWN) {
        return eq;
      }
    }
    // not an authorship the parser understands: only the text can tell
    return StringUtils.equalsDigitOrAsciiLettersIgnoreCase(a.text(), b.text()) ? Equality.EQUAL : Equality.DIFFERENT;
  }

  @Override
  public String toString() {
    return (auct ? "auct." : "") + (sensu == null ? "" : " sensu " + sensu.text())
      + (non.isEmpty() ? "" : " non " + String.join(" non ", getNon()));
  }
}
