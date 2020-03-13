package life.catalogue.parser;

import org.apache.commons.lang3.StringUtils;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;
import java.util.Optional;

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
            return Optional.of(DE.parse(value).doubleValue());
          } else {
            return Optional.of(US.parse(value).doubleValue());
          }

        } else if (comma) {
          return Optional.of(DE.parse(value).doubleValue());

        } else if (dot) {
          return Optional.of(US.parse(value).doubleValue());
        }
      } catch (ParseException ex) {
        // no further ideas
      }
    }
    throw new UnparsableException(Double.class, value);
  }
}
