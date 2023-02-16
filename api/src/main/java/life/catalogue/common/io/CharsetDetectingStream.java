package life.catalogue.common.io;

import life.catalogue.common.text.StringUtils;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BOMInputStream;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import static life.catalogue.common.io.CharsetDetection.BUFFER_SIZE;

/**
 *
 */
public class CharsetDetectingStream extends InputStream {
  private static final Logger LOG = LoggerFactory.getLogger(CharsetDetectingStream.class);
  private Charset charset;
  private final InputStream input;
  private final static int debugHexSize = 16;
  
  private CharsetDetectingStream(InputStream in, Charset charset) {
    this.input = in;
    this.charset = charset;
  }
  
  /**
   * Creates a buffered reader skipping potential bom start sequences
   */
  public static BufferedReader createReader(InputStream in, Charset charset) throws IOException {
    Preconditions.checkNotNull(in);
    Preconditions.checkNotNull(charset);
    return new BufferedReader(new InputStreamReader(new BOMInputStream(in), charset));
  }

  /**
   * Creates a buffered reader using the charset detecting stream to identify the charset.
   */
  public static BufferedReader createReader(InputStream in) throws IOException {
    Preconditions.checkNotNull(in);
    var cs = create(in);
    return new BufferedReader(new InputStreamReader(cs, cs.charset));
  }
  
  public static CharsetDetectingStream create(InputStream in) throws IOException {
    Preconditions.checkNotNull(in);
    BOMInputStream bom = new BOMInputStream(in);
    if (bom.hasBOM()) {
      LOG.debug("{} BOM found", bom.getBOMCharsetName());
      return new CharsetDetectingStream(bom, Charset.forName(bom.getBOMCharsetName()));
      
    } else {
      // if we cannot find a BOM try to detect heuristically extracting a larger byte buffer first
      BufferedInputStream bis = new BufferedInputStream(bom, BUFFER_SIZE);
      bis.mark(BUFFER_SIZE);
      byte[] bytes = new byte[BUFFER_SIZE];
      int size = IOUtils.read(bis, bytes, 0, BUFFER_SIZE);
      if (LOG.isDebugEnabled()) {
        byte[] start = new byte[debugHexSize];
        System.arraycopy(bytes, 0, start, 0, debugHexSize);
        LOG.debug("Buffered {} bytes, starting with {}", size, StringUtils.hexString(start));
      }
      bis.reset();
      // detect from buffer and create wrapped stream
      Charset cs = CharsetDetection.detectEncoding(ByteBuffer.wrap(bytes, 0, size));
      return new CharsetDetectingStream(bis, cs);
    }
  }
  
  public Charset getCharset() {
    return charset;
  }


  //
  // DELEGATED INPUT STREAM METHODS
  //

  @Override
  public int read() throws IOException {
    return input.read();
  }

  public static InputStream nullInputStream() {
    return InputStream.nullInputStream();
  }

  @Override
  public int read(@NotNull byte[] b) throws IOException {
    return input.read(b);
  }

  @Override
  public int read(@NotNull byte[] b, int off, int len) throws IOException {
    return input.read(b, off, len);
  }

  @Override
  public byte[] readAllBytes() throws IOException {
    return input.readAllBytes();
  }

  @Override
  public byte[] readNBytes(int len) throws IOException {
    return input.readNBytes(len);
  }

  @Override
  public int readNBytes(byte[] b, int off, int len) throws IOException {
    return input.readNBytes(b, off, len);
  }

  @Override
  public long skip(long n) throws IOException {
    return input.skip(n);
  }

  @Override
  public int available() throws IOException {
    return input.available();
  }

  @Override
  public void close() throws IOException {
    input.close();
  }

  @Override
  public void mark(int readlimit) {
    input.mark(readlimit);
  }

  @Override
  public void reset() throws IOException {
    input.reset();
  }

  @Override
  public boolean markSupported() {
    return input.markSupported();
  }

  @Override
  public long transferTo(OutputStream out) throws IOException {
    return input.transferTo(out);
  }
}
