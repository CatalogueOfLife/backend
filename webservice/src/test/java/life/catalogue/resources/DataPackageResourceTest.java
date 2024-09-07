package life.catalogue.resources;

import life.catalogue.api.datapackage.PackageDescriptor;

import org.junit.Test;

import static org.junit.Assert.*;

public class DataPackageResourceTest {

  @Test
  public void buildPackage() {
    var dr = new DataPackageResource();
    var dp = dr.buildPackage(new PackageDescriptor());
    assertEquals(16, dp.getResources().size());
    for (var r : dp.getResources()) {
      assertNotNull(r.getName());
      assertNotNull(r.getSchema());
      var s = r.getSchema();
      assertNotNull(s.getName());
      assertNotNull(s.getTitle());
      assertNotNull(s.getRowType());
      assertNotNull(s.getDescription());
    }
  }
}