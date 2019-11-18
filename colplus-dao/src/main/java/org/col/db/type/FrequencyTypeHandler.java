package org.col.db.type;


import org.apache.ibatis.type.MappedTypes;
import org.col.api.vocab.Frequency;

/**
 * MyBatis type handler for {@link Frequency}.
 * Converts the frequency into days for easy comparison of intervals in SQL.
 * Null is converted to the WEEKLY enum entry.
 */
@MappedTypes(Frequency.class)
public class FrequencyTypeHandler extends BaseEnumTypeHandler<Integer, Frequency> {
  
  @Override
  public Integer fromEnum(Frequency value) {
    return value == null ? Frequency.WEEKLY.getDays() : value.getDays();
  }
  
  @Override
  public Frequency toEnum(Integer days) {
    return Frequency.fromDays(days);
  }
  
}
