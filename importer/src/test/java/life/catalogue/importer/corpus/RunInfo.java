package life.catalogue.importer.corpus;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Enumeration;
import java.util.Properties;

/**
 * What produced a corpus run, written next to its output: two runs only tell a change apart when it is clear which
 * code and which name parser each ran with.
 */
class RunInfo {
  private static final String POM = "META-INF/maven/org.gbif.nameparser/name-parser-rust/pom.properties";

  private RunInfo() {
  }

  /**
   * @return the commit of the working directory, marked dirty if tracked files differ from it
   */
  static String git() {
    try {
      String sha = exec("git", "rev-parse", "--short", "HEAD").trim();
      String branch = exec("git", "rev-parse", "--abbrev-ref", "HEAD").trim();
      boolean dirty = !exec("git", "status", "--porcelain", "--untracked-files=no").isBlank();
      return sha + " (" + branch + (dirty ? ", with uncommitted changes" : "") + ")";
    } catch (Exception e) {
      return "unknown (" + e.getMessage() + ")";
    }
  }

  private static String exec(String... cmd) throws IOException, InterruptedException {
    Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
    String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    if (p.waitFor() != 0) {
      throw new IOException(String.join(" ", cmd) + ": " + out.trim());
    }
    return out;
  }

  /**
   * A snapshot keeps its version across builds, so the date of the newest name-parser-rust jar comes with it:
   * the native classifier jar holds the parser and changes while the java binding jar often does not.
   */
  static String nameParser() {
    try {
      String version = "unknown";
      Path newest = null;
      FileTime newestTime = null;
      Enumeration<URL> poms = RunInfo.class.getClassLoader().getResources(POM);
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
