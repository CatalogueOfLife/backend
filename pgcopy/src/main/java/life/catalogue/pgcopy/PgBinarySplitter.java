package life.catalogue.pgcopy;

import org.apache.commons.io.IOUtils;

import java.io.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class PgBinarySplitter {
  private PgBinaryStream in;
  private long size;
  private int parts = 0;
  private long total;
  private byte[] buffer = new byte[256*1024]; // to hold data for a single field - can be extended dynamically as needed
  private Function<Integer, File> fileSupplier;
  private BiConsumer<Integer, File> listener;

  public static PgBinarySplitter build(InputStream in, int size, File baseFile) throws IOException {
    return new PgBinarySplitter(in, size, (p) -> new File(baseFile.getParentFile(), baseFile.getName()+"-"+p));
  }
  public PgBinarySplitter(InputStream in, long size, Function<Integer, File> fileSupplier) throws IOException {
    this(in, size, fileSupplier, null);
  }

  public PgBinarySplitter(InputStream in, long size, Function<Integer, File> fileSupplier, BiConsumer<Integer, File> listener) throws IOException {
    this.in = new PgBinaryStream(in);
    this.size = size;
    this.fileSupplier = fileSupplier;
    this.listener = listener;
  }

  /**
   * @return number of files being split into
   * @throws IOException
   */
  public int split() throws IOException {
    boolean more = dump();
    while (more) {
      more = dump();
    }
    return parts;
  }

  private boolean dump() throws IOException {
    parts++;
    File f = fileSupplier.apply(parts);
    int recs = 0;
    try (var out = new PgBinaryWriter(new FileOutputStream(f))) {
      while(recs < size && copyRow(out)) {
        recs++;
      }
    } catch (Exception e){
      throw e;
    }
    total = total + recs;
    if (listener != null) {
      listener.accept(parts, f);
    }
    return recs >= size;
  }

  private boolean copyRow(PgBinaryWriter out) throws IOException {
    var fields = in.readShort();
    if (fields < 0) {
      return false;
    }
    out.startRow(fields);
    while (fields>0) {
      fields--;
      int length = in.readInt();
      out.getBuffer().writeInt(length);
      if (length > 0) { // can be null = -1
        if (buffer.length < length) {
          buffer = new byte[length];
        }
        in.read(buffer, 0, length);
        out.getBuffer().write(buffer, 0, length);
      }
    }
    return true;
  }

}
