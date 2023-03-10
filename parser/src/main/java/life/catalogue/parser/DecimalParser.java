package life.catalogue.parser;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

/**
 * Parses doubles throwing UnparsableException in case the value is not empty but unparsable.
 */
public class DecimalParser implements Parser<Double> {
  public static final Parser<Double> PARSER = new DecimalParser();
  private static final NumberFormat US = NumberFormat.getNumberInstance(Locale.US);
  private static final NumberFormat DE = NumberFormat.getNumberInstance(Locale.GERMANY);

  @Override
  public Optional<Double> parse(String value) throws UnparsableException {
    if (StringUtils.isBlank(value)) {
      return Optional.empty();
    }
    value = value.replaceAll("\\s", "");

    try {
      return Optional.of(Double.parseDouble(value));
    } catch (NumberFormatException e) {
      // try different locales
      boolean comma = value.contains(",");
      boolean dot = value.contains(".");

      try {
        if (comma && dot) {
          int cIdx = value.lastIndexOf(",");
          int dIdx = value.lastIndexOf(".");
          if (cIdx > dIdx) {
            return Optional.of(parse(DE,value));
          } else {
            return Optional.of(parse(US,value));
          }

        } else if (comma) {
          return Optional.of(parse(DE,value));

        } else if (dot) {
          return Optional.of(parse(US,value));
        }
      } catch (ParseException ex) {
        // no further ideas
      }
    }
    throw new UnparsableException(Double.class, value);
  }

  private static synchronized double parse(NumberFormat format, String value) throws ParseException {
    return format.parse(value).doubleValue();
  }
}
