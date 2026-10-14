package life.catalogue.matching;

import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.model.IssueContainer;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.Taxon;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.interpreter.NameInterpreter;
import life.catalogue.matching.nidx.NameIndex;
import life.catalogue.matching.nidx.NameIndexFactory;
import life.catalogue.matching.nidx.NamesIndexConfig;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * External match queries must never insert into the names index, which is only built from names held in ChecklistBank.
 */
public class MatchingInterpretTest {
  NameIndex ni;
  MatchingUtils utils;
  final NameInterpreter interpreter = new NameInterpreter(new DatasetSettings(), true);

  @Before
  public void init() throws Exception {
    ni = NameIndexFactory.build(NamesIndexConfig.memory(512), null, AuthorshipNormalizer.createWithoutAuthormap()).started();
    utils = new MatchingUtils(ni);
  }

  @After
  public void close() throws Exception {
    ni.close();
  }

  @Test
  public void interpretNeverInserts() {
    var snc = AbstractMatchingJob.interpret(SimpleName.sn("Abies alba Mill."), List.of(), new IssueContainer.Simple(), interpreter, utils);
    assertTrue(snc.isPresent());
    assertNull(snc.get().getNamesIndexId());
    assertEquals(0, ni.size());
  }

  @Test
  public void internalNamesInsert() {
    var nu = new Taxon(interpreter.interpret(SimpleName.sn("Abies alba Mill."), new IssueContainer.Simple()).get().getName());
    var snc = utils.toSimpleNameClassified(nu, List.of());
    assertNotNull(snc.getNamesIndexId());
    assertEquals(1, ni.size());

    // an external query now finds the existing entry without adding another
    var q = AbstractMatchingJob.interpret(SimpleName.sn("Abies alba"), List.of(), new IssueContainer.Simple(), interpreter, utils);
    assertEquals(snc.getNamesIndexId(), q.get().getNamesIndexId());
    assertEquals(1, ni.size());
  }
}
