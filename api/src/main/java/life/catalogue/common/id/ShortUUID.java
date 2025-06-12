package life.catalogue.common.id;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * A short, unambiguous and URL-safe UUID using only 64 URI safe characters.
 * This leads to IDs of 22 or 23 characters long - instead of the 36 for regular UUIDs.
 *
 * @author Harpreet Singh
 */
public final class ShortUUID {

  public static final Builder BUILDER = new Builder();
  public static final Supplier<String> ID_GEN = () -> random().toString();
  public static final int MIN_LEN = 20;

  private final String uuid;

  private ShortUUID(String uuid) {
    this.uuid = uuid;
  }

  @Override
  public String toString() {
    return uuid;
  }

  @Override
  public int hashCode() {
    return uuid.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (o == this)
      return true;

    if (!(o instanceof ShortUUID))
      return false;

    return o.toString().equals(uuid);
  }

  public static ShortUUID random() {
    return BUILDER.build();
  }

  public static class Builder {
    private final char[] alphabet;
    private final int alphabetSize;
    private final double factor;
    private final double length;

    /**
     * Uses only 64 URI safe characters: https://www.ietf.org/rfc/rfc3986.txt
    */
    public Builder() {
      this("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz_-");
    }

    public Builder(String alphabet) {
      this.alphabet = alphabet.toCharArray();
      Arrays.sort(this.alphabet);
      this.alphabetSize = this.alphabet.length;
      this.factor = Math.log(25d) / Math.log(alphabetSize);
      this.length = Math.ceil(factor * 16);
    }

    public ShortUUID build() {
      return build(UUID.randomUUID());
    }

    public ShortUUID build(UUID uuid) {
      String uuidStr = uuid.toString().replaceAll("-", "");

      BigInteger number = new BigInteger(uuidStr, 16);
      String encoded = encode(number, alphabet, (int) length);

      return new ShortUUID(encoded);
    }

    public String decode(String shortUuid) {
      return decode(shortUuid.toCharArray(), alphabet);
    }

    private String encode(final BigInteger bigInt, final char[] alphabet, final int padToLen) {
      BigInteger value = new BigInteger(bigInt.toString());
      BigInteger alphaSize = BigInteger.valueOf(alphabetSize);
      StringBuilder shortUuid = new StringBuilder();

      while (value.compareTo(BigInteger.ZERO) > 0) {
        BigInteger[] fracAndRemainder = value.divideAndRemainder(alphaSize);
        shortUuid.append(alphabet[fracAndRemainder[1].intValue()]);
        value = fracAndRemainder[0];
      }

      if (padToLen > 0) {
        int padding = Math.max(padToLen - shortUuid.length(), 0);
        for (int i = 0; i < padding; i++)
          shortUuid.append(alphabet[0]);
      }

      return shortUuid.toString();
    }

    private String decode(final char[] encoded, final char[] alphabet) {
      BigInteger sum = BigInteger.ZERO;
      BigInteger alphaSize = BigInteger.valueOf(alphabetSize);
      int charLen = encoded.length;

      for (int i = 0; i < charLen; i++) {
        sum = sum.add(alphaSize.pow(i).multiply(BigInteger.valueOf(
          Arrays.binarySearch(alphabet, encoded[i]))));
      }

      String str = sum.toString(16);

      // Pad the most significant bit (MSG) with 0 (zero) if the string is too short.
      if (str.length() < 32) {
        str = String.format("%32s", str).replace(' ', '0');
      }

      StringBuilder sb = new StringBuilder()
        .append(str.substring(0, 8))
        .append("-")
        .append(str.substring(8, 12))
        .append("-")
        .append(str.substring(12, 16))
        .append("-")
        .append(str.substring(16, 20))
        .append("-")
        .append(str.substring(20, 32));

      return sb.toString();
    }
  }

  public static ShortUUID build() {
    return BUILDER.build();
  }

  public static ShortUUID build(UUID uuid) {
    return BUILDER.build(uuid);
  }
}