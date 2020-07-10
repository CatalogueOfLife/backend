package life.catalogue.dw.jersey.exception;

import io.dropwizard.jersey.errors.ErrorMessage;
import life.catalogue.api.model.EditorialDecision;
import life.catalogue.db.mapper.DecisionMapper;
import life.catalogue.db.mapper.DecisionMapperTest;
import life.catalogue.db.mapper.MapperTestBase;
import org.apache.ibatis.exceptions.PersistenceException;
import org.junit.Test;

import javax.ws.rs.core.Response;

import static org.junit.Assert.*;

public class PersistenceExceptionMapperTest extends MapperTestBase<DecisionMapper> {
  public PersistenceExceptionMapperTest() {
    super(DecisionMapper.class);
  }

  @Test
  public void uniqueDecisions() throws Exception {
    try {
      EditorialDecision d = DecisionMapperTest.create(11);
      mapper().create(d);
      mapper().create(d);
      commit();

    } catch (PersistenceException e) {
      PersistenceExceptionMapper map = new PersistenceExceptionMapper();
      Response resp = map.toResponse(e);
      assertNotNull(resp);
      assertEquals(400, resp.getStatus());
      ErrorMessage obj = (ErrorMessage) resp.getEntity();
      assertEquals(400, (int)obj.getCode());
      assertEquals("Decision already exists", obj.getMessage());
      assertTrue(obj.getDetails().startsWith("###"));
    }
  }

}