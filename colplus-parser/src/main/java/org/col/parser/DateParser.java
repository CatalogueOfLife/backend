package org.col.parser;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.YearMonth;
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
 * <p>
 * Parses date strings into {@code FuzzyDate} instances. Fuzzy dates have at least their year set but any other chrono field may or may not
 * be known. Date strings are parsed by iterating over a list of {@link ParseSpec} instances. As soon as a {@code ParseSpec} is capabale of
 * successfully parsing the date string into a {@code java.time} object, the iteration stops. Therefore the more granular {@link ParseSpec}
 * instances should come first in the list. A {@code FuzzyDate} can be instantiated with a list of hard-coded {@link ParseSpec} instances or
 * with a properties file defining a {@link ParseSpec} instances. The layout of these properties of these properties files is explained in
 * src/main/resources/fuzzy-date.properties. The no-arg constructor of {@code FuzzyDate} uses exactly this properties file. It contains a
 * wide variety of date formats (roughly equal to the original GBIF date parser).
 * </p>
 * <p>
 * Note about performance: date parsing is said to be relatively expensive, but the cost does not seem to be incurred in the
 * pattern-matching phase, but in whatever happens next (the construction of a {@code TemporalAccessor} object. The parser flies through
 * non-matching patterns. Therefore the number of {@code ParseSpec} instances with which the parser is instantiated does not hugely impact
 * performance.
 * </p>
 */
public class DateParser implements Parser<FuzzyDate> {

  private static final Logger LOG = LoggerFactory.getLogger(DateParser.class);

  /**
   * A DateStringFilter optionally transforms a date string before it is passed on to a ParseSpec's DateTimeFormatter. It's
   * <code>filter</code> method may return null, which the FuzzyDateParser will take to indicate that the string cannot be parsed into a
   * date. {@code DateStringFilter} implementations must have a no-arg constructor.
   */
  public static interface DateStringFilter {
    String filter(String dateString);
  }

  /**
   * A ParseSpec specifies and fine-tunes how to parse a date string. The following can be specified when parsing a date string:
   * <ol>
   * <li>The {@code DateTimeFormatter} instance doing the actual parsing. <i>Required.</i>)
   * <li>A {@link DateStringFilter} that transforms the input date string before it is parsed by the {@code DateTimeFormatter}.
   * <i>Optional.</i>
   * <li>An array of {@code TemporalQuery} instances that specify the type of {@code java.time} objects that the date string should be
   * attempted to be parsed into. <i>Optional.</i> The {@code TemporalQuery} instances are passed on to the {@code parse} (if just one) c.q.
   * {@code parseBest} (if multiple) method of {@code DateTimeFormatter}.
   * <li>The original pattern string (if available) or the name of the pattern (e.g. "ISO_DATE_TIME"). <i>Optional.</i> Only used for
   * reporting purposes. Named patterns are currently confined to the (unqualified) names of the public static {@code DateTimeFormatter}
   * instances on the {@code DateTimeFormatter} class.
   * </ol>
   */
  public static final class ParseSpec {
    private final DateStringFilter filter;
    private final DateTimeFormatter formatter;
    private final TemporalQuery<?>[] parseInto;
    // The original pattern string. Only used for reporting purposes.
    private final String pattern;

    public ParseSpec(DateStringFilter filter, DateTimeFormatter formatter, TemporalQuery<?>[] parseInto) {
      this(filter, formatter, parseInto, formatter.toString());
    }

    public ParseSpec(DateStringFilter filter, DateTimeFormatter formatter, TemporalQuery<?>[] parseInto, String pattern) {
      this.filter = filter;
      this.formatter = formatter;
      this.parseInto = parseInto;
      this.pattern = pattern;
    }

  }

  /**
   * A FuzzyDateParser instance capable of parsing a wide range of date strings. It should at least match the parsing capabilities of the
   * original GBIF date library.
   */
  public static final DateParser PARSER = new DateParser();

  private final List<ParseSpec> parseSpecs;

  /**
   * Creates a {@code DateParser} capable of parsing a wide variety datetime formats. It uses a list of {
   */
  private DateParser() {
    this(DateParser.class.getResourceAsStream("/fuzzy-date.properties"));
  }

  /**
   * Creates a {@code DateParser} from the provided input stream,supposedly created from a properties file defining the {@code ParseSpec}
   * instances.
   * 
   * @param is
   */
  public DateParser(InputStream is) {
    this(getConfig(is));
  }

  /**
   * Creates a {@code DateParser} from the provided {@code Properties} object.
   * 
   * @param config
   */
  public DateParser(Properties config) {
    this(createParseSpecs(config));
  }

  /**
   * Creates a {@code DateParser} that uses the provided {@code ParseSpec} instances to parse date strings.
   * 
   * @param parseSpecs
   */
  public DateParser(List<ParseSpec> parseSpecs) {
    this.parseSpecs = parseSpecs;
  }

  /**
   * Parses the provided date string using any of the {@code ParseSpec} instances passed in or created in the constructors. Null values or
   * empty strings cause an empty {@code Optional} to be returned. Any other value either result in a non-empty {@code Optional} or an
   * {@code UnparsableException}.
   */
  public Optional<FuzzyDate> parse(String text) throws UnparsableException {
    boolean debug = LOG.isDebugEnabled();
    if (debug) {
      LOG.debug("Parsing \"{}\"", text);
    }
    if (StringUtils.isEmpty(text)) {
      return Optional.empty();
    }
    for (ParseSpec spec : parseSpecs) {
      String filtered = text;
      if (spec.filter != null) {
        if (debug) {
          LOG.debug("Applying filter {}", spec.filter.getClass());
        }
        filtered = spec.filter.filter(text);
        if (filtered == null) {
          continue;
        }
        if (debug) {
          LOG.debug("Filtered date string: \"{}\"", filtered);
        }
      }
      try {
        TemporalAccessor ta;
        if (spec.parseInto == null || spec.parseInto.length == 0) {
          ta = spec.formatter.parse(filtered);
        } else {
          if (spec.parseInto.length == 1) {
            ta = (TemporalAccessor) spec.formatter.parse(filtered, spec.parseInto[0]);
          } else {
            ta = spec.formatter.parseBest(filtered, spec.parseInto);
          }
        }
        if (debug) {
          LOG.debug("MATCH: {} matches {}", filtered, spec.pattern);
        }
        if (!ta.isSupported(YEAR)) {
          throw new UnparsableException("Missing year in date string: " + text);
        }
        return Optional.of(new FuzzyDate(ta, text));
      } catch (DateTimeException e) {
        // Next one then
        if (debug) {
          LOG.debug("{} does not match {}", filtered, spec.pattern);
        }
      }
    }
    if (debug) {
      LOG.debug("NO MATCH");
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
      // Check whether we're dealing a named format like ISO_LOCAL_DATE.
      String pattern = props.getProperty(type + "." + i + ".name");
      if (pattern != null) {
        DateStringFilter filter = getFilter(type, i, props);
        DateTimeFormatter formatter = getNamedFormatter(pattern);
        TemporalQuery<?>[] parseInto = getParseInto(type, i, props);
        parseSpecs.add(new ParseSpec(filter, formatter, parseInto, pattern));
      } else {
        pattern = props.getProperty(type + "." + i + ".pattern");
        if (pattern == null) {
          break;
        }
        DateTimeFormatterBuilder dtfb = new DateTimeFormatterBuilder();
        dtfb.appendPattern(pattern);
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
        parseSpecs.add(new ParseSpec(filter, formatter, parseInto, pattern));
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
   * In the future we could make everything nicely dynamic and allow multiple, configurable TemporalQuery instances to be passed on to the
   * DateTimeFormatter's parseBest() method. But for now we keep it simple: an OffsetDateTime-ish format MUST be parsable into an
   * OffsetDateTime; a LocalDateTime-ish format MUST be parsable into a LocalDateTime, etc. Note that the ParseSpec}class already allows you
   * to specify one or more TemporalQuery classes (to be called the from() method on), we just don't pick it up yet here, hence the unused
   * method parameters.
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
