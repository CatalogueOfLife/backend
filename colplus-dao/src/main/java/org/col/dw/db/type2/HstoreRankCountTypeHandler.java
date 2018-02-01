package org.col.dw.db.type2;

import org.gbif.nameparser.api.Rank;

public class HstoreRankCountTypeHandler extends HstoreCountTypeHandlerBase<Rank> {

  public HstoreRankCountTypeHandler() {
    super(Rank.class);
  }

  @Override
  Rank toKeyAlt(String key) throws IllegalArgumentException {
    return Rank.valueOf(key.toUpperCase());
  }
}
