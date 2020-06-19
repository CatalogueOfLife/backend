package life.catalogue.db.tree;

import life.catalogue.common.io.Resources;
import life.catalogue.dao.NamesTreeDao;
import org.apache.commons.io.IOUtils;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertTrue;

public class BaseDiffServiceTest {

  static Diff diff;

  static class Diff extends BaseDiffService {

    public Diff(NamesTreeDao dao, SqlSessionFactory factory) {
      super(NamesTreeDao.Context.DATASET, dao, factory);
    }

    @Override
    int[] parseAttempts(int key, String attempts) {
      return new int[0];
    }
  }

  @BeforeClass
  public static void init() {
    diff = new Diff(null, null);
  }

  @Test
  public void diffBinaryVersion() throws IOException {
    String v = diff.diffBinaryVersion();
    System.out.println(v);
    assertTrue(v.startsWith("diff"));
    assertTrue(v.length() > 10);
  }

  @Test
  public void udiff() throws Exception {
    final File f1 = Resources.toFile("trees/coldp.tree.gz");
    final File f2 = Resources.toFile("trees/coldp2.tree.gz");

    BufferedReader br = diff.udiff(1, new int[]{1,2}, i -> {
      switch (i) {
        case 1: return f1;
        case 2: return f2;
      }
      return null;
    });


    String version = IOUtils.toString(br);
    System.out.println(version);

    Assert.assertTrue(version.startsWith("---"));
  }

  @Test(expected = NamesTreeDao.AttemptMissingException.class)
  public void namesDiff() throws Exception {
    final File bad = new File("/tmp/I do not exist");
    diff.namesDiff(1, new int[]{1,2,3}, i -> {
      switch (i) {
        case 1: return bad;
        case 2: return bad;
      }
      return null;
    });
  }
}