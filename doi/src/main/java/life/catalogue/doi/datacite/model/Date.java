package life.catalogue.doi.datacite.model;

import java.util.Objects;

public class Date {

  private String date;
  private DateType dateType;

  public Date() {
  }

  public Date(String date, DateType dateType) {
    this.date = date;
    this.dateType = dateType;
  }

  public String getDate() {
    return date;
  }

  public void setDate(String date) {
    this.date = date;
  }

  public DateType getDateType() {
    return dateType;
  }

  public void setDateType(DateType dateType) {
    this.dateType = dateType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Date)) return false;
    Date date1 = (Date) o;
    return Objects.equals(date, date1.date) && dateType == date1.dateType;
  }

  @Override
  public int hashCode() {
    return Objects.hash(date, dateType);
  }
}
