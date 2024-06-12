package life.catalogue.resources;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.User;
import life.catalogue.dw.jersey.provider.DatasetPatchMessageBodyRW;

import java.lang.reflect.Method;

import jakarta.ws.rs.core.MediaType;

import org.apache.ibatis.session.SqlSession;
import org.junit.Test;

import static org.junit.Assert.*;

public class DatasetPatchResourceTest {
  DatasetPatchMessageBodyRW rw = new DatasetPatchMessageBodyRW();

  @Test
  public void patchAnnotations() throws Exception {
    var update = DatasetPatchResource.class.getMethod("update", int.class, Integer.class, Dataset.class, User.class, SqlSession.class);
    assertPatchRW(update, false);

    var create = DatasetPatchResource.class.getMethod("create", int.class, Dataset.class, User.class, SqlSession.class);
    assertPatchRW(create, false);

    var get = DatasetPatchResource.class.getMethod("get", int.class, Integer.class, SqlSession.class);
    assertPatchRW(get, true);
  }

  private void assertPatchRW(Method method, boolean assertWritable) {
    for (var p : method.getParameters()) {
      var annos = p.getDeclaredAnnotations();
      assertEquals(p.getType().equals(Dataset.class), rw.isReadable(Dataset.class, null, annos, MediaType.APPLICATION_JSON_TYPE));
    }

    assertEquals(assertWritable, rw.isWriteable(Dataset.class, null, method.getDeclaredAnnotations(), MediaType.APPLICATION_JSON_TYPE));
  }
}