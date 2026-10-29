package life.catalogue.common.datapackage;

import life.catalogue.api.datapackage.PackageDescriptor;
import life.catalogue.coldp.ColdpTerm;

import life.catalogue.common.io.Resources;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;

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

  @Test
  public void typesAndEnums() throws IOException {
    var dp = new DataPackageBuilder()
        .docs()
        .build(new PackageDescriptor());

    assertEquals(Field.TYPE_STRING, field(dp, "reference", "issued").getType());
    assertEquals(Field.TYPE_STRING, field(dp, "taxon", "scrutinizerDate").getType());
    assertEquals(Field.TYPE_DATETIME, field(dp, "name", "modified").getType());
    // spec only recommends a vocabulary for rank and author/type material countries
    assertFalse(field(dp, "name", "rank").getConstraints().containsKey(Field.CONSTRAINT_KEY_ENUM));
    assertFalse(field(dp, "author", "country").getConstraints().containsKey(Field.CONSTRAINT_KEY_ENUM));
    assertFalse(field(dp, "typematerial", "country").getConstraints().containsKey(Field.CONSTRAINT_KEY_ENUM));
    var countries = (List<String>) field(dp, "vernacularname", "country").getConstraints().get(Field.CONSTRAINT_KEY_ENUM);
    assertTrue(countries.contains("DE"));
    assertEquals(List.of("masculine", "feminine", "neuter"), field(dp, "nameusage", "gender").getConstraints().get(Field.CONSTRAINT_KEY_ENUM));
    assertTrue(((List<String>) field(dp, "distribution", "season").getConstraints().get(Field.CONSTRAINT_KEY_ENUM)).contains("wet"));
  }

  private static Field field(DataPackage dp, String resource, String name) {
    return dp.getResources().stream()
        .filter(r -> r.getName().equals(resource))
        .findFirst().orElseThrow()
        .getSchema().getFields().stream()
        .filter(f -> f.getName().equals(name))
        .findFirst().orElseThrow();
  }
}