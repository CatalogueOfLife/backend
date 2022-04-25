package life.catalogue.db.mapper;

import life.catalogue.api.RandomUtils;
import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.api.vocab.Users;
import life.catalogue.dao.NameDao;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.matching.NameIndexFactory;

import org.apache.ibatis.cursor.Cursor;

import org.gbif.nameparser.api.Rank;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static life.catalogue.api.TestEntityGenerator.DATASET11;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NameUsageMapperNidxTest extends MapperTestBase<NameUsageMapper> {

  public NameUsageMapperNidxTest() {
    super(NameUsageMapper.class, TestDataRule.nidx());
  }

  @Test
  public void testByNidx() throws Exception {
    // with author
    var res = mapper().listByNamesIndexIDGlobal( 4, new Page());
    assertEquals(3, res.size());
    Set<DSID<String>> usageIDs = res.stream().map(DSID::copy).collect(Collectors.toSet());
    assertEquals(
      Set.of(DSID.of(100, "u1"), DSID.of(102, "u1x"), DSID.of(102, "u2x")),
      usageIDs
    );

    // canonical +1
    assertEquals(4, mapper().listByNamesIndexIDGlobal( 3, new Page()).size());

    // none
    assertEquals(0, mapper().listByNamesIndexIDGlobal( 1, new Page()).size());
  }

}