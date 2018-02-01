package org.col.dw.api;

import com.google.common.base.Strings;
import org.apache.commons.lang3.text.WordUtils;
import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.Rank;

import java.util.Calendar;
import java.util.Random;


/**
 * Utils class adding specific string methods to existing guava {@link Strings} and
 * commons {@link org.apache.commons.lang3.StringUtils}.
 */
public class RandomUtils {
  public static final int LINNEAN_YEAR = 1751;
  private static final String CONS = "BCDFGHJKLMNPQRSTVWXYZ";
  private static final String VOC = "AEIOU";
  private static Random rnd = new Random();

  private RandomUtils() {
  }

  /**
   * Creates a random species binomial with no meaning at all, but highly randomized.
   *
   * @return a random canonical species name
   */
  public static String randomSpecies() {
    return randomGenus() + " " + randomEpithet();
  }

  public static String randomGenus() {
    return WordUtils.capitalize(randomString(rnd.nextInt(9) + 3).toLowerCase());
  }

  public static String randomEpithet() {
    return randomString(rnd.nextInt(12) + 4).toLowerCase();
  }
  public static String randomFamily() {
      return WordUtils.capitalize(RandomUtils.randomString(rnd.nextInt(15) + 5).toLowerCase()) + "idae";
  }

  public static String randomAuthor() {
    return WordUtils.capitalize(RandomUtils.randomString(rnd.nextInt(12) + 1).toLowerCase());
  }

  public static Authorship randomAuthorship() {
    Authorship auth = new Authorship();
    while (rnd.nextBoolean()) {
      auth.getAuthors().add(randomAuthor());
    }
    if (rnd.nextBoolean()) {
      auth.setYear(randomSpeciesYear());
    }

    while (rnd.nextInt(10) == 1) {
      auth.getExAuthors().add(randomAuthor());
    }
    return auth;
  }

  public static Rank randomRank() {
    switch (rnd.nextInt(10)) {
      case 1: return Rank.values()[ rnd.nextInt(Rank.values().length) ];
      case 2: return Rank.values()[ rnd.nextInt(Rank.FAMILY.ordinal()) ];
      case 3: return Rank.FAMILY;
      case 4: return Rank.GENUS;
      case 5: return Rank.SUBSPECIES;
      case 6: return Rank.values()[ Rank.SPECIES.ordinal() + rnd.nextInt(Rank.values().length - Rank.SPECIES.ordinal()) ];
      default: return Rank.SPECIES;
    }
  }

  public static Name randomName() {
    Name n = new Name();
    n.setCombinationAuthorship(randomAuthorship());
    Rank rank = randomRank();
    n.setRank(rank);
    if (rank == Rank.SPECIES) {
      n.setScientificName(randomSpecies());
    } else if (rank.isInfraspecific()) {
      n.setScientificName(randomSpecies() + " " + rank.getMarker() + " " + randomEpithet());
    } else if (rank == Rank.FAMILY) {
      n.setScientificName(randomFamily());
    } else {
      n.setScientificName(randomGenus());
    }
    return n;
  }

  /**
   * Creates a random string in upper case of given length with purely latin characters only.
   * Vocals are used much more frequently than consonants
   * @param len
   * @return a random string in upper case
   */
  public static String randomString(int len) {
    StringBuilder sb = new StringBuilder(len);
    for (int i = 0; i < len; i++) {
      if (rnd.nextInt(3) > 1) {
        sb.append(CONS.charAt(rnd.nextInt(CONS.length())));
      } else {
        sb.append(VOC.charAt(rnd.nextInt(VOC.length())));
      }
    }

    return sb.toString();
  }

  /**
   * @return a year since Linnéan times 1751 before now as a 4 character long string
   */
  public static String randomSpeciesYear() {
    int maxYear = Calendar.getInstance().get(Calendar.YEAR);
    return String.valueOf(LINNEAN_YEAR + rnd.nextInt(maxYear - LINNEAN_YEAR + 1));
  }

}
