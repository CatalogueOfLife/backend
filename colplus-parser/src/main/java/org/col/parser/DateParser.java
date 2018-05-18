package org.col.parser;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQuery;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.col.common.date.FuzzyDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.time.temporal.ChronoField.YEAR;

/**
 * Parses date strings into FuzzyDate instances.
 * 
 */
public class DateParser implements Parser<FuzzyDate> {
  private static final Logger LOG = LoggerFactory.getLogger(DateParser.class);

  /**
   * A DateStringFilter optionally transforms a date string before it is parsed by a ParseSpec's
   * DateTimeFormatter. It's <code>filter</code> method may return null, which the FuzzyDateParser
   * will take as a strong suggestion that the string cannot be parsed. DateStringFilter
   * implementations must have a no-arg constructor.
   */
  public static interface DateStringFilter {
    String filter(String dateString);
  }

  /**
   * A ParseSpec specifies how to parse a date string.
   */
  public static final class ParseSpec {
    private final DateStringFilter filter;
    private final DateTimeFormatter formatter;
    private final TemporalQuery<?>[] parseInto;

    public ParseSpec(DateStringFilter filter, DateTimeFormatter formatter,
        TemporalQuery<?>[] parseInto) {
      this.filter = filter;
      this.formatter = formatter;
      this.parseInto = parseInto;
    }

  }

  /**
   * A FuzzyDateParser instance capable of parsing a wide range of date string. It should at least
   * match the parsing capabilities of the original GBIF date library.
   */
  public static final DateParser PARSER = new DateParser();

  private final List<ParseSpec> parseSpecs;

  private DateParser() {
    this(DateParser.class.getResourceAsStream("/fuzzy-date.properties"));
  }

  public DateParser(InputStream is) {
    this(getConfig(is));
  }

  public DateParser(Properties config) {
    this(createParseSpecs(config));
  }

  public DateParser(List<ParseSpec> parseSpecs) {
    this.parseSpecs = parseSpecs;
  }

  public Optional<FuzzyDate> parse(String text) throws UnparsableException {
    if (StringUtils.isEmpty(text)) {
      return Optional.empty();
    }
    for (ParseSpec parseSpec : parseSpecs) {
      String filtered = text;
      if (parseSpec.filter != null) {
        filtered = parseSpec.filter.filter(text);
        if (filtered == null) {
          continue;
        }
      }
      try {
        TemporalAccessor ta;
        if (parseSpec.parseInto == null || parseSpec.parseInto.length == 0) {
          ta = parseSpec.formatter.parse(filtered);
        } else {
          try {
            if (parseSpec.parseInto.length == 1) {
              ta = (TemporalAccessor) parseSpec.formatter.parse(filtered, parseSpec.parseInto[0]);
            } else {
              ta = parseSpec.formatter.parseBest(filtered, parseSpec.parseInto);
            }
          } catch (DateTimeException e) {
            /*
             * The date string is not parsable into to specified target(s), e.g. into a YearMonth
             * instance. However, it might still be parsable into one of java.time's hidden
             * implementations of TemporalAccessor. For us, since we proceed from the richest
             * patterns to to poorest, the fact that a date string matches a pattern is more
             * important than the fact that it can be parsed into a specific implementation of
             * TemporalAccessor.
             */
            ta = parseSpec.formatter.parse(filtered);
          }
        }
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

  private static List<ParseSpec> createParseSpecs(Properties props) {
    List<ParseSpec> parseSpecs = new ArrayList<>();
    String[] types = {"OffsetDateTime", "LocalDateTime", "LocalDate", "YearMonth", "Year"};
    for (String type : types) {
      parseSpecs.addAll(createParseSpecsForType(type, props));
    }
    return parseSpecs;
  }

  private static List<ParseSpec> createParseSpecsForType(String type, Properties props) {
    List<ParseSpec> parseSpecs = new ArrayList<>();
    for (int i = 0;; i++) {
      String val = props.getProperty(type + "." + i + ".name");
      if (val != null) {
        DateStringFilter filter = getFilter(type, i, props);
        DateTimeFormatter formatter = getNamedFormatter(val);
        TemporalQuery<?>[] parseInto = getParseInto(type, i, props);
        parseSpecs.add(new ParseSpec(filter, formatter, parseInto));
      } else {
        val = props.getProperty(type + "." + i + ".pattern");
        if (val == null) {
          break;
        }
        DateTimeFormatterBuilder dtfb = new DateTimeFormatterBuilder();
        dtfb.appendPattern(val);
        if (parseCaseSensitive(type, i, props)) {
          dtfb.parseCaseSensitive();
        }
        ResolverStyle rs = getResolverStyle(type, i, props);
        if (rs == ResolverStyle.LENIENT) {
          dtfb.parseLenient();
        } else if (rs == ResolverStyle.STRICT) {
          dtfb.parseStrict();
        }
        DateStringFilter filter = getFilter(type, i, props);
        DateTimeFormatter formatter = dtfb.toFormatter();
        TemporalQuery<?>[] parseInto = getParseInto(type, i, props);
        parseSpecs.add(new ParseSpec(filter, formatter, parseInto));
      }
    }
    return parseSpecs;
  }

  private static boolean parseCaseSensitive(String type, int i, Properties props) {
    String dfault = props.getProperty("caseSensitive", "false");
    String cs = props.getProperty(type + "." + i + ".caseSensitive", dfault);
    if (cs.isEmpty()) {
      return false;
    }
    return cs.toLowerCase().equals("true");
  }

  private static ResolverStyle getResolverStyle(String type, int i, Properties props) {
    String dfault = props.getProperty("resolverStyle", "LENIENT");
    String style = props.getProperty(type + "." + i + ".resolverStyle", dfault);
    if (style.isEmpty()) {
      return ResolverStyle.LENIENT;
    }
    switch (style.toUpperCase()) {
      case "SMART":
        return ResolverStyle.SMART;
      case "STRICT":
        return ResolverStyle.STRICT;
      case "LENIENT":
      case "":
        return ResolverStyle.LENIENT;
      default:
        throw new IllegalArgumentException("Invalid value for property resolvertStyle: " + style);
    }
  }

  public static DateStringFilter getFilter(String type, int i, Properties props) {
    String className = props.getProperty(type + "." + i + ".filter");
    if (StringUtils.isBlank(className)) {
      return null;
    }
    try {
      return (DateStringFilter) Class.forName(className).newInstance();
    } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
      throw new IllegalArgumentException("Could not create filter for : " + className);
    }
  }

  /*
   * In the future we could make this more dynamic and return multiple, configurable TemporalQuery
   * instances to be passed to DateTimeFormatter.parseBest, but for now this is OK.
   */
  @SuppressWarnings("unused")
  private static TemporalQuery<?>[] getParseInto(String type, int i, Properties props) {
    switch (type) {
      case "OffsetDateTime":
        return new TemporalQuery[] {OffsetDateTime::from};
      case "LocalDateTime":
        return new TemporalQuery[] {LocalDateTime::from};
      case "LocalDate":
        return new TemporalQuery[] {LocalDate::from};
      case "YearMonth":
        return new TemporalQuery[] {YearMonth::from};
      case "Year":
      default:
        return new TemporalQuery[] {Year::from};
    }
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
    } catch (Throwable t) {
      throw new IllegalArgumentException(t);
    }
  }

}
