package life.catalogue.dao;

import life.catalogue.db.TestDataRule;
import org.gbif.utils.file.FileUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedReader;
import java.util.Iterator;
import java.util.stream.Stream;

import static org.junit.Assert.assertFalse;

public abstract class FileMetricsDaoTestBase<K> extends DaoTestBase {

  FileMetricsDao<K> dao;
  
  public FileMetricsDaoTestBase() {
    super(TestDataRule.tree());
  }
  
  K key;

  @Test
  public void roundtripTree() throws Exception {
    BufferedReader expected = FileUtils.getInputStreamReader(FileUtils.classpathStream("trees/tree.tree"), "UTF8");

    dao.updateTree(key, key, 1);
  
    Stream<String> lines = dao.getTree( key, 1);
    assertEquals(expected.lines(), lines);
  }

  @Test(expected = FileMetricsDao.AttemptMissingException.class)
  public void missingFile() throws Exception {
    dao.getTree(key, 77);
  }

  @Test
  public void roundtripNames() throws Exception {
    dao.updateNames(key, key,1);

    Stream<String> lines = dao.getNames(key, 1);
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
