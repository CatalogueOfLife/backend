package life.catalogue.common.io;

import com.google.common.base.Preconditions;
import life.catalogue.common.text.StringUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BOMInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

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
  
  @Override
  public int read() throws IOException {
    return input.read();
  }
}
