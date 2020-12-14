package life.catalogue.common.id;

import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IdConverter {
  private static final Logger LOG = LoggerFactory.getLogger(IdConverter.class);
  public static final IdConverter HEX = new IdConverter("0123456789ABCDEF");
  public static final IdConverter BASE64 = new IdConverter("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/");
  public static final IdConverter LATIN36 = new IdConverter("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ");
  // remove 0O and 1I as they can be ambiguous in some fonts
  public static final IdConverter LATIN32 = new IdConverter("23456789ABCDEFGHJKLMNPQRSTUVWXYZ"); // same as BASE32
  // now also remove all vowels to avoid meaningful and especially offending words in any language
  // See https://github.com/CatalogueOfLife/backend/issues/491
  public static final IdConverter LATIN29 = new IdConverter("23456789BCDFGHJKLMNPQRSTVWXYZ");

  private final char[] chars;
  private final byte[] values; // lookup matrix of char byte -> real byte value
  private final int radix;
  
  public IdConverter(String chars) {
    this.chars = chars.toCharArray();
    int max = 0;
    for (char c : this.chars) {
      max = max>c ? max : c;
    }
    values = new byte[max+1];
    byte val = 0;
    for (char c : this.chars) {
      values[c] = val++;
    }
    radix = chars.length();
    LOG.debug("Created new IdConverter with base {} and chars {}", radix, chars);
  }

  public char[] getChars() {
    return ArrayUtils.clone(chars);
  }

  /**
   * @param id unsigned integer, zero to Integer.MAX_VALUE
   */
  public String encode(int id) {
    byte[] bytes;
    if (id==0) {
      bytes = new byte[1];
      bytes[0] = 0;
    } else {
      int strLen = (int) Math.ceil(logb(id==Integer.MAX_VALUE ? Integer.MAX_VALUE : id+1, radix));
      bytes = new byte[strLen];
      int idx = strLen;
      while (id > 0) {
        int quot = id % radix;
        bytes[--idx] = (byte) quot;
        id = id / radix;
      }
    }
    return encode(bytes);
  }
  
  public int decode(String id) {
    try {
      int num = 0;
      char[] chars = id.toCharArray();
      int power = (int) Math.pow(radix, chars.length-1);
      for (char c : chars) {
        num += values[c] * power;
        power = power / radix;
      }
      return num;
    } catch (ArrayIndexOutOfBoundsException e) {
      throw new IllegalArgumentException("Cannot decode invalid ID " + id);
    }
  }
  
  /**
   * Converts each byte as a single character.
   * Only the lower bits are used, upper ones above radix ignored.
   */
  private String encode(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length);
    for (byte idx : bytes) {
      sb.append(chars[idx]);
    }
    return sb.toString();
  }
  
  public static double logb( int a, int b ) {
    return Math.log(a) / Math.log(b);
  }
}
