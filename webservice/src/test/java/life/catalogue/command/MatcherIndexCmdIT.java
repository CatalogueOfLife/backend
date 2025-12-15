package life.catalogue.command;

import life.catalogue.api.model.SimpleName;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.db.PgUtils;
import life.catalogue.db.SqlSessionFactoryWithPath;
import life.catalogue.db.mapper.NamesIndexMapper;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;
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
public class MatcherIndexCmdIT extends CmdTestBase {

  public MatcherIndexCmdIT() {
    super(MatcherIndexCmd::new, TestDataRule.apple(), "config-matcher-test.yaml", false);
  }

  @Before
  public void init() {
  }

  @Test
  public void testBuild() throws Exception {
    assertTrue(run("matcher", "--key", Integer.toString(TestDataRule.APPLE.key)).isEmpty());
  }

}