package life.catalogue.db.type2;

import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import life.catalogue.api.model.Name;

@MappedTypes(Name.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class JsonNameHandler extends JsonAbstractHandler<Name> {
  
  public JsonNameHandler() {
    super(Name.class);
  }
  
}
