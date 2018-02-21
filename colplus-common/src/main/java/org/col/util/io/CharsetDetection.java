package org.col.util.io;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;
import java.util.function.Predicate;

/**
 * <p>
 * Utility class to guess the encoding of a given byte buffer.
 * The guess is unfortunately not 100% sure, especially for 8-bit charsets.
 * It's not possible to know which 8-bit charset is used. Except through statistical analysis.
 * We only try to detect from 3 widely used 8bit encodings: latin1, windows 1252 and macroman.
 * </p>
 *
 * <p>
 * On the other hand, unicode files encoded in UTF-16 (low or big endian) or UTF-8 files with a Byte Order Marker are
 * easy to find. For UTF-8 files with no BOM,
 * if the buffer is wide enough, it's easy to guess.
 * </p>
 *
 * <p>
 * A byte buffer of 4-16KB is sufficient to be able to guess the encoding.
 * </p>
 */
public class CharsetDetection {

  private static final Logger LOG = LoggerFactory.getLogger(CharsetDetection.class);

  private static final int CHARSET_DETECTION_BUFFER_LENGTH = 16*1024; // 16kB
  private static final int UNDEFINED_PENALTY = 100;
  private static final char[] COMMON_NON_ASCII_CHARS;

  static {
    String commonChars = "äåáàæœčéèêëïñøöüßšž";
    CharBuffer cbuf = CharBuffer.allocate(commonChars.length() * 2);
    for (char c : commonChars.toCharArray()) {
      cbuf.append(c);
      cbuf.append(Character.toUpperCase(c));
    }
    COMMON_NON_ASCII_CHARS = cbuf.array();
  }

  private static final Charset LATIN1 = Charsets.ISO_8859_1;
  private static final Charset WINDOWS1252;
  private static final Charset MACROMAN;

  static {
    Charset cs = null;
    try {
      cs = Charset.forName("Cp1252");
    } catch (Exception e) {
      LOG.warn("Windows 1252 encoding not supported on this Machine");
    }
    WINDOWS1252 = cs;

    cs = null;
    try {
      cs = Charset.forName("MacRoman");
    } catch (Exception e) {
      LOG.warn("MacRoman encoding not supported on this Machine");
    }
    MACROMAN = cs;
  }

  private final ByteBuffer buffer;
  public static final int FIRST_BIT_MASK = 1 << 8;

  /**
   * @param buffer the byte buffer of which we want to know the encoding.
   */
  private CharsetDetection(ByteBuffer buffer) {
    this.buffer = buffer;
  }

  /**
   * Detect a file encoding using up to 16kB of a file
   */
  public static Charset detectEncoding(InputStream stream) throws IOException {
    byte[] bytes = new byte[CHARSET_DETECTION_BUFFER_LENGTH];
    int size = IOUtils.read(stream, bytes, 0, CHARSET_DETECTION_BUFFER_LENGTH);
    return detectEncoding(ByteBuffer.wrap(bytes, 0, size));
  }

  /**
   * Detect a file encoding using up to 16kB of a file
   */
  public static Charset detectEncoding(Path path) throws IOException {
    return detectEncoding(path, CHARSET_DETECTION_BUFFER_LENGTH);
  }

  /**
   * @param bufferLength number of bytes to read in for the detection
   */
  public static Charset detectEncoding(Path path, int bufferLength) throws IOException {
    return detectEncoding(readBuffer(path, bufferLength));
  }

  private static ByteBuffer readBuffer(Path file, int length) throws IOException {
    Preconditions.checkArgument(length>1, "Number of bytes to read must be positive");
    try (SeekableByteChannel sbc = Files.newByteChannel(file, EnumSet.of(StandardOpenOption.READ))) {
      ByteBuffer buff = ByteBuffer.allocate(length);
      // Position is set to 0
      buff.clear();
      sbc.read(buff);
      return buff;
    }
  }

  private static Charset detectEncoding(ByteBuffer buffer) throws IOException {
    Charset charset = new CharsetDetection(buffer).detectEncoding();
    LOG.debug("Detected character encoding " + charset.displayName());
    return charset;
  }

  /**
   * Has a Byte Order Marker for UTF-16 Big Endian
   * (utf-16 and ucs-2).
   *
   * @return true if the buffer has a BOM for UTF-16 Big Endian.
   */
  private boolean hasUTF16BEBom() {
    buffer.rewind();
    return buffer.remaining() > 1
        && buffer.get() == -2
        && buffer.get() == -1;
  }

  /**
   * Has a Byte Order Marker for UTF-16 Low Endian
   * (ucs-2le, ucs-4le, and ucs-16le).
   *
   * @return true if the buffer has a BOM for UTF-16 Low Endian.
   */
  private boolean hasUTF16LEBom() {
    buffer.rewind();
    return buffer.remaining() > 1
        && buffer.get() == -1
        && buffer.get() == -2;
  }

