package org.col.util;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.io.ByteStreams;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

/**
 * <p>
 * Utility class to guess the encoding of a given file or byte array. The guess is unfortunately not 100% sure.
 * Especially for 8-bit charsets. It's not possible
 * to know which 8-bit charset is used. Except through statistical analysis.
 * </p>
 * <p/>
 * <p>
 * On the other hand, unicode files encoded in UTF-16 (low or big endian) or UTF-8 files with a Byte Order Marker are
 * easy to find. For UTF-8 files with no BOM,
 * if the buffer is wide enough, it's easy to guess.
 * </p>
 * <p/>
 * <p>
 * A byte buffer of 4KB or 8KB is sufficient to be able to guess the encoding.
 * </p>
 * This class is a heavily modified version of the original written by Guillaume LAFORGE:
 * com.glaforge.i18n.io.CharsetToolkit
 * taken from
 * http://glaforge.free.fr/wiki/index.php?wiki=GuessEncoding
 *
 * @author Guillaume LAFORGE
 * @author Markus Döring
 */
public class CharsetDetection {

  private static final Logger LOG = LoggerFactory.getLogger(CharsetDetection.class);
  // encodings to test and very unlikely chars in that encoding
  private static final byte LF = 0x0a;
  private static final byte CR = 0x0d;
  private static final byte TAB = 0x09;

