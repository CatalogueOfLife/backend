package life.catalogue.command;

import life.catalogue.api.model.SimpleName;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.db.PgUtils;
import life.catalogue.db.SqlSessionFactoryWithPath;
import life.catalogue.db.mapper.NamesIndexMapper;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;
import life.catalogue.junit.TxtTreeDataRule;

import org.gbif.nameparser.api.Rank;

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
        // the names index is single-tier & canonical-only: every entry is UNRANKED with no authorship
        var sn = new SimpleName(null, n.getScientificName(), null, Rank.UNRANKED);
        if (!names.add(sn)) {
          throw new IllegalStateException("Non unique name "+sn+" in names index");
        }
      });
      // total distinct names_index rows these fixtures produce under the current name parser.
      // Historically (two-tier index: 1 canonical row + 1 row per distinct authorship/rank
      // combination sharing that canonical) this fixture produced 281 rows (284 under an older
      // parser version). names_index is now a single-tier, canonical-only registry -- one row
      // per distinct normalized canonical name; authorship and rank are never stored there at
      // all -- so every one of those old authorship/rank "child" rows disappeared and only the
      // canonical buckets remain. dupe.txtree alone (loaded into 6 datasets here to stress the
      // dedup path) packs e.g. 8 "Acacia" genus entries under different authors and ~10
      // "Poecile montan-" spelling/authorship/rank variants, each formerly its own two-tier row,
      // now collapsing to exactly one canonical row apiece. 132 is the resulting distinct-
      // canonical count; expect this to shift again if the parser's canonicalization changes.
      assertEquals(132, cnt);
    }
  }
}