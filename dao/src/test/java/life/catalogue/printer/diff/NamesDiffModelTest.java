package life.catalogue.printer.diff;

import life.catalogue.printer.NamesDiff;
import life.catalogue.api.jackson.ApiModule;

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;

public class NamesDiffModelTest {

  @Test
  public void counts() {
    NamesDiff d = new NamesDiff("a", "b");
    assertTrue(d.isIdentical());
    d.getRemoved().add("Aus aus");
    d.getAdded().add("Bus bus");
    d.getChanged().add(new ChangedName("Cus cus L.", "Cus cus L., 1758",
      List.of(new Chunk(ChunkOp.EQUAL, "Cus cus L."), new Chunk(ChunkOp.INSERT, ", 1758")), 92.0));
    assertFalse(d.isIdentical());
    assertEquals(1, d.getRemovedCount());
    assertEquals(1, d.getAddedCount());
    assertEquals(1, d.getChangedCount());
  }

  @Test
  public void jsonRoundtrips() throws Exception {
    NamesDiff d = new NamesDiff("dataset_3#1", "dataset_3#2");
    d.getAdded().add("Bus bus");
    d.getChanged().add(new ChangedName("Cus cus", "Cus cvs",
      List.of(new Chunk(ChunkOp.EQUAL, "Cus c"), new Chunk(ChunkOp.DELETE, "u"),
              new Chunk(ChunkOp.INSERT, "v"), new Chunk(ChunkOp.EQUAL, "s")), 88.0));
    var mapper = ApiModule.MAPPER;
    String json = mapper.writeValueAsString(d);
    assertTrue(json.contains("\"added\""));
    assertTrue(json.contains("\"chunks\""));
    assertTrue(json.toLowerCase().contains("insert"));
    NamesDiff back = mapper.readValue(json, NamesDiff.class);
    assertEquals(1, back.getAddedCount());
    assertEquals(1, back.getChangedCount());
    assertEquals("Cus cvs", back.getChanged().get(0).after());
  }
}
