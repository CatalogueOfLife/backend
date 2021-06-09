package life.catalogue.db.type;

import life.catalogue.api.model.Agent;
import life.catalogue.api.vocab.Country;
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
  static final String PGTYPE = "agent";

  public AgentTypeHandler() {
    super(PGTYPE);
  }

  static String[] to(Agent obj) throws SQLException {
    return new String[]{obj.getOrcid(), obj.getGiven(), obj.getFamily(),
      obj.getRorid(), obj.getOrganisation(), obj.getDepartment(), obj.getCity(), obj.getState(), obj.getCountryCode(),
      obj.getEmail(), obj.getUrl(), obj.getNote()
    };
  }

  static Agent from(List<String> cols) throws SQLException {
    if (cols.size() == 12) {
      return new Agent(cols.get(0), cols.get(1), cols.get(2),
        cols.get(3), cols.get(4), cols.get(5), cols.get(6), cols.get(7), Country.fromIsoCode(cols.get(8)).orElse(null),
        cols.get(9), cols.get(10), cols.get(11));
    } else {
      // how can that be ?
      throw new TypeException("Failed to parse "+String.join(",", cols)+" to Agent");
    }
  }

  @Override
  public String[] toAttributes(Agent obj) throws SQLException {
    return to(obj);
  }

  @Override
  public Agent fromAttributes(List<String> cols) throws SQLException {
    return from(cols);
  }
}
