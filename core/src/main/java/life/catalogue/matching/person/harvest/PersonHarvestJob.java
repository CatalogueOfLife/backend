package life.catalogue.matching.person.harvest;

import life.catalogue.api.event.PersonsChanged;
import life.catalogue.api.model.JobResult;
import life.catalogue.api.vocab.JobPriority;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.config.PersonConfig;
import life.catalogue.event.EventBroker;
import life.catalogue.matching.person.PersonFiles;
import life.catalogue.matching.person.PersonTables;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * Harvests the person registry from Wikidata and IPNI and rewrites it in Postgres, following what the sources say now
 * while curated lines win, see {@link PersonMerger}. The sources are read with no database session open - the idle in
 * transaction timeout would end a long transaction - and every answer is cached in the run directory, which a failed run
 * leaves for the next to resume from and a successful one deletes. The merge, its checks and the write happen in one
 * short transaction that keeps other writers out; an inconsistent result writes nothing and fails the job. Either way
 * the review report is the job's download.
 */
public class PersonHarvestJob extends BackgroundJob {
  static final String REPORT = "report.md";
  private final SqlSessionFactory factory;
  private final EventBroker broker;
  private final Path runDir;
  private final Sources sources;
  private final JobResult result;

  @FunctionalInterface
  public interface RedirectLookup {
    /**
     * @return old Q-id to new Q-id of the items that became a redirect
     */
    Map<String, String> redirects(Collection<String> qids) throws Exception;
  }

  /**
   * The authorities to read, and how to ask Wikidata for the redirects of vanished items.
   */
  public record Sources(List<HarvestSource> list, RedirectLookup redirects) {

    /**
     * Wikidata and IPNI over HTTP, serially, 1 s and 250 ms apart, every complete answer cached in the directory.
     */
    public static Sources live(Path cache) {
      var wikidata = new WikidataPersonSource(new CachingFetcher(cache.resolve("wikidata"),
        new RetryingFetcher(new HttpFetcher("application/json"), Duration.ofSeconds(1), Json::complete), Json::complete));
      var ipni = new IpniPersonSource(new CachingFetcher(cache.resolve("ipni"),
        new RetryingFetcher(new HttpFetcher("application/json"), Duration.ofMillis(250), Json::complete), Json::complete));
      return new Sources(List.of(wikidata, ipni), wikidata::redirects);
    }
  }

  /**
   * @param runDir the directory the answers of the sources are cached in, deleted when the run succeeds
   */
  public PersonHarvestJob(int userKey, SqlSessionFactory factory, EventBroker broker, Path runDir, Sources sources) {
    super(JobPriority.LOW, userKey);
    this.factory = factory;
    this.broker = broker;
    this.runDir = runDir;
    this.sources = sources;
    this.result = new JobResult(getKey());
  }

  /**
   * @return the harvest of Wikidata and IPNI, cached in the harvest directory of the config
   */
  public static PersonHarvestJob live(int userKey, SqlSessionFactory factory, EventBroker broker, PersonConfig cfg) {
    Path run = cfg.harvestDir.toPath();
    return new PersonHarvestJob(userKey, factory, broker, run, Sources.live(run.resolve("cache")));
  }

  @Override
  public Object getSerialBy() {
    return PersonHarvestJob.class.getSimpleName();
  }

  @Override
  public boolean isDuplicate(BackgroundJob other) {
    return other instanceof PersonHarvestJob;
  }

  @Override
  public JobResult getResult() {
    return result.getFile().exists() ? result : null;
  }

  @Override
  public void execute() throws Exception {
    setStep("reading the sources");
    PersonRebuild.Harvest harvest = PersonRebuild.fetch(sources.list());
    checkIfCancelled();
    PersonFiles.Content before;
    try (SqlSession session = factory.openSession(true)) {
      before = PersonTables.read(session);
    }
    setStep("asking Wikidata for redirects");
    List<String> gone = PersonRebuild.gone(before, harvest);
    Map<String, String> redirects = gone.isEmpty() ? Map.of() : sources.redirects().redirects(gone);
    checkIfCancelled();
    setStep("rebuilding the registry");
    try (SqlSession session = factory.openSession(false)) {
      try {
        PersonTables.lock(session);
        var outcome = PersonRebuild.rebuild(PersonTables.read(session), harvest, redirects, LocalDate.now());
        writeReport(outcome.report());
        if (!outcome.problems().isEmpty()) {
          throw new IllegalStateException("The harvested registry is inconsistent, nothing written: "
            + String.join("; ", outcome.problems().subList(0, Math.min(10, outcome.problems().size()))));
        }
        PersonTables.write(session, outcome.content());
        // forced: the writes went past MyBatis, which would otherwise neither commit nor roll back
        session.commit(true);
      } catch (Exception e) {
        session.rollback(true);
        throw e;
      }
    }
    broker.publish(new PersonsChanged(getUserKey()));
    FileUtils.deleteDirectory(runDir.toFile());
  }

  private void writeReport(String report) throws IOException {
    File zip = result.getFile();
    FileUtils.forceMkdirParent(zip);
    try (var out = new ZipOutputStream(new FileOutputStream(zip))) {
      out.putNextEntry(new ZipEntry(REPORT));
      out.write(report.getBytes(StandardCharsets.UTF_8));
      out.closeEntry();
    }
    result.calculateSizeAndMd5();
  }
}
