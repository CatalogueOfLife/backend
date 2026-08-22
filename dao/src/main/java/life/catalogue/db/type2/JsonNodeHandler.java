package life.catalogue.db.type2;

import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Stores an arbitrary, schemaless json document given as a Jackson JsonNode in a postgres jsonb column.
 */
@MappedTypes(JsonNode.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class JsonNodeHandler extends JsonAbstractHandler<JsonNode> {

  public JsonNodeHandler() {
    super(JsonNode.class.getSimpleName(), new TypeReference<JsonNode>() {});
  }
}
