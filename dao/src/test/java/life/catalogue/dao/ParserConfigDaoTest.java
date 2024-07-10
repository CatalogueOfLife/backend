package life.catalogue.dao;

import life.catalogue.api.model.ParsedNameUsage;
import life.catalogue.api.model.ParserConfig;
import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.api.vocab.Users;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.parser.NameParser;

import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

import org.junit.ClassRule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ParserConfigDaoTest {
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Test
  public void addToParser() throws InterruptedException {
    ParserConfigDao dao = new ParserConfigDao(SqlSessionFactoryRule.getSqlSessionFactory());
    ParserConfig cfg = new ParserConfig();
    cfg.setScientificName("Jezzinothrips cretacicus");
    cfg.setAuthorship("zur Strassen, 1973");
    cfg.setGenus("Jezzinothrips");
    cfg.setSpecificEpithet("cretacicus");
    cfg.setRank(Rank.SPECIES);
    cfg.setType(NameType.SCIENTIFIC);
    cfg.setCombinationAuthorship(Authorship.yearAuthors("1977", "zur Straßen"));

    assertParsed("Jezzinothrips cretacicus", "zur Strassen, 1973", Rank.SPECIES, "1973","zur Strassen");
    dao.putName(cfg, Users.TESTER);
    assertParsed("Jezzinothrips cretacicus", "zur Strassen, 1973", Rank.SPECIES, "1977","zur Straßen");
    assertParsed("Jezzinothrips cretacicus", "zur Strassen,1973", Rank.SPECIES, "1977","zur Straßen");
    assertParsed("Jezzinothrips  cretacicus", " zur  Strassen , 1973 ", Rank.SPECIES, "1977","zur Straßen");
  }

  void assertParsed(String name, String authorship, Rank rank, String year, String... authors) throws InterruptedException {
    ParsedNameUsage pn = NameParser.PARSER.parse(name, authorship, rank, null, VerbatimRecord.VOID).get();
    assertEquals(rank, pn.getName().getRank());
    assertEquals(Authorship.yearAuthors(year, authors), pn.getName().getCombinationAuthorship());
  }
}