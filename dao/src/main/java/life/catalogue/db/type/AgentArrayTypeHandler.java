package life.catalogue.db.type;

import life.catalogue.api.model.Agent;
import life.catalogue.db.type2.CustomArrayAbstractTypeHandler;

import java.sql.SQLException;
import java.util.List;

import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.apache.ibatis.type.TypeException;

@MappedTypes(Agent.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class AgentArrayTypeHandler extends CustomArrayAbstractTypeHandler<Agent> {

  public AgentArrayTypeHandler() {
    super(AgentTypeHandler.PGTYPE);
  }

  @Override
  public String[] toAttributes(Agent obj) throws SQLException {
    return AgentTypeHandler.to(obj);
  }

  @Override
  public Agent fromAttributes(List<String> cols) throws SQLException {
    return AgentTypeHandler.from(cols);
  }
}
