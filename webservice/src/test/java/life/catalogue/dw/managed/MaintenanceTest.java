package life.catalogue.dw.managed;

import java.io.File;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import jakarta.ws.rs.InternalServerErrorException;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeFalse;

public class MaintenanceTest {

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  @Test
  public void createsMissingFileAndRestores() throws Exception {
    File f = new File(tmp.getRoot(), "sub/.status.json");
    assertTrue(Maintenance.writable(f));
    Maintenance m = new Maintenance(f);
    assertFalse(m.isOn());

    Map<String, Object> status = m.set(true, " Updating search index ");
    assertTrue(f.exists());
    assertEquals(true, status.get("maintenance"));
    assertEquals("Updating search index", status.get("message"));

    // state survives a restart
    Maintenance m2 = new Maintenance(f);
    assertTrue(m2.isOn());
    assertEquals("Updating search index", m2.status().get("message"));

    m2.set(null, null);
    assertFalse(m2.isOn());
    assertNull(m2.status().get("message"));
  }

  @Test
  public void failedWriteKeepsState() throws Exception {
    File dir = tmp.newFolder("readonly");
    File f = new File(dir, ".status.json");
    assertTrue(dir.setWritable(false));
    try {
      // root ignores file permissions
      assumeFalse(dir.canWrite());
      assertFalse(Maintenance.writable(f));

      Maintenance m = new Maintenance(f);
      var e = assertThrows(InternalServerErrorException.class, () -> m.set(true, "Updating"));
      assertTrue(e.getMessage(), e.getMessage().contains("directory is not writable"));
      assertFalse(m.isOn());
      assertNull(m.status().get("message"));
      assertFalse(f.exists());
    } finally {
      dir.setWritable(true);
    }
  }
}
