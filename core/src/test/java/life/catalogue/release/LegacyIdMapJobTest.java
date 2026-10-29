package life.catalogue.release;

import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.model.SimpleNameClassified;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.matching.UsageMatch;

import org.gbif.nameparser.api.Rank;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

public class LegacyIdMapJobTest {
  static final String HEADER = "hash\tid\tstatus\tscientificName\tauthorship\n";
  static final String KEYS = HEADER +
    "aad93abc0884c2d7ab83b609d23243fe\t316421\tsynonym\tAcierda pasqulerl\tBalachowsky, 1939\n" +
    "6a3ba2fef8659ce9708106356d875285\t316423\taccepted\tLimacoccus brasiliensis\t(Hempel, 1934)\n" +
    "000084b8749c5b17d948a4add1c816df\t316500\tmisapplied\tPristis microdon\t(non Latham, 1794)\n" +
    "100084b8749c5b17d948a4add1c816df\t316500\tmisapplied\tPristis microdon\t(non Latham, 1794)\n" +
    "ffffffffffffffffffffffffffffffff\t999999\thigher taxon\tCoccoidea\t\n";

  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  File keysFile(String content) throws IOException {
    File f = folder.newFile();
    Files.writeString(f.toPath(), content);
    return f;
  }

  @Test
  public void keys() throws Exception {
    var keys = LegacyIdMapJob.Keys.load(keysFile(KEYS));
    assertEquals(5, keys.size);
    assertEquals(0, keys.first("316421"));
    assertEquals("aad93abc0884c2d7ab83b609d23243fe", keys.hash(0));
    assertEquals("6a3ba2fef8659ce9708106356d875285", keys.hash(keys.first("316423")));
    // the first of two keys sharing an id, hashes with leading zeros and all bits set survive
    assertEquals(2, keys.first("316500"));
    assertEquals("000084b8749c5b17d948a4add1c816df", keys.hash(2));
    assertEquals("ffffffffffffffffffffffffffffffff", keys.hash(keys.first("999999")));
    assertEquals(-1, keys.first("316422"));
    assertEquals(-1, keys.first("s1"));
    assertEquals(-1, keys.first(null));
  }

  @Test
  public void keysGrow() throws Exception {
    var sb = new StringBuilder(HEADER);
    for (int id = 1; id <= 5000; id++) {
      sb.append(String.format("%032x\t%d\taccepted\tAus bus\t\n", id, id));
    }
    var keys = LegacyIdMapJob.Keys.load(keysFile(sb.toString()));
    assertEquals(5000, keys.size);
    assertEquals(String.format("%032x", 4711), keys.hash(keys.first("4711")));
  }

  @Test(expected = IllegalArgumentException.class)
  public void keysUnsorted() throws Exception {
    LegacyIdMapJob.Keys.load(keysFile(HEADER +
      "6a3ba2fef8659ce9708106356d875285\t316423\taccepted\tLimacoccus brasiliensis\t(Hempel, 1934)\n" +
      "aad93abc0884c2d7ab83b609d23243fe\t316421\tsynonym\tAcierda pasqulerl\tBalachowsky, 1939\n"
    ));
  }

  @Test
  public void status() {
    assertEquals(TaxonomicStatus.ACCEPTED, LegacyIdMapJob.Keys.status("higher taxon"));
    assertEquals(TaxonomicStatus.PROVISIONALLY_ACCEPTED, LegacyIdMapJob.Keys.status("provisionally accepted"));
    assertEquals(TaxonomicStatus.AMBIGUOUS_SYNONYM, LegacyIdMapJob.Keys.status("ambiguous synonym"));
    assertEquals(TaxonomicStatus.MISAPPLIED, LegacyIdMapJob.Keys.status("misapplied"));
  }

  static SimpleNameClassified<SimpleName> usage(String id, String name, String authorship, TaxonomicStatus status) {
    var sn = new SimpleNameClassified<>(SimpleName.sn(Rank.SPECIES, name));
    sn.setId(id);
    sn.setAuthorship(authorship);
    sn.setStatus(status);
    sn.setClassification(List.of());
    return sn;
  }

