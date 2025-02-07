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

  public static TempFile file() throws IOException {
    TempFile tf = new TempFile(directoryFile());
    tf.file.getParentFile().mkdirs();
    return tf;
  }

  public static TempFile directory() throws IOException {
    TempFile tf = new TempFile(directoryFile());
    tf.file.mkdirs();
    return tf;
  }

  public static TempFile directory(File dir) throws IOException {
    TempFile tf = new TempFile(dir);
    tf.file.mkdirs();
    return tf;
  }

  public static TempFile created(File file) throws IOException {
    TempFile tf = new TempFile(file);
    tf.file.getParentFile().mkdirs();
    tf.file.createNewFile();
    return tf;
  }

  private static File createTempFile(String prefix, String suffix) {
    try {
      return File.createTempFile(prefix, suffix);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public TempFile() {
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
  public void close() {
    FileUtils.deleteQuietly(file);
  }
}
