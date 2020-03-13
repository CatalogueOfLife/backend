package life.catalogue.common.io;

import java.io.IOException;
import java.io.Writer;

import org.jetbrains.annotations.NotNull;

public class DevNullWriter extends Writer {
  @Override
  public void write(@NotNull char[] cbuf, int off, int len) throws IOException {
  
  }
  
  @Override
  public void flush() throws IOException {
  
  }
  
  @Override
  public void close() throws IOException {
  
  }
}
