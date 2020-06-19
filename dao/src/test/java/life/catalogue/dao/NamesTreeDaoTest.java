package life.catalogue.dao;

import life.catalogue.db.TestDataRule;
import org.gbif.utils.file.FileUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.util.Iterator;
import java.util.stream.Stream;

import static org.junit.Assert.assertFalse;

public class NamesTreeDaoTest extends DaoTestBase {
  
  DatasetImportDao dao;
  
  public NamesTreeDaoTest() {
    super(TestDataRule.tree());
  }
  
  @Before
  public void initDao(){
    dao = new DatasetImportDao(factory(), treeRepoRule.getRepo());
  }
  
  @Test
  public void roundtripTree() throws Exception {
    BufferedReader expected = FileUtils.getInputStreamReader(FileUtils.classpathStream("trees/tree.tree"), "UTF8");
    
    dao.getTreeDao().updateDatasetTree(11, 1);
  
    Stream<String> lines = dao.getTreeDao().getTree(NamesTreeDao.Context.DATASET, 11, 1);
    assertEquals(expected.lines(), lines);
  }

  @Test(expected = NamesTreeDao.AttemptMissingException.class)
  public void missingFile() throws Exception {
    dao.getTreeDao().getTree(NamesTreeDao.Context.DATASET, 11, 77);
  }

  @Test
  public void roundtripNames() throws Exception {
    dao.getTreeDao().updateDatasetNames(11, 1);

    Stream<String> lines = dao.getTreeDao().getNames(NamesTreeDao.Context.DATASET, 11, 1);
    //lines.forEach(System.out::println);
    assertEquals(("Alopsis\n" +
      "Animalia\n" +
      "Canidae\n" +
      "Canis\n" +
      "Canis adustus\n" +
      "Canis argentinus\n" +
      "Canis aureus\n" +
      "Carnivora\n" +
      "Chordata\n" +
      "Felidae\n" +
      "Felis rufus\n" +
      "Lupulus\n" +
      "Lynx\n" +
      "Lynx lynx\n" +
      "Lynx rufus\n" +
      "Lynx rufus baileyi\n" +
      "Lynx rufus gigas\n" +
      "Mammalia\n" +
      "Pardina\n" +
      "Urocyon\n" +
      "Urocyon citrinus\n" +
      "Urocyon littoralis\n" +
      "Urocyon minicephalus\n" +
      "Urocyon webbi").lines(), lines);

    lines = dao.getTreeDao().getNameIds(NamesTreeDao.Context.DATASET, 11, 1);
    // we have only NULL index ids in this test dataset :)
    assertFalse(lines.findFirst().isPresent());
  }
  
  @Test
  public void bucket() throws Exception {
    Assert.assertEquals("000", NamesTreeDao.bucket(0));
    Assert.assertEquals("003", NamesTreeDao.bucket(3));
    Assert.assertEquals("013", NamesTreeDao.bucket(13));
    Assert.assertEquals("133", NamesTreeDao.bucket(133));
    Assert.assertEquals("999", NamesTreeDao.bucket(999));
    Assert.assertEquals("000", NamesTreeDao.bucket(1000));
    Assert.assertEquals("333", NamesTreeDao.bucket(1333));
    Assert.assertEquals("001", NamesTreeDao.bucket(1789001));
    Assert.assertEquals("456", NamesTreeDao.bucket(-3456));
  }
  
  public static <T> void assertEquals(Stream<T> expected, Stream<T> toTest) {
    Iterator<T> iter = expected.iterator();
    toTest.forEach(x -> {
      if (!iter.hasNext()) {
        System.out.println("Unexpected extra content:");
        System.out.println(x);
      }
      Assert.assertEquals(iter.next(), x);
    });
    assertFalse(iter.hasNext());
  }
}
