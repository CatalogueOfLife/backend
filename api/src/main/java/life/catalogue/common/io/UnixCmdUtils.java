package life.catalogue.common.io;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * File sorting using native unix tools
 */
public class UnixCmdUtils {
  private static final Logger LOG = LoggerFactory.getLogger(UnixCmdUtils.class);

  public static void sortUTF8(File f, int timeout) {
    sort(f, "en_US.UTF-8", timeout);
  }

  public static void sortC(File f, int timeout) {
    sort(f, "C", timeout);
  }

  /**
   * @param lcCtype file sorting locale
   * @param timeout maximum time in seconds to wait for the sorting to complete
   */
  public static void sort(File f, String lcCtype, int timeout) {
    try {
      String cmd = String.format("export LC_CTYPE=%s; sort -o %s.tmp %s && mv %s.tmp %s",
        lcCtype, f.getAbsolutePath(), f.getAbsolutePath(), f.getAbsolutePath(), f.getAbsolutePath()
      );
      LOG.debug("Sort {} with: {}", f, cmd);
      ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", cmd + "; exit 0");

      Process ps = pb.start();
      if (timeout > 0) {
        // limit to 30s, see https://stackoverflow.com/questions/37043114/how-to-stop-a-command-being-executed-after-4-5-seconds-through-process-builder/37065167#37065167
        if (!ps.waitFor(timeout, TimeUnit.SECONDS)) {
          LOG.warn("Sorting of file {} has timed out after {}s", f.getName(), timeout);
          ps.destroy(); // make sure we leave no process behind
        }
      }
      int status = ps.waitFor();
      if (status != 0) {
        String error = InputStreamUtils.readEntireStream(ps.getErrorStream());
        LOG.error(error);
        throw new RuntimeException("Unix sort failed with status " + status + ": " + error);
      }

    } catch (IOException e) {
      throw new RuntimeException("Unix sort failed", e);

    } catch (InterruptedException e) {
      throw new RuntimeException("Unix sort was interrupted", e);
    }
  }

  public static void split(File f, long lines, int suffixLength) {
    split(f, f.getParentFile(), lines, suffixLength);
  }
  public static void split(File f, File dir, long lines, int suffixLength) {
    try {
      String cmd = String.format("split -d -a %s -l %s %s %s", suffixLength, lines, f.getAbsolutePath(), f.getAbsolutePath());
      LOG.info("Sort {} with: {}", f, cmd);
      ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", cmd + "; exit 0");

      Process ps = pb.start();
      int status = ps.waitFor();
      if (status != 0) {
        String error = InputStreamUtils.readEntireStream(ps.getErrorStream());
        LOG.error(error);
        throw new RuntimeException("Unix split failed with status " + status + ": " + error);
      }

    } catch (IOException e) {
      throw new RuntimeException("Unix split failed", e);

    } catch (InterruptedException e) {
      throw new RuntimeException("Unix split was interrupted", e);
    }
  }
}
