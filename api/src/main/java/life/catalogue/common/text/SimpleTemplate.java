package life.catalogue.common.text;

import com.google.common.base.Preconditions;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.IllegalFormatException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple String formatting using property names in curly brackets.
 * For dates an optional format can be given following the {@link DateTimeFormatter} syntax.
 * If provided, the format should follow the property name and be separated by a comma.
 * The current time is available in a special "date" property.
 *
 * Examples: "Hello {name}! Today is {date,EEE}"
 * produces "Hello Jim! Today is Tuesday"
 */
public class SimpleTemplate {
  private final static Pattern ARG_PATTERN = Pattern.compile("\\{([a-zA-Z_0-9]+)(?:,([^}]+))?}");
  private static Locale DEFAULT_LOCALE = Locale.UK;

  public static String render(String template, Object arg) throws IllegalFormatException {
    return render(template, arg, DEFAULT_LOCALE);
  }

  /**
   *
   * @param template
   * @param arg
   * @param locale
   * @return
   * @throws IllegalArgumentException when the argument does not have the properties listed in the template or the template is invalid
   */
  public static String render(String template, Object arg, Locale locale) throws IllegalArgumentException {
    Preconditions.checkNotNull(template);
    try {
      BeanInfo info = Introspector.getBeanInfo(arg.getClass());
      Matcher matcher = ARG_PATTERN.matcher(template);
      StringBuffer buffer = new StringBuffer();
      int idx = 0;
      while (matcher.find()) {
        matcher.appendReplacement(buffer, propertyValue(matcher.group(1), matcher.group(2), info, arg));
        idx++;
      }
      matcher.appendTail(buffer);
      return buffer.toString();
    } catch (IntrospectionException e) {
      throw new RuntimeException(e);
    }
  }

  private static String temporalValue(TemporalAccessor arg, String format) {
    DateTimeFormatter df;
    if (StringUtils.hasContent(format)) {
      df = DateTimeFormatter.ofPattern(format);
    } else {
      df = DateTimeFormatter.ISO_DATE;
    }
    return df.format(arg);
  }

  private static String propertyValue(String prop, String format, BeanInfo info, Object arg) {
    if (prop.equalsIgnoreCase("date")) {
      return temporalValue(LocalDate.now(), format);
    }
    for (PropertyDescriptor pd : info.getPropertyDescriptors()) {
      if (pd.getName().equalsIgnoreCase(prop)) {
        try {
          Object value = pd.getReadMethod().invoke(arg);
          if (value == null) {
            return "";
          } else if (value instanceof TemporalAccessor) {
            return temporalValue((TemporalAccessor)value, format);
          } else {
            return value.toString();
          }
        } catch (IllegalAccessException | InvocationTargetException e) {
          return "";
        }
      }
    }
    throw new IllegalArgumentException("No property "+prop+" exists in argument of type "+info.getBeanDescriptor().getBeanClass().getSimpleName());
  }
}
