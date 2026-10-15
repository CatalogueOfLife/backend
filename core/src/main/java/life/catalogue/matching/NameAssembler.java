package life.catalogue.matching;

import life.catalogue.api.model.Classification;

import org.gbif.nameparser.api.ParsedName;
import org.gbif.nameparser.api.Rank;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

/**
 * Builds the best full name to match from the many ways a name comes in from outside: a scientific name, an
 * authorship, the atomized generic name and epithets, and a classification. Occurrence data often give only some of
 * them, an abbreviated genus or the epithet alone in the name field.
 * A port of NameNRank from GBIF's matching-ws (https://github.com/gbif/matching-ws, Apache License 2.0).
 */
public class NameAssembler {
  private static final Pattern BINOMIAL = Pattern.compile("^\\s*([A-Z][a-z]+)\\s+([a-z1-9-]+)\\s*$");

  /**
   * @param name the full name to match incl its authorship, null if nothing can be built
   * @param rank the rank to match it with
   * @param fromClassification true if the name is the lowest taxon of the classification, as nothing else was given
   */
  public record Result(@Nullable String name, @Nullable Rank rank, boolean fromClassification) {}

  private NameAssembler() {}

  /**
   * The scientific name is trusted if given, as it can be an unparsable OTU, unless it is clearly just one of the
   * epithets. An abbreviated or missing genus is then expanded and a missing authorship appended. Without a usable
   * scientific name it is assembled from the genus, subgenus and epithets, or else the lowest taxon of the
   * classification is used.
   */
  public static Result build(@Nullable String scientificName, @Nullable String authorship, @Nullable String genericName,
                             @Nullable String specificEpithet, @Nullable String infraspecificEpithet, @Nullable Rank rank,
                             @Nullable Classification classification) {
    final Classification cl = classification == null ? new Classification() : classification;
    scientificName = clean(scientificName);
    authorship = clean(authorship);
    specificEpithet = clean(specificEpithet);
    infraspecificEpithet = clean(infraspecificEpithet);
    final String genus = clean(StringUtils.firstNonBlank(genericName, cl.getGenus()));

    if (scientificName != null && useScientificName(scientificName, genericName, specificEpithet, infraspecificEpithet, cl)) {
      String name = expandAbbreviatedGenus(scientificName, genus);
      return new Result(appendAuthorship(name, authorship), rank, false);
    }

    // no usable name given, assemble it from the pieces as best we can
    Rank clRank = cl.getLowestExistingRank();
    if (genus == null && (clRank == null || clRank.isSuprageneric())) {
      if (specificEpithet != null || infraspecificEpithet != null || authorship != null) {
        // only epithets without any genus - nothing that can be matched
        return new Result(null, rank, false);
      } else if (clRank != null) {
        return new Result(clean(cl.getByRank(clRank)), clRank, true);
      }
      return new Result(null, rank, false);
    }

    ParsedName pn = new ParsedName();
    pn.setGenus(genus);
    pn.setInfragenericEpithet(clean(cl.getSubgenus()));
    pn.setSpecificEpithet(specificEpithet);
    pn.setInfraspecificEpithet(infraspecificEpithet);
    pn.setRank(rank);
    // see if the species of the classification can contribute something
    String species = clean(cl.getSpecies());
    if (species != null) {
      Matcher m = BINOMIAL.matcher(species);
      if (m.find()) {
        if (pn.getGenus() == null) {
          pn.setGenus(m.group(1));
        }
        if (pn.getSpecificEpithet() == null) {
          pn.setSpecificEpithet(m.group(2));
        }
      } else if (pn.getSpecificEpithet() == null && StringUtils.isAllLowerCase(species) && !species.contains(" ")) {
        // sometimes the field is wrongly used for the species epithet
        pn.setSpecificEpithet(species);
      }
    }
    String name = pn.canonicalNameWithoutAuthorship();
    return new Result(authorship == null ? name : name + " " + authorship, rank, false);
  }

  /**
   * @return null for blank values and the literal null values of database dumps
   */
  private static String clean(String x) {
    x = StringUtils.trimToNull(x);
    if (x != null) {
      switch (x) {
        case "\\N", "null", "Null", "NULL" -> {
          return null;
        }
        default -> {}
      }
    }
    return x;
  }

  /**
   * A scientific name that is just one of the epithets is not used if a genus is known to assemble the name from.
   */
  private static boolean useScientificName(String scientificName, @Nullable String genericName, @Nullable String specificEpithet,
                                           @Nullable String infraspecificEpithet, Classification cl) {
    if (StringUtils.isNotBlank(cl.getGenus()) || StringUtils.isNotBlank(genericName) || isSimpleBinomial(cl.getSpecies())) {
      return !(StringUtils.isAllLowerCase(scientificName)
        && (scientificName.equals(specificEpithet) || scientificName.equals(infraspecificEpithet) || scientificName.equals(cl.getSpecies())));
    }
    return true;
  }

  static boolean isSimpleBinomial(@Nullable String name) {
    return StringUtils.isNotBlank(name) && BINOMIAL.matcher(name).matches();
  }

  /**
   * Replaces an abbreviated (A. or A) or missing (?) genus with the given one if its first letter agrees.
   */
  static String expandAbbreviatedGenus(String scientificName, @Nullable String genus) {
    if (StringUtils.isNotBlank(scientificName) && StringUtils.isNotBlank(genus) && !scientificName.equalsIgnoreCase(genus)) {
      String[] parts = scientificName.split(" +", 2);
      String genusCorrect = StringUtils.capitalize(genus.trim().toLowerCase());
      if (parts[0].length() <= 2 && genusCorrect.length() > 2 && (
        parts[0].equals("?")
          || parts[0].length() == 2 && parts[0].charAt(1) == '.' && parts[0].charAt(0) == genusCorrect.charAt(0)
          || parts[0].length() == 1 && parts[0].charAt(0) == genusCorrect.charAt(0)
      )) {
        return parts.length > 1 ? genus + " " + parts[1] : genus;
      }
    }
    return scientificName;
  }

  /**
   * Appends the authorship unless the name already contains it.
   */
  static String appendAuthorship(String scientificName, @Nullable String authorship) {
    if (StringUtils.isNotBlank(scientificName) && StringUtils.isNotBlank(authorship)
      && !scientificName.toLowerCase().contains(authorship.trim().toLowerCase())) {
      return scientificName.trim() + " " + authorship.trim();
    }
    return StringUtils.trimToNull(scientificName);
  }
}
