package life.catalogue.basgroup;

import life.catalogue.api.model.ConsolidationName;
import life.catalogue.api.model.FormattableName;
import life.catalogue.common.tax.NameFormatter;
import life.catalogue.matching.similarity.ModifiedDamerauLevenshtein;

import java.util.Objects;

import javax.annotation.Nullable;

/**
 * Decides how two names of the homotypic grouping relate by their spelling alone:
 * the same name, an orthographic variant or a duplicate.
 */
public class HomotypicRules {
  private static final int MAX_NAME_DIFF = 1;
  private static final int MAX_GENUS_DIFF = 1;
  private static final ModifiedDamerauLevenshtein GENUS_DISTANCE = new ModifiedDamerauLevenshtein();

  private HomotypicRules() {
  }

  /**
   * @return true if both names have the same rank and authorship and their names differ by a single edit at most
   */
  public static boolean isSameName(ConsolidationName n1, ConsolidationName n2, ModifiedDamerauLevenshtein mdl) {
    return n1.getRank() == n2.getRank()
      && Objects.equals(n1.getAuthorship(), n2.getAuthorship())
      && Math.abs(n1.getName().length()-n2.getName().length()) <= MAX_NAME_DIFF
      && mdl.getEditDistance(n1.getName(), n2.getName()) <= MAX_NAME_DIFF;
  }

  /**
   * @return true if both names are the same name and rank spelled differently either in their terminal epithet,
   *   e.g. Trachelosiphon colombianum and T. columbianum or Aus albus and Aus alba,
   *   or in their genus by a single edit, e.g. Coriphaena hippurus and Coryphaena hippurus.
   *   A subgenus is not part of the name and is ignored.
   */
  public static boolean isOrthographicVariant(FormattableName n1, FormattableName n2) {
    if (n1.getRank() != n2.getRank()
      || n1.getGenus() == null || n2.getGenus() == null
      || n1.getTerminalEpithet() == null || n2.getTerminalEpithet() == null
      || n1.getInfraspecificEpithet() != null && !equalsIgnoreCase(n1.getSpecificEpithet(), n2.getSpecificEpithet())
    ) {
      return false;
    }
    boolean sameEpithet = n1.getTerminalEpithet().equalsIgnoreCase(n2.getTerminalEpithet());
    if (n1.getGenus().equalsIgnoreCase(n2.getGenus())) {
      return !sameEpithet;
    }
    return sameEpithet && GENUS_DISTANCE.getEditDistance(n1.getGenus().toLowerCase(), n2.getGenus().toLowerCase()) <= MAX_GENUS_DIFF;
  }

  /**
   * @return true if both names are the same name, i.e. the same rank and canonical name.
   *   A subgenus is not part of a species name, so Machimus erevanensis and Machimus (Machimus) erevanensis are duplicates.
   */
  public static boolean isDuplicate(FormattableName n1, FormattableName n2) {
    return n1.getRank() == n2.getRank() && equalsIgnoreCase(NameFormatter.canonicalName(n1), NameFormatter.canonicalName(n2));
  }

  private static boolean equalsIgnoreCase(@Nullable String s1, @Nullable String s2) {
    return s1 == null ? s2 == null : s1.equalsIgnoreCase(s2);
  }
}
