package life.catalogue.common.tax;

import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.util.NameFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

/**
 * The authors of an {@link Authorship} as a list of atoms, the way the ColDP combinationAuthorship and
 * basionymAuthorship columns hold them. ColDP has no column for a name or act published anonymously,
 * so {@link Authorship#isAnonymous()} travels with the authors: a work without attributed authors as the
 * lone code specific "Anon." or "anon.", attributed authors each in square brackets,
 * as ICZN Recommendation 51D cites them.
 * See https://github.com/CatalogueOfLife/backend/issues/1611
 */
public class AuthorAtoms {
  private static final Pattern ANONYMOUS = Pattern.compile("^anon(?:\\.|ymous|ymus)?$", Pattern.CASE_INSENSITIVE);
  private static final Pattern BRACKETED = Pattern.compile("^\\[\\s*(.*?)\\s*]$");

  private AuthorAtoms() {
  }

  /**
   * @return the authors to write for the given authorship, marking an anonymous work
   */
  public static List<String> encode(Authorship a, @Nullable NomCode code) {
    if (a.isAnonymous()) {
      if (a.hasAuthors()) {
        return a.getAuthors().stream().map(x -> "[" + x + "]").collect(Collectors.toList());
      }
      return List.of(NameFormatter.anonymousAuthor(code));
    }
    return a.getAuthors();
  }

  /**
   * Sets the authors of the authorship, and its anonymous flag if they mark an anonymous work.
   * @param authors the authors as read, can be null
   */
  public static void decode(@Nullable List<String> authors, Authorship a) {
    if (authors != null && authors.size() == 1 && ANONYMOUS.matcher(authors.getFirst()).matches()) {
      a.setAnonymous(true);
      a.setAuthors(new ArrayList<>());

    } else if (authors != null && !authors.isEmpty() && authors.stream().allMatch(x -> BRACKETED.matcher(x).matches())) {
      a.setAnonymous(true);
      a.setAuthors(authors.stream().map(AuthorAtoms::unbracket).collect(Collectors.toList()));

    } else {
      a.setAuthors(authors);
    }
  }

  private static String unbracket(String x) {
    Matcher m = BRACKETED.matcher(x);
    return m.matches() ? m.group(1) : x;
  }
}
