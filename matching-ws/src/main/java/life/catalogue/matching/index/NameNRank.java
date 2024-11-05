package life.catalogue.matching.index;

import static life.catalogue.common.tax.NameFormatter.HYBRID_MARKER;

import com.google.common.annotations.VisibleForTesting;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import life.catalogue.matching.model.Classification;
import life.catalogue.matching.model.LinneanClassification;
import life.catalogue.matching.util.CleanupUtils;

import org.apache.commons.lang3.StringUtils;
import org.gbif.nameparser.api.NamePart;
import org.gbif.nameparser.api.ParsedName;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utilit class to construct a full scientific name with authorship and rank from various input parameters.
 */
public class NameNRank {
  private static final Logger LOG = LoggerFactory.getLogger(NameNRank.class);
  private static final List<Rank> REVERSED_DWC_RANKS = new ArrayList<>(Rank.DWC_RANKS);

  static {
    Collections.reverse(REVERSED_DWC_RANKS);
  }

  private static final Pattern BINOMIAL =
      Pattern.compile("^\\s*([A-Z][a-z]+)\\s+([a-z1-9-]+)\\s*$");

  public final String name;
  public final Rank rank;

  public NameNRank(String name, Rank rank) {
    this.name = name;
    this.rank = rank;
  }

  /**
   * Construct the best possible full name with authorship and rank out of various input parameters
   *
   * @param scientificName the full scientific name
   * @param authorship the authorship
   * @param genericName the generic name
   * @param specificEpithet the species epithet
   * @param infraSpecificEpithet the infraspecific epithet
   * @param rank the rank
   * @param classification the classification
   * @return a new NameNRank instance
   */
  public static NameNRank build(
      @Nullable String scientificName,
      @Nullable String authorship,
      @Nullable String genericName,
      @Nullable String specificEpithet,
      @Nullable String infraSpecificEpithet,
      @Nullable Rank rank,
      @Nullable LinneanClassification classification) {

    // make sure we have a classification instance
    classification = classification == null ? new Classification() : classification;
    final String genus = clean(CleanupUtils.first(genericName, classification.getGenus()));
    // If given primarily trust the scientific name, especially since these can be unparsable names
    // like OTUs
    // only exceptions is when the scientific name clearly is just a part of the atoms - then
    // reassemble it
    // authorship can be appended as this is a very common case
    if (exists(scientificName)
        && useScientificName(
            scientificName, genericName, specificEpithet, infraSpecificEpithet, classification)) {
      // expand abbreviated or placeholder genus?
      scientificName = expandAbbreviatedGenus(scientificName, genus);
      // missing authorship?
      scientificName = appendAuthorship(scientificName, authorship);
      // ignore atomized name parameters, but warn if not present
      warnIfMissing(scientificName, genus, "genus");
      warnIfMissing(scientificName, specificEpithet, "specificEpithet");
      warnIfMissing(scientificName, infraSpecificEpithet, "infraSpecificEpithet");
      return new NameNRank(scientificName, rank);

    } else {
      // no name given, assemble from pieces as best as we can
      Rank clRank = lowestRank(classification);
      if (genus == null && (clRank == null || clRank.isSuprageneric())) {
        // use epithets if existing - otherwise higher rank if given
        if (anyNonEmpty(specificEpithet, infraSpecificEpithet, authorship)) {
          // we dont have any genus or species binomen given, just epithets :(
          StringBuilder sb = new StringBuilder();
          sb.append("?"); // no genus
          appendIfExists(sb, specificEpithet);
          appendIfExists(sb, infraSpecificEpithet);
          appendIfExists(sb, authorship);
          return new NameNRank(sb.toString(), rank);

        } else if (clRank != null) {
          return new NameNRank(classification.getHigherRank(clRank), clRank);
        } else {
          return new NameNRank(null, rank);
        }

      } else {
        // try atomized
        ParsedName pn = new ParsedName();
        pn.setGenus(genus);
        pn.setInfragenericEpithet(clean(classification.getSubgenus()));
        pn.setSpecificEpithet(clean(specificEpithet));
        pn.setInfraspecificEpithet(clean(infraSpecificEpithet));
        pn.setRank(rank);
        // see if species rank in classification can contribute sth
        if (exists(classification.getSpecies())) {
          Matcher m = BINOMIAL.matcher(clean(classification.getSpecies()));
          if (m.find()) {
            if (pn.getGenus() == null) {
              pn.setGenus(m.group(1));
            }
            if (pn.getSpecificEpithet() == null) {
              pn.setSpecificEpithet(m.group(2));
            }
          } else if (pn.getSpecificEpithet() == null && StringUtils.isAllLowerCase(classification.getSpecies())
              && !clean(classification.getSpecies()).contains(" ")) {
            // sometimes the field is wrongly used as the species epithet
            pn.setSpecificEpithet(clean(classification.getSpecies()));
          }
        }
        // append author - we don't break it down into parsed name authorships but keep it as one thing
        var cleanAuth = clean(authorship);
        if (cleanAuth != null) {
          return new NameNRank(pn.canonicalNameComplete() + " " + cleanAuth, rank);
        }
        return new NameNRank(pn.canonicalNameComplete(), rank);
      }
    }
  }

