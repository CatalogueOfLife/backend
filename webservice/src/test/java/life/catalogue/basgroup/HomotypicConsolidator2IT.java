package life.catalogue.basgroup;

import life.catalogue.TestDataGenerator;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.TreeTraversalParameter;
import life.catalogue.api.vocab.Issue;
import life.catalogue.assembly.SectorSyncIT;
import life.catalogue.db.NameMatchingRule;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.SqlSessionFactoryRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.VerbatimSourceMapper;
import life.catalogue.printer.PrinterFactory;
import life.catalogue.printer.TextTreePrinter;

import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.apache.ibatis.session.SqlSession;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import static org.junit.Assert.assertTrue;

public class HomotypicConsolidator2IT {

  @ClassRule
  public final static PgSetupRule pg = new PgSetupRule();

  final NameMatchingRule matchingRule = new NameMatchingRule();
  final TestDataRule dataRule = TestDataGenerator.homotypigGrouping();

  @Rule
  public final TestRule chain = RuleChain
    .outerRule(dataRule)
    .around(matchingRule);

  @Test
  public void synSyns() throws IOException {
    var hc = HomotypicConsolidator.forTaxa(SqlSessionFactoryRule.getSqlSessionFactory(), dataRule.testData.key,
      List.of(SimpleName.sn("pott", Rank.FAMILY, "Pottiaceae", "Hampe")),
      u -> Integer.parseInt(u.getId())
    );
    printTree();
    hc.consolidate();
    assertTree("hg-pott.txt", "pott");
  }

  @Test
  public void consolidateNoPrios() throws IOException {
    printTree();
    var hc = HomotypicConsolidator.entireDataset(SqlSessionFactoryRule.getSqlSessionFactory(), dataRule.testData.key);
    hc.consolidate();
    assertTree("hg1.txt", null);

    Set<String> skipped = Set.of("1","2","3","4");
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession()) {
      var vm = session.getMapper(VerbatimSourceMapper.class);
      final DSID<String> key = DSID.root(dataRule.testData.key);
      for (var id : skipped) {
        var v = vm.get(key.id(id));
        assertTrue(v.getIssues().contains(Issue.HOMOTYPIC_CONSOLIDATION_UNRESOLVED));
      }
    }
  }

  @Test
  public void subspeciesSynonyms() throws IOException {
    var hc = HomotypicConsolidator.forTaxa(SqlSessionFactoryRule.getSqlSessionFactory(), dataRule.testData.key,
      List.of(SimpleName.sn("mfo", Rank.FAMILY, "Procyonidae", "")),
      u -> Integer.parseInt(u.getId())
    );
    printTree();
    hc.consolidate();
    assertTree("hg-mfo.txt", "mfo");
  }

  @Test
  public void groupFamily() throws IOException {
    var hc = HomotypicConsolidator.forTaxa(SqlSessionFactoryRule.getSqlSessionFactory(), dataRule.testData.key,
      List.of(SimpleName.sn("dfc", Rank.FAMILY, "Chironomidae", "")),
      u -> Integer.parseInt(u.getId())
    );
    printTree();
    hc.consolidate();
    assertTree("hg-dfc.txt", "dfc");
  }

  @Test
  public void groupTribe() throws IOException {
    System.out.println("\n\n*** CICHORIEAE ***");
    var hc = HomotypicConsolidator.forTaxa(SqlSessionFactoryRule.getSqlSessionFactory(), dataRule.testData.key,
      List.of(SimpleName.sn("atc", Rank.TRIBE, "Cichorieae", "")),
      u -> Integer.parseInt(u.getId()) // lower ids have priority!
    );
    printTree();
    hc.consolidate();
    assertTree("hg-atc.txt", "atc");

    System.out.println("\n\n*** VERNONIEAE ***");

    hc = HomotypicConsolidator.forTaxa(SqlSessionFactoryRule.getSqlSessionFactory(), dataRule.testData.key,
      List.of(SimpleName.sn("atv", Rank.TRIBE, "Vernonieae", "")),
      u -> Integer.parseInt(u.getId()) // lower ids have priority!
    );
    printTree();
    hc.consolidate();
    assertTree("hg-atv.txt", "atv");
  }

  void printTree() throws IOException {
    Writer writer = new StringWriter();
    PrinterFactory.dataset(TextTreePrinter.class, dataRule.testData.key, SqlSessionFactoryRule.getSqlSessionFactory(), writer).print();
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

  void assertTree(String filename, String rootID) throws IOException {
    SectorSyncIT.assertTree(dataRule.testData.key, rootID, openResourceStream(filename));
  }

  InputStream openResourceStream(String filename) {
    return getClass().getResourceAsStream("/grouping-trees/" + filename);
  }
}