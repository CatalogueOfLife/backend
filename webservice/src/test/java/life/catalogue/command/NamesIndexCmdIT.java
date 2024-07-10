package life.catalogue.command;

import life.catalogue.api.model.SimpleName;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.db.PgUtils;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.db.SqlSessionFactoryWithPath;
import life.catalogue.junit.TestDataRule;
import life.catalogue.db.mapper.NamesIndexMapper;
import life.catalogue.junit.TxtTreeDataRule;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSession;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 *
 */
public class NamesIndexCmdIT extends CmdTestBase {

  public NamesIndexCmdIT() {
    super(NamesIndexCmd::new, TestDataRule.apple());
  }

  @Before
  public void init() {
    // file location see config-test.yaml scratch dir
    var tmp = new File("/tmp/colplus/scratch/nidx-build");
    System.out.println("Clear working directory " + tmp.getAbsolutePath());
    FileUtils.deleteQuietly(tmp);
  }
  @Test
  public void testRebuild() throws Exception {
    assertTrue(run("nidx", "--prompt", "0").isEmpty());
  }

  @Test
  public void testRebuildDupes() throws Throwable {
    // load some datasets that have overlapping names
    List<TxtTreeDataRule.TreeDataset> data = new ArrayList<>();
    for (int x=0; x<10; x++) {
      data.add(
        new TxtTreeDataRule.TreeDataset(100+x, "txtree/nidx-rebuild/"+x+".txtree", "Dataset "+x, DatasetOrigin.EXTERNAL)
      );
    }
    for (int x=0; x<6; x++) {
      data.add(
        new TxtTreeDataRule.TreeDataset(110+x, "txtree/nidx-rebuild/dupe.txtree", "Dataset dupe "+x, DatasetOrigin.EXTERNAL)
      );
    }
    System.out.println("Insert "+data.size()+" datasets into postgres");
    try (TxtTreeDataRule treeRule = new TxtTreeDataRule(data)) {
      treeRule.before();
    }

    System.out.println("RUN REBUILD COMMAND");
    assertTrue(run("nidx", "--prompt", "0").isEmpty());

    System.out.println("VERIFY OUTCOME");
    // use a factory that changes the default pg search_path to "nidx" so we use the freshly build data and dont need to copy it over first
    var nidxFactory = new SqlSessionFactoryWithPath(SqlSessionFactoryRule.getSqlSessionFactory(), NamesIndexCmd.BUILD_SCHEMA);

    try (SqlSession session = nidxFactory.openSession()) {
      var nim = session.getMapper(NamesIndexMapper.class);

      var cnt = nim.count();
      System.out.println("\nNames Index with "+cnt+" entries from postgres:");
      nim.processAll().forEach(System.out::println);

      var names = new HashSet<>();
      PgUtils.consume(nim::processAll, n -> {
        var sn = new SimpleName(null, n.getScientificName(), n.getAuthorship(), n.getRank());
        if (!names.add(sn)) {
          throw new IllegalStateException("Non unique name "+sn+" in names index");
        }
      });
      assertEquals(286, cnt);
    }
  }
}