  static SimpleNameClassified<SimpleNameCached> match(String id, String name, TaxonomicStatus status, SimpleNameCached... classification) {
    var sn = new SimpleNameClassified<SimpleNameCached>(new SimpleNameCached(id, name, Rank.SPECIES));
    sn.setStatus(status);
    sn.setClassification(List.of(classification));
    return sn;
  }

  @Test
  public void write() throws Exception {
    var file = keysFile(KEYS);
    var keys = LegacyIdMapJob.Keys.load(file);
    // the legacy dataset lacks 999999 and has a usage without a key
    var usages = Stream.of(
      usage("316423", "Limacoccus brasiliensis", "(Hempel, 1934)", TaxonomicStatus.ACCEPTED),
      usage("316421", "Acierda pasqulerl", "Balachowsky, 1939", TaxonomicStatus.SYNONYM),
      usage("316500", "Pristis microdon", "(non Latham, 1794)", TaxonomicStatus.MISAPPLIED),
      usage("777", "Aus bus", null, TaxonomicStatus.ACCEPTED)
    );
    List<String> matched = new ArrayList<>();
    var out = new ByteArrayOutputStream();
    LegacyIdMapJob.write("col19", keys, usages, () -> LegacyIdMapJob.Keys.stream(file), sn -> {
      matched.add(sn.getId());
      return switch (sn.getName()) {
        case "Limacoccus brasiliensis" -> UsageMatch.match(MatchType.EXACT, match("6TQ3", "Limacoccus brasiliensis", TaxonomicStatus.ACCEPTED), 3, null);
        case "Acierda pasqulerl" -> UsageMatch.match(MatchType.EXACT, match("4BX8", "Acierda pasqulerl", TaxonomicStatus.SYNONYM,
          new SimpleNameCached("7RZ2", "Aclerda pasquieri", Rank.SPECIES)), 3, null);
        case "Coccoidea" -> UsageMatch.match(MatchType.VARIANT, match("C0C", "Coccoidea", TaxonomicStatus.ACCEPTED), 3, null);
        default -> UsageMatch.empty(3);
      };
    }, out);

    // every usage with a key is matched once, the missing key by its name. The usage without a key is not matched
    assertEquals(List.of("316423", "316421", "316500", "999999"), matched);

    var lines = out.toString(StandardCharsets.UTF_8).split("\n");
    assertEquals(6, lines.length);
    assertEquals("col19ID\tcol19InternalID\tcol19Status\tcol19Rank\tcol19ScientificName\tcol19Authorship\tmatchType\tID\trank\tstatus\tscientificName\tauthorship\tacceptedID\tacceptedScientificName\tacceptedAuthorship", lines[0]);
    assertEquals("6a3ba2fef8659ce9708106356d875285\t316423\taccepted\tspecies\tLimacoccus brasiliensis\t(Hempel, 1934)\texact\t6TQ3\tspecies\taccepted\tLimacoccus brasiliensis\t\t\t\t", lines[1]);
    assertEquals("aad93abc0884c2d7ab83b609d23243fe\t316421\tsynonym\tspecies\tAcierda pasqulerl\tBalachowsky, 1939\texact\t4BX8\tspecies\tsynonym\tAcierda pasqulerl\t\t7RZ2\tAclerda pasquieri\t", lines[2]);
    // both keys of a record, unmatched
    assertTrue(lines[3].startsWith("000084b8749c5b17d948a4add1c816df\t316500\tmisapplied\t"));
    assertTrue(lines[4].startsWith("100084b8749c5b17d948a4add1c816df\t316500\tmisapplied\t"));
    assertEquals("ffffffffffffffffffffffffffffffff\t999999\taccepted\t\tCoccoidea\t\tvariant\tC0C\tspecies\taccepted\tCoccoidea\t\t\t\t", lines[5]);
  }
}
