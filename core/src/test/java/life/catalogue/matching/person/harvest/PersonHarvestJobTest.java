package life.catalogue.matching.person.harvest;

import life.catalogue.api.event.PersonsChanged;
import life.catalogue.api.model.Person;
import life.catalogue.api.model.PersonName;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.api.vocab.PersonFormCode;
import life.catalogue.api.vocab.PersonNameKind;
import life.catalogue.api.vocab.PersonSource;
import life.catalogue.api.vocab.Users;
import life.catalogue.event.EventBroker;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;
import life.catalogue.matching.person.PersonFiles;
import life.catalogue.matching.person.PersonTables;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PersonHarvestJobTest {

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public TestDataRule testDataRule = TestDataRule.empty();

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  private final EventBroker broker = mock(EventBroker.class);
  private Path run;

  @Before
  public void init() throws IOException {
    run = tmp.newFolder("run").toPath();
    // an answer an earlier, failed run cached
    Files.writeString(run.resolve("cached.json"), "{}");
  }

  private static SqlSessionFactory factory() {
    return SqlSessionFactoryRule.getSqlSessionFactory();
  }

  private PersonHarvestJob job(HarvestSource... sources) {
    return new PersonHarvestJob(Users.TESTER, factory(), broker, run, new PersonHarvestJob.Sources(List.of(sources), qids -> Map.of()));
  }

  private static HarvestSource source(PersonRecord... records) {
    return new HarvestSource() {
      public String name() {
        return "test";
      }

      public List<PersonRecord> read() {
        return List.of(records);
      }
    };
  }

  private static PersonRecord wd(String q, String label) {
    var w = new PersonRecord.Builder(PersonSource.WIKIDATA);
    w.wikidata = q;
    w.label(label);
    return w.build();
  }

  private static List<String> ids() {
    try (SqlSession session = factory().openSession(true)) {
      return PersonTables.read(session).persons().stream().map(Person::id).sorted().toList();
    }
  }

  private static String report(PersonHarvestJob job) throws IOException {
    try (var zip = new ZipFile(job.getResult().getFile())) {
      return new String(zip.getInputStream(zip.getEntry(PersonHarvestJob.REPORT)).readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  public void writesAndAnnounces() throws Exception {
    var job = job(source(wd("Q1", "Carl Linnaeus")));
    job.run();
    assertEquals(String.valueOf(job.getError()), JobStatus.FINISHED, job.getStatus());
    assertEquals(List.of("wd:Q1"), ids());
    verify(broker).publish(new PersonsChanged(Users.TESTER));
    assertFalse("a successful run leaves no cache behind", Files.exists(run));
    assertTrue(report(job), report(job).contains("## Persons added: 1"));
  }

  /** a local person with an authority id is inconsistent, and a harvest keeps local persons as they are */
  @Test
  public void inconsistentWritesNothing() throws Exception {
    var bad = new Person("clb:1", "Q9", null, null, List.of(), "Doe", null, null, null, null, null, null, Set.of(),
      PersonSource.CURATED);
    try (SqlSession session = factory().openSession(false)) {
      PersonTables.write(session, new PersonFiles.Content(List.of(bad),
        List.of(new PersonName("clb:1", "Ann Doe", PersonNameKind.FULL, PersonFormCode.ANY, PersonSource.CURATED)), List.of()));
      session.commit(true);
    }
    var job = job(source(wd("Q2", "Ann Roe")));
    job.run();
    assertEquals(JobStatus.FAILED, job.getStatus());
    assertEquals(List.of("clb:1"), ids());
    verifyNoInteractions(broker);
    assertTrue("a failed run keeps its cache for the next", Files.exists(run.resolve("cached.json")));
    assertTrue(report(job), report(job).contains("clb:1 is local but has authority ids"));
  }

  @Test
  public void failingSourceWritesNothing() {
    HarvestSource broken = new HarvestSource() {
      public String name() {
        return "broken";
      }

      public List<PersonRecord> read() {
        throw new IllegalStateException("HTTP 503");
      }
    };
    var job = job(broken);
    job.run();
    assertEquals(JobStatus.FAILED, job.getStatus());
    assertEquals(List.of(), ids());
    verifyNoInteractions(broker);
    assertTrue(Files.exists(run.resolve("cached.json")));
  }
}
