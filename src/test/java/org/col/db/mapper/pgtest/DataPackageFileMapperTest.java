package org.gbif.datarepo.persistence.mappers;

import org.gbif.api.model.common.DOI;
import org.gbif.datarepo.api.model.AlternativeIdentifier;
import org.gbif.datarepo.api.model.DataPackage;
import org.gbif.datarepo.api.model.DataPackageFile;
import org.gbif.datarepo.api.model.Tag;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.google.common.hash.Hashing;
import com.google.inject.Injector;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Integration tests of DataPackageMapper.
 */
public class DataPackageFileMapperTest  extends BaseMapperTest {

  //Guice injector used to instantiate Mappers.
  private static Injector injector;

  private static final String ALTERNATIVE_ID_TEST = UUID.randomUUID().toString();

  /**
   * Initializes the MyBatis module.
   */
  @BeforeClass
  public static void init() {
    injector = buildInjector();
  }

  /**
   * Creates a new instance of a DataPackage.
   */
  private static DataPackage testDataPackage() {
    DataPackage dataPackage = new DataPackage();
    AlternativeIdentifier alternativeIdentifier = new AlternativeIdentifier();
    alternativeIdentifier.setCreated(new Date());
    alternativeIdentifier.setCreatedBy("testUser");
    alternativeIdentifier.setType(AlternativeIdentifier.Type.UUID);
    alternativeIdentifier.setIdentifier(ALTERNATIVE_ID_TEST);
    Tag tag = new Tag();
    tag.setCreated(new Date());
    tag.setCreatedBy("testUser");
    tag.setValue("DataOne");
    String testChecksum = Hashing.md5().newHasher(32).hash().toString();
    dataPackage.setDoi(new DOI(DOI.TEST_PREFIX, Long.toString(new Date().getTime())));
    dataPackage.addAlternativeIdentifier(alternativeIdentifier);
    dataPackage.addTag(tag);
    dataPackage.setChecksum(testChecksum);
    dataPackage.setCreated(new Date());
    dataPackage.setCreatedBy("testUser");
    dataPackage.setDescription("test");
    dataPackage.setMetadata("metadata.xml");
    dataPackage.setSize(1);
    dataPackage.setTitle("test");
    dataPackage.addFile(new DataPackageFile("test.xml", testChecksum, 1));
    return dataPackage;
  }

  private void insertDataPackage(DataPackage dataPackage) {
    DataPackageMapper dataPackageMapper = injector.getInstance(DataPackageMapper.class);
    dataPackageMapper.create(dataPackage);

    AlternativeIdentifierMapper alternativeIdentifierMapper = injector.getInstance(AlternativeIdentifierMapper.class);
    dataPackage.getAlternativeIdentifiers().forEach( alternativeIdentifierMapper::create);

    TagMapper tagMapper = injector.getInstance(TagMapper.class);
    dataPackage.getTags().forEach(tagMapper::create);
  }

  /**
   * Tests methods create and get.
   */
  @Test
  public void testCreate() {
    DataPackage dataPackage = testDataPackage();
    insertDataPackage(dataPackage);
    DataPackageMapper mapper = injector.getInstance(DataPackageMapper.class);

    DataPackage justCreated = mapper.get(dataPackage.getDoi().getDoiName());
    Assert.assertEquals(justCreated.getDoi(), dataPackage.getDoi());
  }

  /**
   * Tests methods create and get.
   */
  @Test
  public void testGetByAlternativeIdentifier() {
    DataPackageMapper mapper = injector.getInstance(DataPackageMapper.class);
    DataPackage dataPackage = testDataPackage();
    insertDataPackage(dataPackage);
    DataPackage justCreated = mapper.getByAlternativeIdentifier(ALTERNATIVE_ID_TEST);
    Assert.assertEquals(ALTERNATIVE_ID_TEST, justCreated.getAlternativeIdentifiers().get(0).getIdentifier());
  }

  /**
   * Tests methods create and delete.
   */
  @Test
  public void testDelete() {
    DataPackageMapper mapper = injector.getInstance(DataPackageMapper.class);
    DataPackage dataPackage = testDataPackage();
    insertDataPackage(dataPackage);
    mapper.delete(dataPackage.getDoi());
  }

  /**
   * Tests methods create and list.
   */
  @Test
  public void testList() {
    DataPackageMapper mapper = injector.getInstance(DataPackageMapper.class);
    DataPackage dataPackage = testDataPackage();
    mapper.create(dataPackage);
    List<DataPackage> dataPackages = mapper.list("testUser", null, null, null, false, null);
    Assert.assertTrue(dataPackages.size() >=  1);
  }

  /**
   * Tests methods create and list.
   */
  @Test
  public void testListByTags() {
    DataPackageMapper mapper = injector.getInstance(DataPackageMapper.class);
    DataPackage dataPackage = testDataPackage();
    insertDataPackage(dataPackage);
    List<DataPackage> dataPackages = mapper.list(null, null, null, null, null,
                                                 dataPackage.getTags().stream()
                                                   .map(Tag::getValue).collect(Collectors.toList()));
    Assert.assertTrue(dataPackages.size() >=  1);
  }


  /**
   * Tests methods create and list.
   */
  @Test
  public void testListByNonExistingTags() {
    DataPackageMapper mapper = injector.getInstance(DataPackageMapper.class);
    DataPackage dataPackage = testDataPackage();
    insertDataPackage(dataPackage);
    List<DataPackage> dataPackages = mapper.list(null, null, null, null, null, Collections.singletonList("NoATag"));
    Assert.assertTrue(dataPackages.size() >=  0);
  }

  /**
   * Tests methods create and count.
   */
  @Test
  public void testCount() {
    DataPackageMapper mapper = injector.getInstance(DataPackageMapper.class);
    DataPackage dataPackage = testDataPackage();
    mapper.create(dataPackage);
    Assert.assertTrue(mapper.count("testUser", null, null, null, false, null) >= 1);
  }


  /**
   * Tests methods create and update.
   */
  @Test
  public void testUpdate() {
    DataPackageMapper mapper = injector.getInstance(DataPackageMapper.class);
    DataPackage dataPackage = testDataPackage();
    mapper.create(dataPackage);
    dataPackage.setTitle("newTitle");
    mapper.update(dataPackage);
    Assert.assertEquals("newTitle", mapper.get(dataPackage.getDoi().getDoiName()).getTitle());
  }
}
