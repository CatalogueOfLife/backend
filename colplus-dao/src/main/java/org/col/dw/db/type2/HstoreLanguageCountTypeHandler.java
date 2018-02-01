package org.col.dw.db.type2;

import org.col.dw.api.vocab.Language;

public class HstoreLanguageCountTypeHandler extends HstoreCountTypeHandlerBase<Language> {

  public HstoreLanguageCountTypeHandler() {
    super(Language.class);
  }

  @Override
  Language toKeyAlt(String key) throws IllegalArgumentException {
    return Language.fromIsoCode(key);
  }
}
