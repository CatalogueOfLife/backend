package org.col.matching;

import java.io.File;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.google.common.base.Stopwatch;
import org.col.api.model.Name;
import org.col.matching.authorship.AuthorComparator;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mapdb.DBMaker;

/**
 *   100.000 inserts / lookups in ms
 * 1.000.000 inserts / lookups in ms
 *
 * Memory hashmap
 *   1740 / 623
 * Memory treemap (values outside)
 *   3649 / 1638
 *
 *
 * File hashmap
 *   1819 /  652
 *  16425 / 6741
 * File treemap (values outside)
 *   3813 /  1554
 *  31833 / 15002
 *
 */
@Ignore("Manual test for understanding performance & behavior of mapdb")
public class NameIndexMapDBTest {

  NameIndex ni;
  int SIZE = 1000000;
  private File dbf;

  @Before
  public void init() {
    dbf = new File("/tmp/ni-test");
    DBMaker.Maker dbMaker = DBMaker
        .fileDB(dbf)
        .fileMmapEnableIfSupported();
    //dbMaker = DBMaker.memoryDB();

    ni = new NameIndex(dbMaker, AuthorComparator.createWithAuthormap(), datasetKey);
  }

  @After
  public void teardown() throws Exception {
    ni.close();
    if (dbf.exists()) {
      dbf.delete();
    }
  }

  @Test
  public void add() {
    Stopwatch watch = Stopwatch.createUnstarted();
    ni.add(name(-1));

    final List<Name> names = IntStream.rangeClosed(1, SIZE)
        .mapToObj(NameIndexMapDBTest::name)
        .collect(Collectors.toList());

    watch.start();
    ni.addAll(names);
    watch.stop();
    System.out.println("Writes: "+ watch.elapsed().toMillis()+"ms");

    watch.reset();
    Random rnd = new Random();
    Name n = name(0);
    watch.start();
    for (int q = 1; q<SIZE; q++) {
      NameMatch match = ni.match(upd(n, q), false);
    }
    watch.stop();
    System.out.println("Reads: "+ watch.elapsed().toMillis()+"ms");

  }

  private static Name upd(Name n, int i) {
    n.setKey(i);
    n.setScientificName("Abies alba ssp"+i);
    return n;
  }

  private static Name name(int i) {
    Name n = new Name();
    n.setRank(Rank.SUBSPECIES);
    n.setGenus("Abies");
    n.setSpecificEpithet("alba");
    n.setType(NameType.INFORMAL);
    upd(n, i);
    return n;
  }
}