package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.TypeMaterial;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.TypeStatus;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TypeMaterialMapperTest extends CRUDEntityTestBase<DSID<String>, TypeMaterial, TypeMaterialMapper> {

  public TypeMaterialMapperTest() {
    super(TypeMaterialMapper.class);
  }

  @Override
  TypeMaterial createTestEntity(int dkey) {
    return TestEntityGenerator.newType(dkey, TestEntityGenerator.NAME1.getId());
  }

  @Override
  TypeMaterial removeDbCreatedProps(TypeMaterial obj) {
    return TestEntityGenerator.nullifyUserDate(obj);
  }


  @Test
  public void copyDataset() throws Exception {
    CopyDatasetTestComponent.copy(mapper(), datasetKey, true);
  }

  @Test
  public void sectorProcessable() throws Exception {
    SectorProcessableTestComponent.test(mapper(), DSID.of(Datasets.COL, 1));
  }

  @Test
  public void roundtrip2() throws Exception {
    TypeMaterial u1 = createTestEntity(datasetKey);
    mapper().create(u1);
    commit();

    removeDbCreatedProps(u1);
    TypeMaterial u2 = removeDbCreatedProps(mapper().get(u1.getKey()));
    //printDiff(u1, u2);
    assertEquals(u1, u2);
  }

  @Override
  void updateTestObj(TypeMaterial obj) {
    obj.setStatus(TypeStatus.NEOTYPE);
    obj.setCitation("harr harr harr. No citation at all. GER1234");
  }

  @Test
  public void listByName() {
    List<TypeMaterial> types = new ArrayList<>();
    for (int i = 1; i < 5; i++) {
      TypeMaterial tm = createTestEntity(TestEntityGenerator.NAME1.getDatasetKey());
      tm.setRemarks("Type number " + i);
      mapper().create(tm);
      types.add(tm);
    }

    List<TypeMaterial> types2 = mapper().listByName(TestEntityGenerator.NAME1);
    TestEntityGenerator.nullifyDate(types2);
    //printDiff(types, types2);
    assertEquals(types, types2);

    // empty list, not null
    assertEquals(0, mapper().listByName(DSID.of(datasetKey, "NoT-REALL")).size());
  }
}