  /**
   * Looks for a Byte Order Marker for UTF-8 in the buffer
   * (Used by Microsoft's Notepad and other editors).
   *
   * @return true if the buffer has a BOM for UTF8.
   */
  private boolean hasUTF8Bom() {
    buffer.rewind();
    return buffer.remaining() > 2
        && buffer.get() == -17
        && buffer.get() == -69
        && buffer.get() == -65;
  }

  private static boolean isCommonChar(char c) {
    for (char cc : COMMON_NON_ASCII_CHARS) {
      if (c == cc) {
        return true;
      }
    }
    return false;
  }

  /**
   * If a byte has the form 10xxxxx, then it's a continuation byte of a multiple byte character;
   *
   * @return true if all the bytes in the buffer are continuation bytes.
   */
  private static boolean isContinuation(ByteBuffer buffer) {
    while(buffer.hasRemaining()) {
      byte b = buffer.get();
      if (!(-128 <= b && b <= -65)) return false;
    }
    return true;
  }

  /**
   * Detects the byte length used to encode a utf8 character by looking at the first byte.
   *
   * 2 bytes: 110xxxxx 10xxxxxx
   * 3 bytes: 1110xxxx 10xxxxxx 10xxxxxx
   * 4 bytes: 11110xxx 10xxxxxx 10xxxxxx 10xxxxxx
   *
   * The amount of ones in the first byte tells you how many of the following bytes still belong to the same character.
   * All bytes that belong to the sequence start with 10 in binary.
   * To encode the character you convert its codepoint to binary and fill in the x's.
   *
   * @return number of bytes expected to be continuation bytes following this byte
   */
  private static int bytesSequenceLength(byte b) {
    int cnt = 0;
    while ((b & FIRST_BIT_MASK) != 0) {
      cnt++;
      b <<= 1;
    }
    return cnt-1;
  }

  private Charset detectCharacterEncoding8bit() {

    // the number of "bad" chars for the best guess. A better guess will have
    long leastSuspicousChars = testLatin1();
    long suspicousChars;

    // the best guess so far
    Charset bestEncoding = LATIN1;

    if (WINDOWS1252 != null) {
      suspicousChars = testWindows1252();
      if (suspicousChars < leastSuspicousChars) {
        leastSuspicousChars = suspicousChars;
        bestEncoding = WINDOWS1252;
      }
    }

    if (MACROMAN != null) {
      suspicousChars = testMacRoman();
      if (suspicousChars < leastSuspicousChars) {
        leastSuspicousChars = suspicousChars;
        bestEncoding = MACROMAN;
      }
    }

    LOG.debug("8bit Encoding guessed: {} with {} rare characters", bestEncoding, leastSuspicousChars);
    return bestEncoding;
  }

  /**
   * <p>
   * Guess the encoding of the provided buffer.
   * </p>
   * If Byte Order Markers are encountered at the beginning of the buffer, we immidiately
   * return the charset implied by this BOM. Otherwise, the file would not be a human
   * readable text file.</p>
   * <p/>
   * <p>
   * If there is no BOM, this method tries to discern whether the file is UTF-8 or not. If it is not UTF-8,
   * we try to select the best match from latin1, windows 1252 or macroman.
   * </p>
   * <p/>
   * <p>
   * It is possible to discern UTF-8 thanks to the pattern of characters with a multi-byte sequence.
   * </p>
   * <p/>
   * <pre>
   * UCS-4 range (hex.)        UTF-8 octet sequence (binary)
   * 0000 0000-0000 007F       0xxxxxxx
   * 0000 0080-0000 07FF       110xxxxx 10xxxxxx
   * 0000 0800-0000 FFFF       1110xxxx 10xxxxxx 10xxxxxx
   * 0001 0000-001F FFFF       11110xxx 10xxxxxx 10xxxxxx 10xxxxxx
   * 0020 0000-03FF FFFF       111110xx 10xxxxxx 10xxxxxx 10xxxxxx 10xxxxxx
   * 0400 0000-7FFF FFFF       1111110x 10xxxxxx 10xxxxxx 10xxxxxx 10xxxxxx 10xxxxxx
   * </pre>
   * <p>
   * With UTF-8, 0xFE and 0xFF never appear.
   * </p>
   *
   * @return the Charset recognized or the best matching 8bit encoding of latin1, windows 1252 or macroman.
   */
  Charset detectEncoding() {
    // require at least 2 bytes, use latin1 otherwise as there is no content anyways
    if (buffer.remaining() < 2) {
      LOG.debug("No content, use latin1");
      return Charsets.ISO_8859_1;
    }

    // if the file has a Byte Order Marker, we can assume the file is in UTF-xx
    // otherwise, the file would not be human readable
    if (hasUTF8Bom()) {
      return Charsets.UTF_8;
    }
    if (hasUTF16LEBom()) {
      return Charsets.UTF_16LE;
    }
    if (hasUTF16BEBom()) {
      return Charsets.UTF_16BE;
    }

    // if it's not UTF-8 or a BOM present check for UTF16 zeros
    Charset cs = detectUtf16();
    if (cs != null) {
      return cs;
    }

    // if the file is in UTF-8, high order bytes must have a certain value, in order to be valid
    // if it's not the case, we can assume the encoding is some 8 bit one
    boolean validU8Char = true;

    final ByteBuffer charBytes = ByteBuffer.allocate(6);
    while (buffer.hasRemaining()) {
      byte b = buffer.get();
      if (b < 0) {
        // a high order bit was encountered, thus the encoding is not US-ASCII
        // read expected continuation bytes
        int expBytes= bytesSequenceLength(b);
        if (expBytes > 0 && buffer.remaining() >= expBytes) {
          charBytes.clear();
          while (expBytes-- > 0) {
            charBytes.put(buffer.get());
          }
          charBytes.flip();
          if (!isContinuation(charBytes)) {
            validU8Char = false;
            break;
          }
        }
      }
    }

    // if no invalid UTF-8 were encountered, we can assume the encoding is UTF-8,
    // otherwise the file would not be human readable
    if (validU8Char) {
      return Charsets.UTF_8;
    }

    // finally it must be some 8bit encoding we try to detect statistically
    return detectCharacterEncoding8bit();
  }

