package life.catalogue.db.type;

import life.catalogue.api.model.Agent;
import life.catalogue.db.type2.CustomAbstractTypeHandler;

import java.sql.SQLException;
import java.util.List;

import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.apache.ibatis.type.TypeException;

@MappedTypes(Agent.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class AgentTypeHandler extends CustomAbstractTypeHandler<Agent> {

  public AgentTypeHandler() {
    super("person");
  }

  @Override
  public String[] toAttributes(Agent obj) throws SQLException {
    return new String[]{obj.getGivenName(), obj.getFamilyName(), obj.getEmail(), obj.getOrcid()};
  }

  @Override
  public Agent fromAttributes(List<String> cols) throws SQLException {
    if (cols.size() == 4) {
      return new Agent(cols.get(0), cols.get(1), cols.get(2), cols.get(3));
    } else {
      // how can that be ?
      throw new TypeException("Failed to parse "+String.join(",", cols)+" to Agent");
    }
  }
}
