package life.catalogue.common.text;

import com.google.common.base.Preconditions;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.text.DateFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple String formatting using property names in curly brackets.
 * For dates an optional format can be given following the {@link DateTimeFormatter} syntax.
 * If provided, the format should follow the property name and be separated by a comma.
 * The current time is available in a special "date" property.
 *
 * We extend the regular date formatting patterns {@link DateFormatSymbols.patternChars} with
 * a custom entry "ddd" to format the day of the month with an english suffix, e.g. 1st, 2nd, 3rd, 4th.
 *
 * Examples: "Hello {name}! Today is {date,EEE}"
 * produces "Hello Jim! Today is Tuesday"
 */
public class SimpleTemplate {
  private final static Pattern ARG_PATTERN = Pattern.compile("\\{([a-zA-Z_0-9]+)(?:,([^}]+))?}");

  /**
   *
   * @param template
   * @param arg
   * @return
   * @throws IllegalArgumentException when the argument does not have the properties listed in the template or the template is invalid
   */
  public static String render(String template, Object arg) throws IllegalArgumentException {
    Preconditions.checkNotNull(template);
    try {
      boolean isMap = arg instanceof Map;
      BeanInfo info = null;
      Map<?,?> map = null;
      if (isMap) {
        map = (Map<?,?>) arg;
      } else {
        info = Introspector.getBeanInfo(arg.getClass());
      }
      Matcher matcher = ARG_PATTERN.matcher(template);
      StringBuffer buffer = new StringBuffer();
      while (matcher.find()) {
        matcher.appendReplacement(buffer, isMap ?
          mapValue(matcher.group(1), matcher.group(2), map) :
          propertyValue(matcher.group(1), matcher.group(2), info, arg)
        );
      }
      matcher.appendTail(buffer);
      return buffer.toString();
    } catch (IntrospectionException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * We extend the regular date formatting patterns {@link DateFormatSymbols.patternChars} with
   * a custom entry ddd to format the day of the month with an english suffix, e.g. 1st, 2nd, 3rd, 4th.
   *
   * @param arg
   * @param format
   * @return
   */
  private static String temporalValue(TemporalAccessor arg, String format) {
    final Pattern DAY_SUFFIX = Pattern.compile("(?<!d)ddd");
    final String PLACEHOLDER = "@€§&";

    if (StringUtils.hasContent(format)) {
      // looks for special "ddd" date pattern
      Matcher m = DAY_SUFFIX.matcher(format);
      String placeholderValue = null;
      if (m.find()) {
        int day = arg.get(ChronoField.DAY_OF_MONTH);
        placeholderValue = day+dayOfMonthSuffix(day);
        format = DAY_SUFFIX.matcher(format).replaceAll(PLACEHOLDER);
      }
      DateTimeFormatter df = DateTimeFormatter.ofPattern(format);
      if (placeholderValue != null) {
        return df.format(arg).replaceAll(PLACEHOLDER, placeholderValue);
      }
      return df.format(arg);
    } else {
      return DateTimeFormatter.ISO_DATE.format(arg);
    }
  }

  private static String mapValue(String key, String format, Map<?, ?> arg) {
    if (key.equalsIgnoreCase("date")) {
      return temporalValue(LocalDate.now(), format);
    }
    if (arg.containsKey(key)) {
      return str(arg.get(key), format);
    }
    throw new IllegalArgumentException("No key "+key+" exists in map argument of size " + arg.size());
  }

  private static String str(Object value, String format){
    if (value == null) {
      return "";
    } else if (value instanceof TemporalAccessor) {
      return temporalValue((TemporalAccessor)value, format);
    } else {
      return value.toString();
    }
  }

  private static String propertyValue(String prop, String format, BeanInfo info, Object arg) {
    if (prop.equalsIgnoreCase("date")) {
      return temporalValue(LocalDate.now(), format);
    }
    for (PropertyDescriptor pd : info.getPropertyDescriptors()) {
      if (pd.getName().equalsIgnoreCase(prop)) {
        try {
          return str(pd.getReadMethod().invoke(arg), format);
        } catch (IllegalAccessException | InvocationTargetException e) {
          return "";
        }
      }
    }
    throw new IllegalArgumentException("No property "+prop+" exists in argument of type "+info.getBeanDescriptor().getBeanClass().getSimpleName());
  }

  public static String dayOfMonthSuffix(final int n) {
    Preconditions.checkArgument(n >= 1 && n <= 31, "illegal day of month: " + n);
    if (n >= 11 && n <= 13) {
      return "th";
    }
    switch (n % 10) {
      case 1:  return "st";
      case 2:  return "nd";
      case 3:  return "rd";
      default: return "th";
    }
  }
}
