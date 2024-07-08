package life.catalogue.matching.util;

import java.net.MalformedURLException;
import java.net.URL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Utility class knowing the url layout of rs.gbif.org to access authority and dictionary files. */
@Service
public class Dictionaries {

  private static final Logger LOG = LoggerFactory.getLogger(Dictionaries.class);
  public static final String FILENAME_BLACKLIST = "blacklisted.txt";

  String dictionaryPath;

  public Dictionaries(@Value("${dictionary.path: dictionaries/}") String dictionaryPath) {
    this.dictionaryPath = dictionaryPath;
  }

  public static Dictionaries createDefault() {
    return new Dictionaries("dictionaries/");
//    return new Dictionaries("https://rs.gbif.org/dictionaries/");
  }

  /**
   * @param path given as array of individual names that will be concatenated
   * @return url to file inside rs.gbif.org
   */
  private static URL url(String domain, String... path) {
    try {
      if (!domain.startsWith("http")) {
        return Dictionaries.class.getClassLoader().getResource(domain + String.join("/", path));
      } else {
        return new URL(domain + String.join("/", path));
      }
    } catch (MalformedURLException e) {
      LOG.error("Cannot insert " + domain+ " for path " + String.join("/", path), e);
    }
    return null;
  }

  /**
   * @param filename of authority dictionary file requested
   * @return url to file inside to authority folder of rs.gbif.org
   */
  public URL authorityUrl( String filename) {
    return url(dictionaryPath, "authority", filename);
  }

  /**
   * @param filename of synonyms file requested
   * @return url to file inside to synonyms dictionary folder of rs.gbif.org
   */
  public URL synonymUrl(String filename) {
    return url(dictionaryPath, "synonyms", filename);
  }
}