  private Charset detectUtf16() {

    // first try to see if we got a little or big endian, i.e. lots of zeros as the first byte or second byte if we deal
    // with latin characters at least
    int zerosLE = 0;
    int zerosBE = 0;
    boolean even = true;

    while (buffer.hasRemaining()) {
      byte b = buffer.get();
      even = !even;
      if (b == 0x00) {
        // zero occurr a lot in utf16 with latin characters
        if (even) {
          zerosLE++;
        } else {
          zerosBE++;
        }
      }
    }

    // a UTF16 encoding with many latin characters would have either lots of even or uneven bytes as zero - but not both
    buffer.rewind();
    int min = buffer.remaining() / 10;
    if ((zerosBE > min || zerosLE > min) && Math.abs(zerosBE - zerosLE) > min) {
      Charset charset = zerosBE > zerosLE ? Charsets.UTF_16BE : Charsets.UTF_16LE;

      // now try to decode the whole lot just to make sure
      try {
        CharsetDecoder decoder = charset.newDecoder();
        decoder.decode(buffer);
        // that worked without a problem - think we got it!
        return charset;

      } catch (CharacterCodingException e) {
        // finally try with the plain UTF16 encoding
        charset = Charsets.UTF_16;
        try {
          CharsetDecoder decoder = charset.newDecoder();
          buffer.rewind();
          decoder.decode(buffer);
          // that worked without a problem - think we got it!
          return charset;

        } catch (CharacterCodingException e2) {
        }
      }
    }

    return null;
  }

  private long testDecoder(CharsetDecoder decoder, Predicate<Byte> isUndefined) {
    long suspicous = 0;

    // first try to decode the whole lot
    try {
      buffer.rewind();
      CharBuffer cbuf = decoder.decode(buffer);
      while (cbuf.hasRemaining()) {
        char c = cbuf.get();
        if (isCommonChar(c)) {
          suspicous--;
        }
      }
      // if that worked without a problem try to count suspicious characters which are rarely used in our texts
      buffer.rewind();
      while (buffer.hasRemaining()) {
        byte b = buffer.get();
        if (isUndefined.test(b)) {
          suspicous += UNDEFINED_PENALTY;
        }
      }
    } catch (CharacterCodingException e) {
      suspicous = Long.MAX_VALUE;
    }

    return suspicous;
  }

  private long testLatin1() {
    return testDecoder(LATIN1.newDecoder(), b ->
        // range 7f-9f undefined, see http://de.wikipedia.org/wiki/ISO_8859-1
        (b >= (byte) 0x80 && b <= (byte) 0x9f)
    );
  }

  /**
   * MacRoman defines the entire byte range, there are no undefined bytes.
   */
  private long testMacRoman() {
    return testDecoder(MACROMAN.newDecoder(), b -> false);
  }

  /**
   * Spot suspicous characters which are rarely used in our texts
   * see http://de.wikipedia.org/wiki/ISO_8859-1
   */
  private long testWindows1252() {
    return testDecoder(WINDOWS1252.newDecoder(), b ->
        (b == (byte) 0x81 || b == (byte) 0x8d || b == (byte) 0x8f || b == (byte) 0x90 || b == (byte) 0x9d)
    );
  }

}
