package life.catalogue.matching.authorship.corpus;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Enumeration;
import java.util.Properties;

/**
 * The name parser the corpus tools run with, to be written into what they produce: a corpus is only comparable to
 * another parsed by the same parser. A snapshot keeps its version across builds, so the date of its newest jar comes
 * with it - the native classifier jar holds the parser and changes while the java binding jar often does not.
 */
public class NameParserVersion {
  private static final String POM = "META-INF/maven/org.gbif.nameparser/name-parser-rust/pom.properties";

  private NameParserVersion() {
  }

  public static String get() {
    try {
      String version = "unknown";
      Path newest = null;
      FileTime newestTime = null;
      Enumeration<URL> poms = NameParserVersion.class.getClassLoader().getResources(POM);
      while (poms.hasMoreElements()) {
        URL url = poms.nextElement();
        try (InputStream in = url.openStream()) {
          Properties p = new Properties();
          p.load(in);
          version = p.getProperty("version", version);
        }
        if ("jar".equals(url.getProtocol())) {
          Path jar = Path.of(URI.create(url.getPath().substring(0, url.getPath().indexOf("!/"))));
          FileTime t = Files.getLastModifiedTime(jar);
          if (newestTime == null || t.compareTo(newestTime) > 0) {
            newest = jar;
            newestTime = t;
          }
        }
      }
      return version + " (" + (newest == null ? "no jar" : newest.getFileName() + " of " + newestTime) + ")";
    } catch (Exception e) {
      return "unknown (" + e.getMessage() + ")";
    }
  }
}
