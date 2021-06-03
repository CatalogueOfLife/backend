package life.catalogue.db.type2;

import life.catalogue.db.type.BaseEnumSetTypeHandler;

import org.gbif.nameparser.api.Rank;

public class RankSetTypeHandler extends BaseEnumSetTypeHandler<Rank> {

  public RankSetTypeHandler() {
    super(Rank.class, true);
  }

}
