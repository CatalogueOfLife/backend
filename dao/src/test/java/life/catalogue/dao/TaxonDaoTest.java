package life.catalogue.dao;

import life.catalogue.api.BeanPrinter;
import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.*;
import life.catalogue.db.MybatisTestUtils;
import life.catalogue.db.SqlSessionFactoryRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.NameRelationMapper;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.db.mapper.SectorMapperTest;
import life.catalogue.db.mapper.SynonymMapper;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.matching.NameIndexFactory;

import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.apache.ibatis.session.SqlSession;
import org.junit.Test;

import static life.catalogue.api.TestEntityGenerator.*;
import static org.junit.Assert.*;

public class TaxonDaoTest {

  @Test
  public void typeContent() {
    var tm = new TypeMaterial();
    String x = TaxonDao.typeContent(tm);
    assertEquals("", x);

    tm.setCollector("COL");
    x = TaxonDao.typeContent(tm);
    assertEquals("col|col", x);

    tm.setCountry(Country.GERMANY);
    x = TaxonDao.typeContent(tm);
    assertEquals("col|germany|col", x);
  }

}
