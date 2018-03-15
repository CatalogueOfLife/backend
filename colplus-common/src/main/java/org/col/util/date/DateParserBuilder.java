package org.col.util.date;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.apache.commons.lang3.StringUtils;

public class DateParserBuilder {

  private List<DateTimeFormatter> formatters = new ArrayList<>();

  private void configure() {
    Properties props = new Properties();
    try {
      props.load(getClass().getResourceAsStream("fuzzy-date.properties"));
    } catch (IOException e) {
    }
    for (int i = 0;; i++) {
      String property = "fmt." + i;
      if (!props.containsKey(property)) {
        break;
      }
      String pattern = props.getProperty(property);
      String caseSensitive = props.getProperty(property + ".caseSenstive", "false");
      DateTimeFormatterBuilder dtfb = new DateTimeFormatterBuilder();
      dtfb.appendPattern(pattern);
      if (!caseSensitive.equalsIgnoreCase("true")) {
        dtfb.parseCaseInsensitive();
      }
      formatters.add(dtfb.toFormatter());
    }
  }

}
