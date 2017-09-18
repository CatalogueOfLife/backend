package org.col.db.mapper;

import com.google.common.base.Stopwatch;
import org.col.api.Name;
import org.col.api.vocab.Rank;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 *
 */
@Ignore
public class NameMapperNoRuleTest extends MapperTestBaseNoRule<NameMapper> {

  private int repeat = 1000;

  public NameMapperNoRuleTest() {
    super(NameMapper.class);
  }

  private Name create() throws Exception {
    Name n = new Name();
    n.setScientificName("Abies alba Mill.");
    n.setCanonicalName("Abies alba");
    n.setAuthorship("Mill.");
    n.setMonomial("Abies");
    n.setEpithet("alba");
    n.setInfraEpithet(null);
    n.setRank(Rank.SPECIES);
    return n;
  }

  @Test
  public void writeRead() throws Exception {
    Name s1 = create();
    mapper.insert(s1);

    commit();

    Name s2 = mapper.get(s1.getKey());

    assertEquals(s1, s2);
  }

  @Test
  public void roughPerformance() throws Exception {
    Stopwatch watch = Stopwatch.createStarted();

    for (int i = 0; i < repeat; i++) {
      Name n = create();
      mapper.insert(n);
    }
    commit();
    watch.stop();
    System.out.println(repeat + " INSERTS: " + watch.toString());

    watch.reset();
    watch.start();
    for (int i = 2; i < repeat + 2; i++) {
      Name n = mapper.get(i);
    }
    watch.stop();
    System.out.println(repeat + " READS: " + watch.toString());
  }

}