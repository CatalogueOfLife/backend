package life.catalogue.common.io;

import java.io.IOException;
import java.nio.file.Files;

import org.apache.commons.io.FileUtils;

import com.google.common.base.Preconditions;

/**
 * A temporary file or folder that get removed when it is closed.
 */
public class TmpIO implements AutoCloseable {
  
  public final java.io.File file;
  
  public TmpIO(java.io.File file) {
    this.file = Preconditions.checkNotNull(file);
  }
  
  @Override
  public final void close() throws Exception {
    FileUtils.deleteQuietly(file);
  }


  /**
   * A temporary file that get removed when it is closed.
   */
  public static class File extends TmpIO {

    public File() throws IOException {
      this("col-", "");
    }

    public File(String prefix, String suffix) throws IOException {
      this(java.io.File.createTempFile(prefix, suffix));
    }

    public File(java.io.File file) {
      super(file);
      if (!file.isFile()) {
        throw new IllegalArgumentException("Not a file: " + file);
      }
    }
  }

  /**
   * A temporary folder that get removed when it is closed.
   * All contents will be removed too.
   */
  public static class Dir extends TmpIO {

    public Dir() throws IOException {
      this("col-");
    }

    public Dir(String prefix) throws IOException {
      this(Files.createTempDirectory(prefix).toFile());
    }

    public Dir(java.io.File file) {
      super(file);
      if (!file.isDirectory()) {
        throw new IllegalArgumentException("Not a directory: " + file);
      }
    }
  }
}