  private static final int CHARSET_DETECTION_BUFFER_LENGTH = 16384; // =16kB
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
      LOG.warn("Windows 1252 encoding not supported on this Virtual Machine");
    }
    WINDOWS1252 = cs;

    cs = null;
    try {
      cs = Charset.forName("MacRoman");
    } catch (Exception e) {
      LOG.warn("MacRoman encoding not supported on this Virtual Machine");
    }
    MACROMAN = cs;
  }

  private final byte[] buffer;

  /**
   * Constructor of the <code>com.glaforge.i18n.io.CharsetToolkit</code> utility class.
   *
   * @param buffer the byte buffer of which we want to know the encoding.
   */
  private CharsetDetection(byte[] buffer) {
    this.buffer = buffer;
  }

  /**
   * Detect a file encoding using up to 16kB of a file
   */
  public static Charset detectEncoding(InputStream stream) throws IOException {
    return detectEncoding(ByteStreams.toByteArray(ByteStreams.limit(stream, CHARSET_DETECTION_BUFFER_LENGTH)));
  }

  /**
   * Detect a file encoding using up to 16kB of a file
   */
  public static Charset detectEncoding(File file) throws IOException {
    return detectEncoding(FileUtils.readFileToByteArray(file));
  }

  /**
   * @param bufferLength number of bytes to read in for the detection. If zero or negative read entire file
   */
  public static Charset detectEncoding(File file, int bufferLength) throws IOException {
    Preconditions.checkArgument(bufferLength > 1);
    return detectEncoding(readByteBuffer(file, bufferLength).array());
  }

  private static Charset detectEncoding(byte[] data) throws IOException {
    CharsetDetection detector = new CharsetDetection(data);
    Charset charset = detector.detectEncoding();

    LOG.debug("Detected character encoding " + charset.displayName());
    return charset;
  }

  /**
   * Retrieve the default charset of the system.
   *
   * @return the default <code>Charset</code>.
   */
  public static Charset getDefaultSystemCharset() {
    return Charset.forName(System.getProperty("file.encoding"));
  }

  /**
   * Reads the first bytes of a file into a byte buffer.
   *
   * @param bufferSize the number of bytes to read from the file
   */
  private static ByteBuffer readByteBuffer(File file, int bufferSize) throws IOException {
    ByteBuffer bbuf = ByteBuffer.allocate(bufferSize);
    BufferedInputStream f = new BufferedInputStream(new FileInputStream(file), bufferSize);

    int b;
    while ((b = f.read()) != -1) {
      if (!bbuf.hasRemaining()) {
        break;
      }
      bbuf.put((byte) b);
    }
    f.close();

    return bbuf;
  }

  /**
   * Has a Byte Order Marker for UTF-16 Big Endian
   * (utf-16 and ucs-2).
   *
   * @param bom a buffer.
   *
   * @return true if the buffer has a BOM for UTF-16 Big Endian.
   */
  private static boolean hasUTF16BEBom(byte[] bom) {
    return bom[0] == -2 && bom[1] == -1;
  }

  /**
   * Has a Byte Order Marker for UTF-16 Low Endian
   * (ucs-2le, ucs-4le, and ucs-16le).
   *
   * @param bom a buffer.
   *
   * @return true if the buffer has a BOM for UTF-16 Low Endian.
   */
  private static boolean hasUTF16LEBom(byte[] bom) {
    return bom[0] == -1 && bom[1] == -2;
  }

  /**
   * Has a Byte Order Marker for UTF-8 (Used by Microsoft's Notepad and other editors).
   *
   * @param bom a buffer.
   *
   * @return true if the buffer has a BOM for UTF8.
   */
  protected static boolean hasUTF8Bom(byte[] bom) {
    return bom[0] == -17 && bom[1] == -69 && bom[2] == -65;
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
   * If the byte has the form 10xxxxx, then it's a continuation byte of a multiple byte character;
   *
   * @param b a byte.
   *
   * @return true if it's a continuation char.
   */
  private static boolean isContinuationChar(byte b) {
    return -128 <= b && b <= -65;
  }

  /**
   * If the byte has the form 11110xx, then it's the first byte of a five-bytes sequence character.
   *
   * @param b a byte.
   *
   * @return true if it's the first byte of a five-bytes sequence.
   */
  private static boolean isFiveBytesSequence(byte b) {
    return -8 <= b && b <= -5;
  }

  /**
   * If the byte has the form 11110xx, then it's the first byte of a four-bytes sequence character.
   *
   * @param b a byte.
   *
   * @return true if it's the first byte of a four-bytes sequence.
   */
  private static boolean isFourBytesSequence(byte b) {
    return -16 <= b && b <= -9;
  }

  /**
   * If the byte has the form 1110xxx, then it's the first byte of a six-bytes sequence character.
   *
   * @param b a byte.
   *
   * @return true if it's the first byte of a six-bytes sequence.
   */
  private static boolean isSixBytesSequence(byte b) {
    return -4 <= b && b <= -3;
  }

  /**
   * If the byte has the form 1110xxx, then it's the first byte of a three-bytes sequence character.
   *
   * @param b a byte.
   *
   * @return true if it's the first byte of a three-bytes sequence.
   */
  private static boolean isThreeBytesSequence(byte b) {
    return -32 <= b && b <= -17;
  }

  /**
   * If the byte has the form 110xxxx, then it's the first byte of a two-bytes sequence character.
   *
   * @param b a byte.
   *
   * @return true if it's the first byte of a two-bytes sequence.
   */
  private static boolean isTwoBytesSequence(byte b) {
    return -64 <= b && b <= -33;
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
   * we try to select the best match fro latin1, windows 1252 or macroman.
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
    // if the file has a Byte Order Marker, we can assume the file is in UTF-xx
    // otherwise, the file would not be human readable
    if (hasUTF8Bom(buffer)) {
      return Charsets.UTF_8;
    }
    if (hasUTF16LEBom(buffer)) {
      return Charsets.UTF_16LE;
    }
    if (hasUTF16BEBom(buffer)) {
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

    // TODO the buffer is not read up to the end, but up to length - 6

    int length = buffer.length;
    int i = 0;
    while (i < length - 6) {
      byte b0 = buffer[i];
      byte b1 = buffer[i + 1];
      byte b2 = buffer[i + 2];
      byte b3 = buffer[i + 3];
      byte b4 = buffer[i + 4];
      byte b5 = buffer[i + 5];
      if (b0 < 0) {
        // a high order bit was encountered, thus the encoding is not US-ASCII
        // a two-bytes sequence was encoutered
        if (isTwoBytesSequence(b0)) {
          // there must be one continuation byte of the form 10xxxxxx,
          // otherwise the following characteris is not a valid UTF-8 construct
          if (isContinuationChar(b1)) {
            i++;
          } else {
            validU8Char = false;
          }
        }
        // a three-bytes sequence was encoutered
        else if (isThreeBytesSequence(b0)) {
          // there must be two continuation bytes of the form 10xxxxxx,
          // otherwise the following characteris is not a valid UTF-8 construct
          if (isContinuationChar(b1) && isContinuationChar(b2)) {
            i += 2;
          } else {
            validU8Char = false;
          }
        }
        // a four-bytes sequence was encoutered
        else if (isFourBytesSequence(b0)) {
          // there must be three continuation bytes of the form 10xxxxxx,
          // otherwise the following characteris is not a valid UTF-8 construct
          if (isContinuationChar(b1) && isContinuationChar(b2) && isContinuationChar(b3)) {
            i += 3;
          } else {
            validU8Char = false;
          }
        }
        // a five-bytes sequence was encoutered
        else if (isFiveBytesSequence(b0)) {
          // there must be four continuation bytes of the form 10xxxxxx,
          // otherwise the following characteris is not a valid UTF-8 construct
          if (isContinuationChar(b1) && isContinuationChar(b2) && isContinuationChar(b3) && isContinuationChar(b4)) {
            i += 4;
          } else {
            validU8Char = false;
          }
        }
        // a six-bytes sequence was encoutered
        else if (isSixBytesSequence(b0)) {
          // there must be five continuation bytes of the form 10xxxxxx,
          // otherwise the following characteris is not a valid UTF-8 construct
          if (isContinuationChar(b1) && isContinuationChar(b2) && isContinuationChar(b3) && isContinuationChar(b4)
              && isContinuationChar(b5)) {
            i += 5;
          } else {
            validU8Char = false;
          }
        } else {
          validU8Char = false;
        }
      }
      if (!validU8Char) {
        break;
      }
      i++;
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

    int length = buffer.length;
    int i = 0;
    while (i < length) {
      byte b = buffer[i];
      i++;
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
    int min = buffer.length / 10;
    if ((zerosBE > min || zerosLE > min) && Math.abs(zerosBE - zerosLE) > min) {
      Charset charset = zerosBE > zerosLE ? Charsets.UTF_16BE : Charsets.UTF_16LE;

      // now try to decode the whole lot just to make sure
      try {
        CharsetDecoder decoder = charset.newDecoder();
        decoder.decode(ByteBuffer.wrap(buffer));
        // that worked without a problem - think we got it!
        return charset;
      } catch (CharacterCodingException e) {
        // finally try with the plain UTF16 encoding
        charset = Charsets.UTF_16;
        try {
          CharsetDecoder decoder = charset.newDecoder();
          decoder.decode(ByteBuffer.wrap(buffer));
          // that worked without a problem - think we got it!
          return charset;
        } catch (CharacterCodingException e2) {
        }
      }
    }

    return null;
  }

  private long testLatin1() {
    Charset charset = Charsets.ISO_8859_1;
    CharsetDecoder decoder = charset.newDecoder();

    long suspicous = 0;
    // count the following

    // first try to decode the whole lot and count common non ascii chars
    try {
      CharBuffer cbuf = decoder.decode(ByteBuffer.wrap(buffer));
      while (cbuf.hasRemaining()) {
        char c = cbuf.get();
        if (isCommonChar(c)) {
          suspicous--;
        }
      }

      // if that worked without a problem try to count suspicous characters which are rarely used in our texts
      int length = buffer.length;
      int i = 0;
      while (i < length) {
        byte b = buffer[i];
        i++;
        // range 7f-9f undefined, see http://de.wikipedia.org/wiki/ISO_8859-1
        if (b >= (byte) 0x80 && b <= (byte) 0x9f) {
          suspicous += UNDEFINED_PENALTY;
        }
      }
    } catch (CharacterCodingException e) {
      suspicous = Long.MAX_VALUE;
    }

    return suspicous;
  }

  private long testMacRoman() {
    CharsetDecoder decoder = MACROMAN.newDecoder();

    long suspicous = 0;

    // first try to decode the whole lot
    try {
      CharBuffer cbuf = decoder.decode(ByteBuffer.wrap(buffer));
      while (cbuf.hasRemaining()) {
        char c = cbuf.get();
        if (isCommonChar(c)) {
          suspicous--;
        }
      }
      // if that worked without a problem try to count suspicious characters which are rarely used in our texts
      int length = buffer.length;
      int i = 0;
      while (i < length) {
        byte b = buffer[i];
        i++;
        // all ranges defined I am afraid
      }
    } catch (CharacterCodingException e) {
      suspicous = Long.MAX_VALUE;
    }

    return suspicous;
  }

  private long testWindows1252() {
    CharsetDecoder decoder = WINDOWS1252.newDecoder();
    long suspicous = 0;

    // first try to decode the whole lot
    try {
      CharBuffer cbuf = decoder.decode(ByteBuffer.wrap(buffer));
      while (cbuf.hasRemaining()) {
        char c = cbuf.get();
        if (isCommonChar(c)) {
          suspicous--;
        }
      }
      // if that worked without a problem try to count suspicous characters which are rarely used in our texts
      // see http://de.wikipedia.org/wiki/ISO_8859-1
      int length = buffer.length;
      int i = 0;
      while (i < length) {
        byte b = buffer[i];
        i++;
        // 5 undefined chars
        if (b == (byte) 0x81 || b == (byte) 0x8d || b == (byte) 0x8f || b == (byte) 0x90 || b == (byte) 0x9d) {
          suspicous += UNDEFINED_PENALTY;
        }
      }
    } catch (CharacterCodingException e) {
      suspicous = Long.MAX_VALUE;
    }

    return suspicous;
  }

}
