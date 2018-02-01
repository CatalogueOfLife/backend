package org.col.dw.db.type;


import org.apache.ibatis.type.MappedTypes;
import org.col.dw.api.vocab.Language;

/**
 * MyBatis type handler for {@link Language}.
 * Persists languages as their lower case 2 letter iso code and uses NULL for the UNKNOWN enumeration.
 * Any unknown code or null string is converted to the UNKNWON enum entry.
 */
@MappedTypes(Language.class)
public class LanguageTypeHandler extends BaseEnumTypeHandler<String, Language> {

  @Override
  public String fromEnum(Language value) {
    return value == null || value == Language.UNKNOWN ? null : value.getIso2LetterCode();
  }

  @Override
  public Language toEnum(String key) {
    return Language.fromIsoCode(key);
  }

}
