package life.catalogue.dw.jersey.exception;

import io.dropwizard.jersey.errors.ErrorMessage;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.EditorialDecision;
import life.catalogue.api.model.TreeNode;
import life.catalogue.db.mapper.*;
import org.apache.ibatis.exceptions.PersistenceException;
import org.junit.Test;

import javax.ws.rs.core.Response;

import java.util.UUID;

import static org.junit.Assert.*;

public class PersistenceExceptionMapperTest extends MapperTestBase<DecisionMapper> {
  public PersistenceExceptionMapperTest() {
    super(DecisionMapper.class);
  }

  @Test
  public void missing404() throws Exception {
    try {
      mapper(TreeMapper.class).get(9999, TreeNode.Type.CATALOGUE, DSID.of(9999, "1"));
    } catch (PersistenceException e) {
      PersistenceExceptionMapper map = new PersistenceExceptionMapper();
      Response resp = map.toResponse(e);
      assertNotNull(resp);
      assertEquals(404, resp.getStatus());
      ErrorMessage obj = (ErrorMessage) resp.getEntity();
      assertEquals(404, (int)obj.getCode());
      assertEquals("Dataset 9999 does not exist", obj.getMessage());
      assertNull(obj.getDetails());
    }
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
      assertNull(obj.getDetails());
    }
  }

  @Test
  public void uniqueDataset() throws Exception {
    try {
      Dataset d = DatasetMapperTest.create();
      d.setKey(999);
      mapper(DatasetMapper.class).createWithKey(d);
      mapper(DatasetMapper.class).createWithKey(d);
      commit();

    } catch (PersistenceException e) {
      PersistenceExceptionMapper map = new PersistenceExceptionMapper();
      Response resp = map.toResponse(e);
      assertNotNull(resp);
      assertEquals(400, resp.getStatus());
      ErrorMessage obj = (ErrorMessage) resp.getEntity();
      assertEquals(400, (int)obj.getCode());
      assertEquals("Dataset with key='999' already exists", obj.getMessage());
      assertNull(obj.getDetails());
    }
  }

  @Test
  public void uniqueAlias() throws Exception {
    Dataset d = DatasetMapperTest.create();
    d.setGbifKey(null);
    d.setAlias(null);
    testUnique(d, null);

    d.setAlias("uniqAli");
    testUnique(d, "Dataset with alias='uniqAli' already exists");

    d.setAlias(null);
    UUID gbif = UUID.randomUUID();
    d.setGbifKey(gbif);
    testUnique(d, "Dataset with gbif_key='"+gbif+"' already exists");
  }

  void testUnique(Dataset d, String expected) throws Exception {
    mapper(DatasetMapper.class).create(d);
    commit();

    try {
      mapper(DatasetMapper.class).create(d);
      assertNull("No unique error expected", expected);

    } catch (PersistenceException e) {
      PersistenceExceptionMapper map = new PersistenceExceptionMapper();
      Response resp = map.toResponse(e);
      assertNotNull(resp);
      assertEquals(400, resp.getStatus());
      ErrorMessage obj = (ErrorMessage) resp.getEntity();
      assertEquals(400, (int)obj.getCode());
      assertEquals(expected, obj.getMessage());
      assertNull(obj.getDetails());
      commit();
    }
  }

}