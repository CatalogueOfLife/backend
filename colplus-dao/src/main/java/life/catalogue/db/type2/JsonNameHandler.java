package life.catalogue.db.type2;

import com.fasterxml.jackson.core.type.TypeReference;
import life.catalogue.api.model.Name;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

@MappedTypes(Name.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class JsonNameHandler extends JsonAbstractHandler<Name> {
  
  public JsonNameHandler() {
    super(Name.class.getSimpleName(), new TypeReference<Name>() {});
  }
  
}
