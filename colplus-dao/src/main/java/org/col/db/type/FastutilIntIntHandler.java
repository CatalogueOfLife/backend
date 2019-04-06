package org.col.db.type;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.col.db.type2.JsonAbstractHandler;

@MappedTypes(Int2IntMap.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class FastutilIntIntHandler extends JsonAbstractHandler<Int2IntMap> {
  
  public FastutilIntIntHandler() {
    super(Int2IntMap.class);
  }
  
}
