package life.catalogue.matching.mmap;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;

/**
 * Little endian primitives shared by the memory mapped matcher store files.
 *
 * <p>All files are written and read little endian regardless of the host, so an index built on one
 * machine can be shipped to another - which is exactly what {@code MatchingServerBuildCmd} does.
 *
 * <p>Mapping goes through the Java 22+ foreign memory API: the returned {@link MemorySegment} is bounds
 * checked (an offset bug raises an {@link IndexOutOfBoundsException} instead of corrupting memory) and is
 * unmapped deterministically when its {@link Arena} is closed. None of this needs {@code --add-opens} or
 * {@code --enable-native-access}: {@link FileChannel#map(FileChannel.MapMode, long, long, Arena)} is not a
 * restricted method.
 */
public final class MmapIO {
  public static final ValueLayout.OfInt INT = ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN);
  public static final ValueLayout.OfLong LONG = ValueLayout.JAVA_LONG_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN);
  public static final ValueLayout.OfByte BYTE = ValueLayout.JAVA_BYTE;

  private MmapIO() {}

  /**
   * Maps an entire file into memory. The channel is closed right away - the mapping stays valid until the
   * arena is closed.
   */
  public static MemorySegment map(File f, Arena arena, boolean write) throws IOException {
    var opts = write
      ? new StandardOpenOption[]{StandardOpenOption.READ, StandardOpenOption.WRITE}
      : new StandardOpenOption[]{StandardOpenOption.READ};
    try (FileChannel ch = FileChannel.open(f.toPath(), opts)) {
      var mode = write ? FileChannel.MapMode.READ_WRITE : FileChannel.MapMode.READ_ONLY;
      return ch.map(mode, 0, ch.size(), arena);
    }
  }

  /** Murmur3 finalizer, used to spread both int keys and string hashes across the table. */
  public static int fmix(int h) {
    h ^= h >>> 16;
    h *= 0x85ebca6b;
    h ^= h >>> 13;
    h *= 0xc2b2ae35;
    h ^= h >>> 16;
    return h;
  }

  public static int hash(int key) {
    return fmix(key);
  }

  /** FNV-1a over the key bytes, finalized with {@link #fmix(int)}. */
  public static int hash(byte[] key, int len) {
    int h = 0x811c9dc5;
    for (int i = 0; i < len; i++) {
      h ^= key[i] & 0xff;
      h *= 0x01000193;
    }
    return fmix(h);
  }

  /**
   * Size of the open addressed hash table holding n keys: the smallest power of two that keeps the load
   * factor at or below 0.5, so linear probing stays short.
   */
  public static int tableSize(int n) {
    long want = Math.max(16L, 2L * n + 1);
    if (want > (1L << 30)) {
      throw new IllegalArgumentException("Too many entries for a single store: " + n);
    }
    int size = 16;
    while (size < want) {
      size <<= 1;
    }
    return size;
  }

  /** Little endian buffered writer for the temporary files a store build streams through. */
  public static class LeOut implements AutoCloseable {
    private final OutputStream out;
    private final byte[] buf = new byte[8];
    private long written = 0;

    public LeOut(File f) throws IOException {
      this.out = new BufferedOutputStream(new FileOutputStream(f), 65536);
    }

    public void writeByte(int v) throws IOException {
      out.write(v);
      written++;
    }

    public void writeInt(int v) throws IOException {
      buf[0] = (byte) v;
      buf[1] = (byte) (v >>> 8);
      buf[2] = (byte) (v >>> 16);
      buf[3] = (byte) (v >>> 24);
      out.write(buf, 0, 4);
      written += 4;
    }

    public void writeLong(long v) throws IOException {
      for (int i = 0; i < 8; i++) {
        buf[i] = (byte) (v >>> (8 * i));
      }
      out.write(buf, 0, 8);
      written += 8;
    }

    public void write(byte[] b) throws IOException {
      out.write(b);
      written += b.length;
    }

    public long written() {
      return written;
    }

    public void flush() throws IOException {
      out.flush();
    }

    @Override
    public void close() throws IOException {
      out.close();
    }
  }

  /** Little endian buffered reader for the temporary files a store build streams through. */
  public static class LeIn implements AutoCloseable {
    private final DataInputStream in;
    private final byte[] buf = new byte[8];

    public LeIn(File f) throws IOException {
      this.in = new DataInputStream(new BufferedInputStream(new FileInputStream(f), 65536));
    }

    public int readByte() throws IOException {
      return in.readUnsignedByte();
    }

    public int readInt() throws IOException {
      in.readFully(buf, 0, 4);
      return (buf[0] & 0xff) | (buf[1] & 0xff) << 8 | (buf[2] & 0xff) << 16 | (buf[3] & 0xff) << 24;
    }

    public long readLong() throws IOException {
      in.readFully(buf, 0, 8);
      long v = 0;
      for (int i = 7; i >= 0; i--) {
        v = (v << 8) | (buf[i] & 0xffL);
      }
      return v;
    }

    public void readFully(byte[] b, int len) throws IOException {
      in.readFully(b, 0, len);
    }

    public void skip(long n) throws IOException {
      long left = n;
      while (left > 0) {
        long s = in.skip(left);
        if (s <= 0) {
          if (in.read() < 0) throw new EOFException();
          s = 1;
        }
        left -= s;
      }
    }

    public InputStream stream() {
      return in;
    }

    @Override
    public void close() throws IOException {
      in.close();
    }
  }
}
