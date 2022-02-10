package life.catalogue.common.datapackage;

import life.catalogue.api.datapackage.PackageDescriptor;
import life.catalogue.coldp.ColdpTerm;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class DataPackageBuilderTest {

  @Test
  public void buildFullColdp() {
    var dp = new DataPackageBuilder().build(new PackageDescriptor());
    assertEquals(ColdpTerm.RESOURCES.size(), dp.getResources().size());

    dp.getResources().forEach(r -> {
      assertNotNull(r.getName());
      r.getSchema().getFields().forEach(f -> {
        assertNotNull("Missing field name for " + f, f.getName());
        assertNotNull("Missing type for field " + f.getName(), f.getType());
      });
    });
  }
}