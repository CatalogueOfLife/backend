package org.col.db.type2;

import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.col.api.model.Name;

@MappedTypes(Name.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class JsonIntMapHandler extends Json2TypeRefHandler<Map<Integer,Integer>> {
  
  public JsonIntMapHandler() {
    super("IntIntMap", new TypeReference<Map<Integer, Integer>>(){});
  }
  
}
