package life.catalogue.common.io;

import java.io.Closeable;
import java.io.IOException;

public interface RowWriter extends Closeable {

  void write(String[] row) throws IOException;

}
