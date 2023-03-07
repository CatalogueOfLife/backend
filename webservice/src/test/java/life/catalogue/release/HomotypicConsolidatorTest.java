package life.catalogue.release;

import life.catalogue.TestDataGenerator;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.TreeTraversalParameter;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.Issue;
import life.catalogue.assembly.SectorSyncIT;
import life.catalogue.basgroup.HomotypicConsolidator;
import life.catalogue.common.id.IdConverter;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.db.NameMatchingRule;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.SqlSessionFactoryRule;
import life.catalogue.db.TestDataRule;

import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.VerbatimSourceMapper;
import life.catalogue.db.tree.PrinterFactory;
import life.catalogue.db.tree.TextTreePrinter;
import life.catalogue.importer.neo.printer.GraphFormat;
import life.catalogue.importer.neo.printer.PrinterUtils;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import org.apache.poi.ss.formula.functions.T;

import org.gbif.nameparser.api.Rank;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.Assert.*;

public class HomotypicConsolidatorTest {

  @ClassRule
  public final static PgSetupRule pg = new PgSetupRule();

  final NameMatchingRule matchingRule = new NameMatchingRule();
  final TestDataRule dataRule = TestDataGenerator.homotypigGrouping();

  @Rule
  public final TestRule chain = RuleChain
    .outerRule(dataRule)
    .around(matchingRule);

  @Test
  public void consolidateNoPrios() throws IOException {
    var hc = HomotypicConsolidator.forAllFamilies(SqlSessionFactoryRule.getSqlSessionFactory(), dataRule.testData.key);
    hc.consolidate();
    printTree();
    assertTree("hg1.txt");

    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession()) {
      var vm = session.getMapper(VerbatimSourceMapper.class);
      consumeTree(u -> {
        if (u.getRank().isSpeciesOrBelow()) {
          var v = vm.getIssues(DSID.of(dataRule.testData.key, u.getId()));
          assertTrue(v.getIssues().contains(Issue.HOMOTYPIC_MULTI_ACCEPTED));
        }
      });
    }
  }

  @Test
  public void groupFamily() {
    var hc = HomotypicConsolidator.forFamilies(SqlSessionFactoryRule.getSqlSessionFactory(), dataRule.testData.key,
      List.of(SimpleName.sn("x5", Rank.FAMILY, "Chironomidae", "")),
      u -> {
        return Integer.parseInt(u.getId());
      });
    hc.consolidate();
    printTree();
  }


  void printTree() {
    Writer writer = new StringWriter();
    PrinterFactory.dataset(TextTreePrinter.class, dataRule.testData.key, SqlSessionFactoryRule.getSqlSessionFactory(), writer);
    System.out.println(writer.toString().trim());
  }

  void consumeTree(Consumer<SimpleName> handler) {
    TreeTraversalParameter params = TreeTraversalParameter.dataset(dataRule.testData.key);
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession();
         var cursor = session.getMapper(NameUsageMapper.class).processTreeSimple(params)) {
      cursor.forEach(handler);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  void assertTree(String filename) throws IOException {
    SectorSyncIT.assertTree(dataRule.testData.key, openResourceStream(filename));
  }

  InputStream openResourceStream(String filename) {
    return getClass().getResourceAsStream("/grouping-trees/" + filename);
  }
}