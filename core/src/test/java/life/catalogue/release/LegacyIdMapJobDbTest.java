package life.catalogue.release;

import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.model.SimpleNameClassified;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.MatchingMode;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.api.vocab.Users;
import life.catalogue.config.ReleaseConfig;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;
import life.catalogue.junit.TxtTreeDataRule;
import life.catalogue.matching.UsageMatch;
import life.catalogue.matching.UsageMatcher;
import life.catalogue.matching.UsageMatcherFactory;
import life.catalogue.matching.nidx.NameIndexFactory;

import org.gbif.nameparser.api.Rank;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.zip.GZIPInputStream;

import org.apache.ibatis.session.SqlSession;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class LegacyIdMapJobDbTest {
  static final int LEGACY_KEY = 100;
  static final int XR_KEY = 101;

  @ClassRule
  public final static SqlSessionFactoryRule pg = new PgSetupRule();

  @Rule
  public final TestDataRule dataRule = TestDataRule.empty();

  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  UsageMatcherFactory matcherFactory;
  UsageMatcher matcher;
  ReleaseConfig.LegacyIdMap map;

  @Before
  public void setUp() throws Throwable {
    try (TxtTreeDataRule treeRule = new TxtTreeDataRule(List.of(
      new TxtTreeDataRule.TreeDataset(LEGACY_KEY, "legacy-idmap/col19.txtree", "COL19", DatasetOrigin.EXTERNAL),
      new TxtTreeDataRule.TreeDataset(XR_KEY, "legacy-idmap/xr.txtree", "COL XR", DatasetOrigin.XRELEASE)
    ))) {
      treeRule.before();
    }
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true);
         var stmt = session.getConnection().createStatement()
    ) {
      stmt.execute("UPDATE dataset SET issued = '2026-09-25' WHERE key = " + XR_KEY);
    }

    // the matcher finds the accepted name only
    var match = new SimpleNameClassified<SimpleNameCached>(new SimpleNameCached("6TQ3", "Limacoccus brasiliensis", Rank.SPECIES));
    match.setStatus(TaxonomicStatus.ACCEPTED);
    match.setClassification(List.of());
    matcher = mock(UsageMatcher.class);
    when(matcher.match(any(), any(MatchingMode.class), anyBoolean())).thenAnswer(inv -> {
      SimpleNameClassified<?> q = inv.getArgument(0);
      return "Limacoccus brasiliensis".equals(q.getName()) ? UsageMatch.match(MatchType.EXACT, match, XR_KEY, null) : UsageMatch.empty(XR_KEY);
    });
    matcherFactory = mock(UsageMatcherFactory.class);
    when(matcherFactory.persistent(anyInt())).thenReturn(matcher);
    when(matcherFactory.getNameIndex()).thenReturn(NameIndexFactory.passThru());

    map = new ReleaseConfig.LegacyIdMap();
    map.name = "col19";
    map.datasetKey = LEGACY_KEY;
    map.keys = folder.newFile("2019_natural_keys.tsv");
    Files.writeString(map.keys.toPath(), "hash\tid\tstatus\tscientificName\tauthorship\n" +
      "aad93abc0884c2d7ab83b609d23243fe\t316421\tsynonym\tAcierda pasqulerl\tBalachowsky, 1939\n" +
      "6a3ba2fef8659ce9708106356d875285\t316423\taccepted\tLimacoccus brasiliensis\t(Hempel, 1934)\n" +
      "0896d3e342aabf8da757dc9aae049aa5\t316442\taccepted\tEriococcus chathamensis\tHoy, 1962\n"
    );
  }

  @Test
  public void mapping() throws Exception {
    File downloads = folder.newFolder("col");
    var job = new LegacyIdMapJob(map, XR_KEY, Users.TESTER, downloads, SqlSessionFactoryRule.getSqlSessionFactory(), matcherFactory);
    File expected = new File(downloads, "monthly/2026-09-25_xr_col19-idmap.tsv.gz");
    assertEquals(expected, job.file());

    job.run();
    assertNull(job.getError());
    assertTrue(expected.exists());
    verify(matcherFactory).persistent(XR_KEY);
    verify(matcher).close();
    // no temp file left behind
    assertEquals(1, expected.getParentFile().list().length);

    List<String> lines;
    try (var in = new GZIPInputStream(new FileInputStream(expected))) {
      lines = List.of(new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n"));
    }
    // header + 3 keys: two from the legacy usages, the missing one from the keys file
    assertEquals(4, lines.size());
    assertTrue(lines.contains("6a3ba2fef8659ce9708106356d875285\t316423\taccepted\tspecies\tLimacoccus brasiliensis\t(Hempel, 1934)\texact\t6TQ3\tspecies\taccepted\tLimacoccus brasiliensis\t\t\t\t"));
    assertTrue(lines.stream().anyMatch(l -> l.startsWith("aad93abc0884c2d7ab83b609d23243fe\t316421\tsynonym\tspecies\tAcierda pasqulerl\tBalachowsky, 1939\tnone\t")));
    assertEquals("0896d3e342aabf8da757dc9aae049aa5\t316442\taccepted\t\tEriococcus chathamensis\tHoy, 1962\tnone\t\t\t\t\t\t\t\t", lines.get(3));

    // an existing file is never overwritten
    long modified = expected.lastModified();
    Files.writeString(expected.toPath(), "keep");
    var again = new LegacyIdMapJob(map, XR_KEY, Users.TESTER, downloads, SqlSessionFactoryRule.getSqlSessionFactory(), matcherFactory);
    again.run();
    assertNull(again.getError());
    assertEquals("keep", Files.readString(expected.toPath()));
    verify(matcherFactory, times(1)).persistent(XR_KEY);
  }

  @Test(expected = IllegalArgumentException.class)
  public void noRelease() throws Exception {
    new LegacyIdMapJob(map, LEGACY_KEY, Users.TESTER, folder.newFolder(), SqlSessionFactoryRule.getSqlSessionFactory(), matcherFactory);
  }
}
