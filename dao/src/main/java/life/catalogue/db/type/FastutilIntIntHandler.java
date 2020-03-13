package life.catalogue.db.type;

import com.fasterxml.jackson.core.type.TypeReference;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import life.catalogue.db.type2.JsonAbstractHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

@MappedTypes(Int2IntOpenHashMap.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class FastutilIntIntHandler extends JsonAbstractHandler<Int2IntOpenHashMap> {
  
  public FastutilIntIntHandler() {
    super(Int2IntOpenHashMap.class.getSimpleName(), new TypeReference<Int2IntOpenHashMap>() {});
  }
  
}
