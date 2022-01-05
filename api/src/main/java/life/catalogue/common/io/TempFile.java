package life.catalogue.common.io;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;

import com.google.common.base.Preconditions;

/**
 * A temporary file or folder that get removed when it is closed.
 * If its a folder all contents will be removed too.
 */
public class TempFile implements AutoCloseable {
  
  public final File file;
  
  public TempFile() throws IOException {
    this("col-", "");
  }
  
  public TempFile(String prefix, String suffix) throws IOException {
    this(File.createTempFile(prefix, suffix));
  }
  
  public TempFile(File file) {
    this.file = Preconditions.checkNotNull(file);
  }
  
  @Override
  public void close() throws Exception {
    FileUtils.deleteQuietly(file);
  }
}
