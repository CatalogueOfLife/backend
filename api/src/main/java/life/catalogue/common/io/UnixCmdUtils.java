package life.catalogue.common.io;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * File sorting using native unix tools
 */
public class UnixCmdUtils {
  private static final Logger LOG = LoggerFactory.getLogger(UnixCmdUtils.class);

  public static void sort(File f) {
    try {
      String cmd = String.format("export LC_CTYPE=en_US.UTF-8; sort -o %s.tmp %s && mv %s.tmp %s",
        f.getAbsolutePath(), f.getAbsolutePath(), f.getAbsolutePath(), f.getAbsolutePath()
      );
      LOG.debug("Sort {} with: {}", f, cmd);
      ProcessBuilder pb = new ProcessBuilder("bash", "-c", cmd + "; exit 0");

      Process ps = pb.start();
      // limit to 30s, see https://stackoverflow.com/questions/37043114/how-to-stop-a-command-being-executed-after-4-5-seconds-through-process-builder/37065167#37065167
      if (!ps.waitFor(30, TimeUnit.SECONDS)) {
        LOG.warn("Sorting of file {} has timed out after 30s", f.getName());
        ps.destroy(); // make sure we leave no process behind
      }
      int status = ps.waitFor();
      if (status != 0) {
        String error = InputStreamUtils.readEntireStream(ps.getErrorStream());
        throw new RuntimeException("Unix sort failed with status " + status + ": " + error);
      }

    } catch (IOException e) {
      throw new RuntimeException("Unix sort failed", e);

    } catch (InterruptedException e) {
      throw new RuntimeException("Unix sort was interrupted", e);
    }
  }
}
