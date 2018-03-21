package org.col.parser;

import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.YEAR;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.time.DateTimeException;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import org.apache.commons.lang3.StringUtils;
import org.col.util.date.FuzzyDate;

/**
 * Parses date strings into FuzzyDate instances.
 * 
 */
public class FuzzyDateParser implements Parser<FuzzyDate> {

  /**
   * A ParseSpec specifies how to parse a date string. Currently it just contains the
   * DateTimeFormatter with which to parse the date string. But it is easily forseeable that we
   * could make date parsing more sophisticated through things external to the DateTimeFormatter.
   * For example, we could allow for an option to use a "parse best" strategy (see
   * {@link DateTimeFormatter#parseBest(CharSequence, java.time.temporal.TemporalQuery...)
   * DateTimeFormatter.parseBest}).
   */
  public static class ParseSpec {
    @SuppressWarnings("unused")
    private final DateTimeFormatter formatter;

    public ParseSpec(DateTimeFormatter formatter) {
      this.formatter = formatter;
    }
  }

  public static final FuzzyDateParser PARSER = new FuzzyDateParser();

  private final List<ParseSpec> parseSpecs;

  private FuzzyDateParser() {
    this(FuzzyDateParser.class.getResourceAsStream("/fuzzy-date.properties"));
  }

  public FuzzyDateParser(InputStream is) {
    this(getConfig(is));
  }

  public FuzzyDateParser(Properties config) {
    this(createFormatters(config));
  }

  public FuzzyDateParser(List<ParseSpec> parseSpecs) {
    this.parseSpecs = parseSpecs;
  }

  public Optional<FuzzyDate> parse(String text) throws UnparsableException {
    if (StringUtils.isEmpty(text)) {
      return Optional.empty();
    }
    for (DateTimeFormatter dtf : parseSpecs) {
      try {
        TemporalAccessor ta = dtf.parse(text);
        if (!ta.isSupported(YEAR)) {
          throw new UnparsableException("Missing year in date string: " + text);
        }
        return Optional.of(new FuzzyDate(ta, text));
      } catch (DateTimeException e) {
        // Next one then
      }
    }
    throw new UnparsableException("Invalid date: " + text);
  }

  private static List<ParseSpec> createFormatters(Properties props) {
    String defaultCaseSensitive = props.getProperty("caseSensitive", "false");
    List<ParseSpec> formatters = new ArrayList<>();
    String[] types = {"OffsetDateTime", "LocalDateTime", "LocalDate", "YearMonth", "Year"};
    for (String type : types) {
      formatters.addAll(createFormattersForType(type, props));
    }
    for (int i = 0;; i++) {
      String property = "formatter." + i;
      if (!props.containsKey(property)) {
        break;
      }
      String pattern = props.getProperty(property);
      String caseSensitive = props.getProperty(property + ".caseSenstive", defaultCaseSensitive);
      DateTimeFormatterBuilder dtfb = new DateTimeFormatterBuilder();
      dtfb.appendPattern(pattern);
      if (caseSensitive.equalsIgnoreCase("true")) {
        dtfb.parseCaseSensitive();
      }
      DateTimeFormatter dtf = dtfb.toFormatter();
      dtf.withResolverFields(YEAR, MONTH_OF_YEAR, DAY_OF_MONTH);
      formatters.add(new ParseSpec(dtfb.toFormatter()));
    }
    return formatters;
  }

  private static List<ParseSpec> createFormattersForType(String type, Properties props) {
    String defaultCaseSensitive = props.getProperty("caseSensitive", "false");
    List<ParseSpec> formatters = new ArrayList<>();
    for (int i = 0;; i++) {
      String name = props.getProperty(type + "." + i + ".name");
      if (name != null) {
        /*
         * For example: "ISO_LOCAL_DATE_TIME", the name of a public static final field of the
         * DateTimeFormatter class.
         */
        formatters.add(new ParseSpec(getNamedFormatter(name)));
        continue;
      }
      String pattern = props.getProperty(type + "." + i + ".pattern");
      if (pattern != null) {
        DateTimeFormatterBuilder dtfb = new DateTimeFormatterBuilder();
        dtfb.appendPattern(pattern);
        String caseSensitive =
            props.getProperty(type + "." + i + ".caseSensitive", defaultCaseSensitive);
        if (caseSensitive.equalsIgnoreCase("true")) {
          dtfb.parseCaseSensitive();
        }
        formatters.add(new ParseSpec(dtfb.toFormatter()));
      }
      break;
    }
    return formatters;
  }

  private static Properties getConfig(InputStream is) {
    Properties config = new Properties();
    try {
      config.load(is);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return config;
  }

  private static DateTimeFormatter getNamedFormatter(String name) {
    try {
      Field f = DateTimeFormatter.class.getDeclaredField(name);
      return (DateTimeFormatter) f.get(null);
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }

}
