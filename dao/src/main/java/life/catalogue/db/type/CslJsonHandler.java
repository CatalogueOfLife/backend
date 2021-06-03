package life.catalogue.db.type;

import life.catalogue.api.model.CslData;
import life.catalogue.db.type2.JsonAbstractHandler;

import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import com.fasterxml.jackson.core.type.TypeReference;

@MappedTypes(CslData.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class CslJsonHandler extends JsonAbstractHandler<CslData> {
  
  public CslJsonHandler() {
    super(CslData.class.getSimpleName(), new TypeReference<CslData>() {});
  }
  
}
