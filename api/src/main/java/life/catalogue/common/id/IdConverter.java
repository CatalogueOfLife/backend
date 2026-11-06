package life.catalogue.common.id;

import java.util.Arrays;

import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IdConverter {
  private static final Logger LOG = LoggerFactory.getLogger(IdConverter.class);
  public static final IdConverter HEX = new IdConverter("0123456789ABCDEF");
  public static final IdConverter BASE64 = new IdConverter("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/");
  // only URI safe characters: https://www.ietf.org/rfc/rfc3986.txt
  public static final IdConverter URISAFE64 = new IdConverter("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz_-");
  public static final IdConverter LATIN36 = new IdConverter("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ");
  // remove 0O and 1I as they can be ambiguous in some fonts
  public static final IdConverter LATIN32 = new IdConverter("23456789ABCDEFGHJKLMNPQRSTUVWXYZ"); // same as BASE32
  // now also remove all vowels to avoid meaningful and especially offending words in any language
  // See https://github.com/CatalogueOfLife/backend/issues/491
  public static final IdConverter LATIN29 = new IdConverter("23456789BCDFGHJKLMNPQRSTVWXYZ");

  private final char[] chars;
  private final byte[] values; // lookup matrix of char byte -> real byte value, -1 for chars not in the alphabet
  private final int radix;
  private final double logRadix;
  private final char prefix; // optional prefix char to scope the ids
  private final boolean usePrefix;

  public IdConverter(String chars) {
    this(chars, null);
  }

  public IdConverter(IdConverter converter, char prefix) {
    this(String.valueOf(converter.chars), prefix);
  }

  public IdConverter(String chars, Character prefix) {
    this.usePrefix = prefix != null;
    this.prefix = prefix == null ? '_' : prefix; // will be ignored if null

    this.chars = chars.toCharArray();
    int max = 0;
    for (char c : this.chars) {
      max = max>c ? max : c;
    }
    values = new byte[max+1];
    Arrays.fill(values, (byte) -1);
    byte val = 0;
    for (char c : this.chars) {
      values[c] = val++;
    }
    radix = chars.length();
    logRadix = Math.log(radix);
    LOG.debug("Created new IdConverter with base {} and chars {}", radix, chars);
  }

  public char[] getChars() {
    return ArrayUtils.clone(chars);
  }

  /**
   * @param id unsigned integer, zero to Integer.MAX_VALUE
   */
  public String encode(int id) {
    // the number of digits stays the floating point estimate it always was, which pads some ids with a leading zero digit,
    // e.g. LATIN36 encodes 46655 as 0ZZZ
    final int strLen = id == 0 ? 1 : (int) Math.ceil(Math.log(id==Integer.MAX_VALUE ? Integer.MAX_VALUE : id+1) / logRadix);
    if (strLen < 0) {
      // negative ids are not supported, -1 used to fail allocating the digits
      throw new NegativeArraySizeException(String.valueOf(strLen));
    }
    final int off = usePrefix ? 1 : 0;
    final char[] buf = new char[off + strLen];
    if (usePrefix) {
      buf[0] = prefix;
    }
    Arrays.fill(buf, off, buf.length, chars[0]);
    int idx = buf.length;
    while (id > 0) {
      if (--idx < off) {
        throw new ArrayIndexOutOfBoundsException(idx - off);
      }
      buf[idx] = chars[id % radix];
      id = id / radix;
    }
    return new String(buf);
  }
  
  public int decode(String id) {
    try {
      int num = 0;
      int power = (int) Math.pow(radix, id.length() - (usePrefix ? 2 : 1));
      for (int i = usePrefix ? 1 : 0; i < id.length(); i++) {
        final char c = id.charAt(i);
        // check if character is allowed
        if (c >= values.length || values[c] < 0) {
          throw new IllegalArgumentException("Unsupported character "+c);
        }
        num += values[c] * power;
        power = power / radix;
      }
      return num;
    } catch (ArrayIndexOutOfBoundsException e) {
      throw new IllegalArgumentException("Cannot decode invalid ID " + id);
    }
  }
  
  public static double logb( int a, int b ) {
    return Math.log(a) / Math.log(b);
  }
}
