package life.catalogue.matching.person.harvest;

import life.catalogue.api.event.PersonsChanged;
import life.catalogue.api.model.JobInfo;
import life.catalogue.api.model.JobResult;
import life.catalogue.api.model.Page;
import life.catalogue.api.search.JobSearchRequest;
import life.catalogue.api.vocab.JobPriority;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.config.PersonConfig;
import life.catalogue.db.mapper.JobMapper;
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
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.annotation.Nullable;

import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * Harvests the person registry from Wikidata and IPNI and rewrites it in Postgres, following what the sources say now
 * while curated lines win, see {@link PersonMerger}. The sources are read with no database session open - the idle in
 * transaction timeout would end a long transaction - and every answer is cached while they are read: a run that fails
 * reading leaves the cache for the next to resume from, and once every source has been read it is deleted, so no later
 * run replays these answers, whatever happens to this one. The merge, its checks and the write happen in one
 * short transaction that keeps other writers out; an inconsistent result writes nothing and fails the job. Either way
 * the review report is the job's download.
 */
public class PersonHarvestJob extends BackgroundJob {
  static final String REPORT = "report.md";
  private final SqlSessionFactory factory;
  private final EventBroker broker;
  private final Path cacheDir;
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
   * @param cacheDir the directory the sources cache their answers in, deleted once every source has been read
   */
  public PersonHarvestJob(int userKey, SqlSessionFactory factory, EventBroker broker, Path cacheDir, Sources sources) {
    super(JobPriority.LOW, userKey);
    this.factory = factory;
    this.broker = broker;
    this.cacheDir = cacheDir;
    this.sources = sources;
    this.result = new JobResult(getKey());
  }

  /**
   * @return the harvest of Wikidata and IPNI, cached in the harvest directory of the config
   */
  public static PersonHarvestJob live(int userKey, SqlSessionFactory factory, EventBroker broker, PersonConfig cfg) {
    Path cache = cfg.harvestDir.toPath().resolve("cache");
    return new PersonHarvestJob(userKey, factory, broker, cache, Sources.live(cache));
  }

  /**
   * @return when the last harvest finished that succeeded, null for none
   */
  @Nullable
  public static LocalDateTime lastFinished(SqlSessionFactory factory) {
    var req = new JobSearchRequest();
    req.setJob(Set.of(PersonHarvestJob.class.getSimpleName()));
    req.setStatus(Set.of(JobStatus.FINISHED));
    try (SqlSession session = factory.openSession(true)) {
      List<JobInfo> jobs = session.getMapper(JobMapper.class).search(req, new Page(0, 1));
      return jobs.isEmpty() ? null : jobs.get(0).getFinished();
    }
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
    // every source has been read: no later run may replay these answers, even if this one fails from here on
    FileUtils.deleteDirectory(cacheDir.toFile());
    checkIfCancelled();
    setStep("rebuilding the registry");
    PersonTables.transaction(factory, session -> {
      var outcome = PersonRebuild.rebuild(PersonTables.read(session), harvest, redirects, LocalDate.now());
      writeReport(outcome.report());
      if (!outcome.problems().isEmpty()) {
        throw new IllegalStateException("The harvested registry is inconsistent, nothing written: "
          + String.join("; ", outcome.problems().subList(0, Math.min(10, outcome.problems().size()))));
      }
      PersonTables.write(session, outcome.content());
    });
    broker.publish(new PersonsChanged(getUserKey()));
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
