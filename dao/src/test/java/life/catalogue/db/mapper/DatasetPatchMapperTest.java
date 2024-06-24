package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetTest;
import life.catalogue.api.vocab.Datasets;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.util.ArrayList;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class DatasetPatchMapperTest extends MapperTestBase<DatasetPatchMapper> {

  public DatasetPatchMapperTest() {
    super(DatasetPatchMapper.class);
  }

  @Test
  public void deleteByDataset() throws Exception {
    mapper().deleteByDataset(Datasets.COL);
  }
  @Test
  public void roundTripNullPatch() throws Exception {
    var d1 = DatasetTest.createNullPatchDataset(TestEntityGenerator.DATASET11.getKey());
    // ignore the source property which we dont even store in the db!
    d1.setSource(null);

    TestEntityGenerator.setUserDate(d1);
    mapper().create(Datasets.COL, d1);
    commit();

    removeDbCreatedProps(d1);
    Dataset d2 = removeDbCreatedProps(mapper().get(Datasets.COL, d1.getKey()));
    // if the equal fails on container properties make sure these properties are listed in Dataset.PATCH_PROPS
    assertEquals(d1, d2);
  }

  @Test
  public void roundtripCrud() throws Exception {
    Dataset u1 = removeNonPatchProps(DatasetMapperTest.populate(new Dataset()));
    u1.setPrivat(true); // we dont store private flag in patches and it defaults to true
    // source key must be an existing dataset
    u1.setKey(TestEntityGenerator.DATASET11.getKey());
    TestEntityGenerator.setUserDate(u1);
    mapper().create(Datasets.COL, u1);
    commit();

    removeDbCreatedProps(u1);
    Dataset u2 = removeDbCreatedProps(mapper().get(Datasets.COL, u1.getKey()));
    assertEquals(u1, u2);

    // test update
    u1.setTitle("Some other title");
    // try bad values cause we should not have constraints on patches
    u1.setConfidence(1946732);
    u1.setOrigin(null);
    mapper().update(Datasets.COL, u1);
    commit();

    removeDbCreatedProps(u1);
    u2 = removeDbCreatedProps(mapper().get(Datasets.COL, u1.getKey()));

    assertEquals(removeDbCreatedProps(u1), u2);

    mapper().delete(Datasets.COL, u1.getKey());
    commit();

    assertNull(mapper().get(Datasets.COL, u1.getKey()));
  }


  Dataset removeNonPatchProps(Dataset d) throws Exception {
    // clear all properties that can NOT be patched
    for(PropertyDescriptor prop : Introspector.getBeanInfo(Dataset.class, Object.class).getPropertyDescriptors()){
      if (prop.getWriteMethod() == null) continue;

      if (!Dataset.PATCH_PROPS.contains(prop)) {
        if (prop.getWriteMethod().getParameterTypes().length > 1) continue;

        // we only have boolean as primitive types on dataset
        if (prop.getWriteMethod().getParameterTypes()[0].isPrimitive()) {
          System.out.println("Set false: " + prop.getWriteMethod());
          prop.getWriteMethod().invoke(d, false);
        } else {
          System.out.println("Set NULL: " + prop.getWriteMethod());
          prop.getWriteMethod().invoke(d, (Object) null);
        }
      }
    }
    return d;
  }

  Dataset removeDbCreatedProps(Dataset obj) {
    obj.setCreated(null);
    obj.setModified(null);
    if (obj.getSource() == null) {
      obj.setSource(new ArrayList<>());
    }
    return obj;
  }

}