  public void setGenus(ParsedName parsedName, String genus) {
    if (genus != null && genus.startsWith(String.valueOf(HYBRID_MARKER))) {
      parsedName.setGenus(genus.substring(1));
      parsedName.setNotho(NamePart.GENERIC);
    } else {
      parsedName.setGenus(genus);
    }
  }

  private static String clean(String x) {
    x = StringUtils.trimToNull(x);
    if (x != null) {
      switch (x) {
        case "\\N":
        case "null":
        case "Null":
        case "NULL":
          return null;
        default:
      }
    }
    return x;
  }

  private static boolean anyNonEmpty(String... x) {
    if (x != null) {
      for (String y : x) {
        if (exists(y)) return true;
      }
    }
    return false;
  }

  private static void appendIfExists(StringBuilder sb, @Nullable String x) {
    if (exists(x)) {
      if (sb.length() > 0) {
        sb.append(" ");
      }
      sb.append(x.trim());
    }
  }

  private static boolean useScientificName(
      String scientificName,
      @Nullable String genericName,
      @Nullable String specificEpithet,
      @Nullable String infraSpecificEpithet,
      LinneanClassification cl) {
    // without genus given we cannot assemble the name, so lets then just use it as it is
    if (exists(cl.getGenus()) || exists(genericName) || isSimpleBinomial(cl.getSpecies())) {
      // scientific name is just one of the epithets
      if (StringUtils.isAllLowerCase(scientificName)
          && (scientificName.equals(specificEpithet)
              || scientificName.equals(infraSpecificEpithet)
              || scientificName.equals(cl.getSpecies()))) {
        return false;
      }
    }
    return true;
  }

  public static boolean isSimpleBinomial(String name) {
    return exists(name) && BINOMIAL.matcher(name).matches();
  }

  private static void warnIfMissing(String name, @Nullable String epithet, String part) {
    if (exists(epithet) && name != null && !name.toLowerCase().contains(epithet.toLowerCase())) {
      LOG.debug("ScientificName >{}< missing {}: {}", name, part, epithet);
    }
  }

  private static boolean exists(String x) {
    return StringUtils.isNotBlank(x);
  }

  @VisibleForTesting
  public static String expandAbbreviatedGenus(String scientificName, String genus) {
    if (exists(scientificName) && exists(genus) && !scientificName.equalsIgnoreCase(genus)) {
      String[] parts = scientificName.split(" +", 2);
      String genusCorrect = StringUtils.capitalize(genus.trim().toLowerCase());
      if (parts[0].length() <= 2 && genusCorrect.length() > 2 && (
        parts[0].equals("?") // is the genus missing alltogether?
          || parts[0].length() == 2 && parts[0].charAt(1) == '.' && parts[0].charAt(0) == genusCorrect.charAt(0)
          || parts[0].length() == 1 && parts[0].charAt(0) == genusCorrect.charAt(0)
      )) {
        StringBuilder sb = new StringBuilder();
        sb.append(genus);
        if (parts.length > 1) {
          sb.append(" ")
            .append(parts[1]);
        }
        return sb.toString();
      }
    }
    return scientificName;
  }

  @VisibleForTesting
  public static String appendAuthorship(String scientificName, String authorship) {
    if (!StringUtils.isBlank(scientificName)
        && !StringUtils.isBlank(authorship)
        && !scientificName.toLowerCase().contains(authorship.trim().toLowerCase())) {
      return scientificName.trim() + " " + authorship.trim();
    }
    return StringUtils.trimToNull(scientificName);
  }

  private static Rank lowestRank(LinneanClassification cl) {
    for (Rank r : REVERSED_DWC_RANKS) {
      if (exists(cl.getHigherRank(r))) {
        return r;
      }
    }
    return null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof NameNRank )) return false;
    NameNRank nameNRank = (NameNRank) o;
    return Objects.equals(name, nameNRank.name) && rank == nameNRank.rank;
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, rank);
  }

  @Override
  public String toString() {
    return name + " [" + rank + "]";
  }
}
