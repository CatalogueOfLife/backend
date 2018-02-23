package org.col.db.type;


import org.apache.ibatis.type.MappedTypes;
import org.col.api.vocab.Country;

/**
 * MyBatis TypeHandler for {@link Country} enum.
 * Persists contries as their upper case 2 letter iso code and uses NULL for the UNKNOWN enumeration.
 * Any unknown code or null string is converted to the UNKNWON enum entry.
 */
@MappedTypes(Country.class)
public class CountryTypeHandler extends BaseEnumTypeHandler<String, Country> {

  @Override
  public String fromEnum(Country value) {
    return value == null ? null : value.getIso2LetterCode();
  }

  /**
   * Matches a notnull key against the Country Enum. A key with no match against the country Enum will return
   * Country.UNKNOWN.
   *
   * @param key a 2 or 3 letter ISO 3166 Country code
   * @return matching Country Enum, or null if incoming key was null
   */
  @Override
  public Country toEnum(String key) {
    return Country.fromIsoCode(key).orElse(null);
  }
}
