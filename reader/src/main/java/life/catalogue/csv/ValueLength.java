package life.catalogue.csv;

import org.gbif.dwc.terms.Term;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Conservative maximum lengths of verbatim values by the kind of their term.
 * A value above them usually means a broken delimiter or quotation merged several columns or records into one,
 * see https://github.com/CatalogueOfLife/backend/issues/1233
 */
public class ValueLength {
  static final int MAX_ID = 200;
  static final int MAX_NAME = 500;
  static final int MAX_TEXT = 5000;
  // terms that can hold a list of identifiers are judged per identifier
  private static final Pattern ID_LIST_DELIMITER = Pattern.compile("[,;|]");
  // name parts and authorships, compared by their lower case simple name
  private static final Set<String> NAME_TERMS = Set.of(
    "scientificname", "authorship", "scientificnameauthorship", "combinationauthorship", "basionymauthorship",
    "uninomial", "genus", "genericname", "subgenus", "infragenericepithet", "specificepithet", "infraspecificepithet",
    "cultivarepithet", "speciesep", "infraspeciesep", "authorstring", "infraspeciesauthorstring"
  );
  // long free text without any limit
  private static final Set<String> FREE_TEXT_TERMS = Set.of(
    "description", "document", "treatment", "text", "content", "abstract"
  );

  private ValueLength() {
  }

  /**
   * @return true if the value is far longer than expected for its term
   */
  public static boolean isUnusual(Term term, String value) {
    if (value == null || value.length() <= MAX_ID) {
      return false;
    }
    String name = term.simpleName();
    if (isIdentifier(name)) {
      for (String id : ID_LIST_DELIMITER.split(value)) {
        if (id.trim().length() > MAX_ID) {
          return true;
        }
      }
      return false;
    }
    String lower = name.toLowerCase();
    if (NAME_TERMS.contains(lower)) {
      return value.length() > MAX_NAME;
    }
    if (FREE_TEXT_TERMS.contains(lower)) {
      return false;
    }
    return value.length() > MAX_TEXT;
  }

  private static boolean isIdentifier(String name) {
    return name.endsWith("ID") || name.endsWith("Id") || name.equalsIgnoreCase("identifier");
  }
}
