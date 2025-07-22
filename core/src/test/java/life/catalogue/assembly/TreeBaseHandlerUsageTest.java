package life.catalogue.assembly;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.EntityType;
import life.catalogue.api.vocab.IgnoreReason;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.TaxonMapper;
import life.catalogue.matching.nidx.NameIndex;
import life.catalogue.release.UsageIdGen;

import org.gbif.nameparser.api.Rank;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class TreeBaseHandlerUsageTest {

  @Test
  public void usageToString() {
    var u = new TreeHandler.Usage("1234", "p987", Rank.SPECIES, TaxonomicStatus.SYNONYM, null);
    System.out.println(u);
  }
}