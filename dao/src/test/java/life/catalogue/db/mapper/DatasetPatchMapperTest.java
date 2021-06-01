package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetMetadata;
import life.catalogue.api.model.Page;
import life.catalogue.api.vocab.Datasets;
import org.junit.Test;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;

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
  public void roundtripCrud() throws Exception {
    Dataset u1 = readFirst();
    mapper().create(Datasets.COL, u1);
    commit();

    removeDbCreatedProps(u1);
    Dataset u2 = removeDbCreatedProps(mapper().get(Datasets.COL, u1.getKey()));
    printDiff(u1, u2);
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

    printDiff(u1, u2);
    assertEquals(removeDbCreatedProps(u1), u2);


    mapper().delete(Datasets.COL, u1.getKey());
    commit();

    assertNull(mapper().get(Datasets.COL, u1.getKey()));
  }


  Dataset readFirst() throws Exception {
    DatasetMapper dm = mapper(DatasetMapper.class);
    Dataset d = new Dataset(dm.list(new Page(0,1)).get(0));

    // clear all properties that do NOT exist in the DatasetMetadata interface
    BeanInfo metaInfo = Introspector.getBeanInfo(DatasetMetadata.class);
    for(PropertyDescriptor prop : Introspector.getBeanInfo(Dataset.class, Object.class).getPropertyDescriptors()){
      if (prop.getWriteMethod() == null) continue;

      boolean exists = false;
      for(PropertyDescriptor iProp : metaInfo.getPropertyDescriptors()){
        if (prop.getReadMethod() != null && iProp.getReadMethod().getName().equalsIgnoreCase(prop.getReadMethod().getName())) {
          exists = true;
          break;
        }
      }
      if (!exists) {
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
    TestEntityGenerator.nullifyUserDate(obj);
    return obj;
  }

}