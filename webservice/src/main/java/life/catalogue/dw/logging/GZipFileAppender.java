package life.catalogue.dw.logging;

import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.util.FileUtil;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

/**
 * File appender that uses a GZIPOutputStream to write to the file.
 */
public class GZipFileAppender<E> extends FileAppender<E> {

  @Override
  public boolean isAppend() {
    return false;
  }

  @Override
  public void setAppend(boolean append) {
    // ignore, we never append
  }

  /**
   * We copy most of the underlying FileAppender behavior, but use a ZipOutputStream instead of a Resilient one.
   *
   * <p>
   * Sets and <i>opens</i> the file where the log output will go. The specified
   * file must be writable.
   *
   * <p>
   * If there was already an opened file, then the previous file is closed
   * first.
   *
   * <p>
   * <b>Do not use this method directly. To configure a FileAppender or one of
   * its subclasses, set its properties one by one and then call start().</b>
   *
   * @param file_name
   *          The path to the log file.
   */
  @Override
  public void openFile(String file_name) throws IOException {
    lock.lock();
    try {
      File file = new File(file_name);
      boolean result = FileUtil.createMissingParentDirectories(file);
      if (!result) {
        addError("Failed to create parent directories for [" + file.getAbsolutePath() + "]");
      }
      GZIPOutputStream gzipos = new GZIPOutputStream(new FileOutputStream(file), true);
      setOutputStream(gzipos);
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void stop() {
    super.stop();
  }

  @Override
  public boolean isPrudent() {
    return false;
  }

  @Override
  public void setPrudent(boolean prudent) {
    // ignore, we are never resilient
  }
}
