package org.col.util.date;

import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.List;

public class DateParser {

  private List<DateTimeFormatter> formatters;

  public FuzzyDate parse(String text) {
    for (DateTimeFormatter dtf : formatters) {
      //TemporalAccessor ta = dtf.
    }
    return null;
  }

}
