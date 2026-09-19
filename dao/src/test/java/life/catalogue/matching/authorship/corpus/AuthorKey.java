package life.catalogue.matching.authorship.corpus;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

/**
 * The authors of a name without its years: the four teams basionym ex authors, basionym authors, combination ex
 * authors and combination authors, separated by a semicolon, the authors of a team by a pipe.
 * <p>
 * This is what the corpus is mined on, so it deliberately knows nothing about the code it is there to measure -
 * no ASCII folding, no author map, no initials. Do not make it use AuthorshipNormalizer.
 *
 * @param exact the authors as parsed, whitespace collapsed
 * @param loose lower cased letters and digits only. Two citations that are equal on this differ in punctuation,
 *              spacing or case alone and make no pair worth comparing. Diacritics stay, as folding them is the
 *              job of the code that is measured.
 */
public record AuthorKey(String exact, String loose) {
  private static final char SLOT_SEPARATOR = ';';

  public static AuthorKey of(ExportRow r) {
    List<List<String>> slots = List.of(r.basExAuthors(), r.basAuthors(), r.combExAuthors(), r.combAuthors());
    StringBuilder exact = new StringBuilder();
    StringBuilder loose = new StringBuilder();
    for (int i = 0; i < slots.size(); i++) {
      if (i > 0) {
        exact.append(SLOT_SEPARATOR);
        loose.append(SLOT_SEPARATOR);
      }
      boolean firstExact = true;
      boolean firstLoose = true;
      for (String author : slots.get(i)) {
        String a = StringUtils.normalizeSpace(author);
        if (a.isEmpty()) continue;
        if (!firstExact) exact.append(ExportRow.TEAM_SEPARATOR);
        exact.append(a);
        firstExact = false;

        String l = loose(a);
        if (l.isEmpty()) continue;
        if (!firstLoose) loose.append(ExportRow.TEAM_SEPARATOR);
        loose.append(l);
        firstLoose = false;
      }
    }
    return new AuthorKey(exact.toString(), loose.toString());
  }

  private static String loose(String author) {
    StringBuilder sb = new StringBuilder(author.length());
    author.codePoints()
          .filter(Character::isLetterOrDigit)
          .map(Character::toLowerCase)
          .forEach(sb::appendCodePoint);
    return sb.toString();
  }

  /**
   * @return true if no author is left to compare
   */
  public boolean isEmpty() {
    return loose.length() == 3; // nothing but the slot separators
  }
}
