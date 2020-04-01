package life.catalogue.api;

import java.net.URI;
import java.util.Calendar;
import java.util.Random;
import java.util.regex.Pattern;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.text.WordUtils;
import life.catalogue.api.model.Name;
import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.Rank;


/**
 * Utils class adding specific string methods to existing guava {@link Strings} and
 * commons {@link org.apache.commons.lang3.StringUtils}.
 */
public class RandomUtils {
  public static final int LINNEAN_YEAR = 1751;
  private static final String CONS = "BCDFGHJKLMNPQRSTVWXYZ";
  private static final String VOC = "AEIOU";
  private static Random rnd = new Random();
  private static final Pattern REPL_NULL = Pattern.compile("\u0000");
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
    return WordUtils.capitalize(randomLatinString(rnd.nextInt(9) + 3).toLowerCase());
  }
  
  public static String randomEpithet() {
    return randomLatinString(rnd.nextInt(12) + 4).toLowerCase();
  }
  
  public static String randomFamily() {
    return WordUtils.capitalize(RandomUtils.randomLatinString(rnd.nextInt(15) + 5).toLowerCase()) + "idae";
  }
  
  public static String randomAuthor() {
    return WordUtils.capitalize(RandomUtils.randomLatinString(rnd.nextInt(12) + 1).toLowerCase());
  }
  
  public static Authorship randomAuthorship() {
    Authorship auth = new Authorship();
    while (rnd.nextBoolean()) {
      auth.addAuthor(randomAuthor());
    }
    if (rnd.nextBoolean()) {
      auth.setYear(randomSpeciesYear());
    }
    
    while (rnd.nextInt(10) == 1) {
      auth.addExAuthor(randomAuthor());
    }
    return auth;
  }
  
  public static Rank randomRank() {
    switch (rnd.nextInt(10)) {
      case 1:
        return Rank.values()[rnd.nextInt(Rank.values().length)];
      case 2:
        return Rank.values()[rnd.nextInt(Rank.FAMILY.ordinal())];
      case 3:
        return Rank.FAMILY;
      case 4:
        return Rank.GENUS;
      case 5:
        return Rank.SUBSPECIES;
      case 6:
        return Rank.values()[Rank.SPECIES.ordinal() + rnd.nextInt(Rank.values().length - Rank.SPECIES.ordinal())];
      default:
        return Rank.SPECIES;
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
   *
   * @param len
   * @return a random string in upper case
   */
  public static String randomLatinString(int len) {
    Preconditions.checkArgument(len > 0);
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
   * A random string of unicode chars excluding only the \u0000 null value
   * which cannot be stored in Postgres.
   */
  public static String randomUnicodeString(int len) {
    Preconditions.checkArgument(len > 0);
    return REPL_NULL.matcher(RandomStringUtils.random(len)).replaceAll(" ");
  }
  
  /**
   * Creates a random URI using the http or https protocol.
   */
  public static URI randomUri() {
    StringBuilder sb = new StringBuilder();
    switch (rnd.nextInt(10)) {
      case 1: sb.append("https"); break;
      case 2: sb.append("ftp"); break;
      default: sb.append("http");
    }
    sb.append("://www.")
        .append(randomLatinString(8).toLowerCase())
        .append(".com");
    if (rnd.nextBoolean()) {
      sb.append("/").append(randomLatinString(4).toLowerCase())
        .append("/").append(Integer.toString(Math.abs(rnd.nextInt())));
      
      if (rnd.nextBoolean()) {
        sb.append("#").append(randomLatinString(14));
      }
    }
    return URI.create(sb.toString());
  }

  /**
   * @return a year since Linnéan times 1751 before now as a 4 character long string
   */
  public static String randomSpeciesYear() {
    int maxYear = Calendar.getInstance().get(Calendar.YEAR);
    return String.valueOf(LINNEAN_YEAR + rnd.nextInt(maxYear - LINNEAN_YEAR + 1));
  }
  
  public static <T> T populate(T instance) {
    Class<T> c = (Class<T>) instance.getClass();
    
    return instance;
  }
  
}
