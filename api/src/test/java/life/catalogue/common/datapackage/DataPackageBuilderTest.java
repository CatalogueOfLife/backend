package life.catalogue.common.datapackage;

import life.catalogue.api.datapackage.PackageDescriptor;

import life.catalogue.coldp.ColdpTerm;

import org.junit.Test;

import static org.junit.Assert.*;

public class DataPackageBuilderTest {

  @Test
  public void build() {
    var dp = new DataPackageBuilder().build(new PackageDescriptor());
    assertEquals(ColdpTerm.RESOURCES.size(), dp.getResources().size());
  }
}