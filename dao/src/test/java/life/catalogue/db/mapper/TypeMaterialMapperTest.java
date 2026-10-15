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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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

  /** the batch form the archive export of a subtree uses, see NameBatchable */
  @Test
  public void listByNames() {
    final var name = TestEntityGenerator.NAME1;
    for (int i = 1; i < 4; i++) {
      mapper().create(createTestEntity(name.getDatasetKey()));
    }
    commit();

    final int dk = name.getDatasetKey();
    assertEquals(mapper().listByName(name).size(), mapper().listByNames(dk, List.of(name.getId())).size());
    assertTrue(mapper().listByNames(dk, List.of("no-such-name")).isEmpty());
    assertEquals(mapper().listByName(name).size(), mapper().listByNames(dk, List.of("nope", name.getId())).size());
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
    obj.getNames().get(0).setStatus(TypeStatus.NEOTYPE);
    obj.addName(TestEntityGenerator.NAME2.getId(), TypeStatus.ISOTYPE);
    obj.setCitation("harr harr harr. No citation at all. GER1234");
  }

  private TypeMaterial createMultiName(String id) {
    TypeMaterial tm = TestEntityGenerator.newType(datasetKey, id, TestEntityGenerator.NAME1.getId());
    tm.addName(TestEntityGenerator.NAME2.getId(), TypeStatus.LECTOTYPE);
    return tm;
  }

  @Test
  public void multipleNames() throws Exception {
    TypeMaterial tm = createMultiName("multi");
    mapper().create(tm);
    commit();

    TypeMaterial tm2 = removeDbCreatedProps(mapper().get(tm.getKey()));
    removeDbCreatedProps(tm);
    assertEquals(tm, tm2);
    assertEquals(2, tm2.getNames().size());

    // one specimen listed for both names, the archive batch lists it once
    assertEquals(List.of(tm2), mapper().listByName(TestEntityGenerator.NAME1).stream().map(this::removeDbCreatedProps).toList());
    assertEquals(List.of(tm2), mapper().listByName(TestEntityGenerator.NAME2).stream().map(this::removeDbCreatedProps).toList());
    assertEquals(1, mapper().listByNames(datasetKey, List.of(TestEntityGenerator.NAME1.getId(), TestEntityGenerator.NAME2.getId())).size());

    // designations carry the status for their name
    var d1 = mapper().listDesignations(TestEntityGenerator.NAME1);
    assertEquals(1, d1.size());
    assertEquals(TypeStatus.HOLOTYPE, d1.get(0).getStatus());
    assertEquals(TestEntityGenerator.NAME1.getId(), d1.get(0).getNameId());
    var d2 = mapper().listDesignations(TestEntityGenerator.NAME2);
    assertEquals(TypeStatus.LECTOTYPE, d2.get(0).getStatus());
    assertEquals("multi", d2.get(0).getTypeMaterial().getId());

    // deleting one name keeps the specimen for the other
    mapper().deleteByName(TestEntityGenerator.NAME1);
    commit();
    tm2 = mapper().get(tm.getKey());
    assertEquals(1, tm2.getNames().size());
    assertEquals(TestEntityGenerator.NAME2.getId(), tm2.getNames().get(0).getNameId());
    assertTrue(mapper().listByName(TestEntityGenerator.NAME1).isEmpty());

    // deleting the last name removes the specimen
    mapper().deleteByName(TestEntityGenerator.NAME2);
    commit();
    assertNull(mapper().get(tm.getKey()));
  }

  @Test
  public void deleteOrphans() throws Exception {
    TypeMaterial tm = createMultiName("orphan");
    mapper().create(tm);
    TypeMaterial tm2 = TestEntityGenerator.newType(datasetKey, "orphan2", TestEntityGenerator.NAME2.getId());
    mapper().create(tm2);
    commit();
    // all names exist
    assertEquals(0, mapper().deleteOrphans(datasetKey, null));

    // a specimen without names is an orphan
    mapper().deleteNames(tm2);
    commit();
    assertEquals(1, mapper().deleteOrphans(datasetKey, null));
    commit();
    assertNull(mapper().get(tm2.getKey()));
    assertEquals(2, mapper().get(tm.getKey()).getNames().size());
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