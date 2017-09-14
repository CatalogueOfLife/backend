package org.col.db.type;


import org.col.api.vocab.Language;

/**
 * MyBatis type handler for {@link Language}.
 * Persists languages as their lower case 2 letter iso code and uses NULL for the UNKNOWN enumeration.
 * Any unknown code or null string is converted to the UNKNWON enum entry.
 */
public class LanguageTypeHandler extends BaseEnumTypeHandler<String, Language> {

  @Override
  public String fromEnum(Language value) {
    return value == null || value == Language.UNKNOWN  ? null : value.getIso2LetterCode();
  }

  @Override
  public Language toEnum(String key) {
    return Language.fromIsoCode(key);
  }

}
