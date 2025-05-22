package life.catalogue.matching.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/** Utility class knowing the url layout of rs.gbif.org to access authority and dictionary files. */
@Service
@Slf4j
public class Dictionaries {

  private static final Logger LOG = LoggerFactory.getLogger(Dictionaries.class);
  public static final String FILENAME_BLACKLIST = "blacklisted.txt";

  String dictionaryPath;

  public Dictionaries(@Value("${dictionary.path:dictionaries}") String dictionaryPath) {
    this.dictionaryPath = dictionaryPath;
  }

  public static Dictionaries createDefault() {
    return new Dictionaries("dictionaries");
  }

  /**
   * Retrieves an InputStream for a synonyms dictionary file.
   *
   * @param domain   The domain of the requested synonyms file.
   * @param filename The name of the synonyms file.
   * @return An InputStream to the file inside the synonyms dictionary folder of rs.gbif.org, or null if not found.
   * @throws IOException If an I/O error occurs.
   */
  public InputStream getDictionaryInputStream(String domain, String filename) throws IOException {
    String filePath = String.join("/", dictionaryPath, domain, filename);

    if (!dictionaryPath.startsWith("http")) {
      log.info("Loading dictionary file from resources: {}", filePath);
      ClassPathResource resource = new ClassPathResource(filePath, getClass().getClassLoader());

      if (resource.exists()) {
        return resource.getURL().openStream();
      } else {
        log.warn("Failed to load as resource stream: {}", filePath);
        return null;
      }
    }

    try {
      URL url = new URL(filePath);
      return url.openStream();
    } catch (MalformedURLException e) {
      LOG.error("Invalid URL for path: {}", filePath, e);
    }

    return null;
  }

}
