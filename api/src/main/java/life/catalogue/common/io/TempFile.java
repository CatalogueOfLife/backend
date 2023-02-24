package life.catalogue.common.io;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import org.apache.commons.io.FileUtils;

import com.google.common.base.Preconditions;

/**
 * A temporary file or folder that get removed when it is closed.
 * If its a folder all contents will be removed too.
 */
public class TempFile implements AutoCloseable {
  
  public final File file;

  public static File directoryFile() {
    return new File("/tmp/col/" + UUID.randomUUID());
  }

  public static TempFile directory() throws IOException {
    TempFile tf = new TempFile(directoryFile());
    tf.file.mkdirs();
    return tf;
  }

  private static File createTempFile(String prefix, String suffix) {
    try {
      return File.createTempFile(prefix, suffix);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public TempFile() throws IOException {
    this("col-", "");
  }
  
  public TempFile(String prefix, String suffix) {
    this(createTempFile(prefix, suffix));
  }

  public TempFile(File dir, String filename) {
    this(new File(dir, filename));
  }

  public TempFile(File file) {
    this.file = Preconditions.checkNotNull(file);
  }

  public boolean exists() {
    return file.exists();
  }

  public boolean isDirectory() {
    return file.isDirectory();
  }

  public boolean isFile() {
    return file.isFile();
  }

  @Override
  public void close() throws Exception {
    FileUtils.deleteQuietly(file);
  }
}
