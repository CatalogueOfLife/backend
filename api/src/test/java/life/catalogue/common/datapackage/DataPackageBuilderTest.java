package life.catalogue.common.datapackage;

import life.catalogue.api.datapackage.PackageDescriptor;
import life.catalogue.coldp.ColdpTerm;

import life.catalogue.common.io.Resources;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class DataPackageBuilderTest {

  @Test
  public void buildFullColdp() throws IOException {

    var dp = new DataPackageBuilder()
        .docs()
        .build(new PackageDescriptor());
    assertEquals(ColdpTerm.RESOURCES.size(), dp.getResources().size());

    dp.getResources().forEach(r -> {
      //System.out.println(r.getName());
      assertNotNull(r.getName());
      assertNotNull(r.getName(), r.getDescription());
      if (r instanceof TreatmentResource) {
        assertNotNull(r.getDescription());

      } else {
        r.getSchema().getFields().forEach(f -> {
          assertNotNull("Missing field name for " + f, f.getName());
          assertNotNull("Missing type for field " + f.getName(), f.getType());
          assertNotNull("Missing description for " + f.getName(), f.getDescription());
        });
      }
    });
  }
}