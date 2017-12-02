package org.col.db.type;


import org.apache.ibatis.type.MappedTypes;
import org.gbif.nameparser.api.Rank;

@MappedTypes(Rank.class)
public class RankTypeHandler extends BaseEnumTypeHandler<String, Rank> {

  @Override
  public String fromEnum(Rank value) {
    return value == null ? null : value.name().toLowerCase();
  }

  @Override
  public Rank toEnum(String key) {
    if (key == null) {
      return null;
    } else {
      return Rank.valueOf(key.toUpperCase());
    }
  }
}
