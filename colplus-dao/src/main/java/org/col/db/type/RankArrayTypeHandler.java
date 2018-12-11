package org.col.db.type;

import org.col.db.type2.EnumArrayTypeHandler;
import org.gbif.nameparser.api.Rank;

public class RankArrayTypeHandler extends EnumArrayTypeHandler<Rank> {

  public RankArrayTypeHandler() {
    super(Rank.class);
  }

}
