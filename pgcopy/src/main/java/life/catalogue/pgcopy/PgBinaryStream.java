package life.catalogue.pgcopy;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * InputStream for the pg binary format that understands the formats header.
 */
public class PgBinaryStream extends DataInputStream {

  /**
   * @param in the stream to read, buffered unless it is a BufferedInputStream already.
   *           Every field is read with a few small reads, each of which is a system call on a plain file stream.
   */
  public PgBinaryStream(InputStream in) throws IOException {
    super(in instanceof BufferedInputStream ? in : new BufferedInputStream(in, 64 * 1024));
    readHeader();
  }

  public void readHeader() throws IOException  {
    // 11 bytes required header
    for (int i = 0 ; i < PgBinaryWriter.HEADER.length() ; i++) {
      byte c = (byte) PgBinaryWriter.HEADER.charAt(i);
      byte x = readByte();
      if (c != x) throw new IllegalArgumentException("Not a valid PGCOPY stream");
    }
    // 32 bit integer indicating no OID
    int oid = readInt();
    // 32 bit header extension area length
    int ext = readInt();
  }

}
