package life.catalogue.jobs;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.TaxonWithClassification;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.JobPriority;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.DatasetBlockingJob;
import life.catalogue.dao.DaoUtils;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.VerbatimRecordMapper;
import life.catalogue.es.indexing.NameUsageIndexService;
import life.catalogue.matching.AddIdentifiersSpec;
import life.catalogue.matching.PlacementChecker;
import life.catalogue.matching.UsageMatcherFactory;

import org.gbif.nameparser.api.Rank;

import java.util.ArrayList;
import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

/**
 * Flags the accepted genera of an external dataset with a {@link Issue#PLACEMENT_CONFLICT} if they are placed in a taxonomic
 * group the reference checklist does not know them in, the same check an import does. It replaces earlier flags and
 * recounts the issues of the latest import, so datasets imported before the check existed, or never reimported because
 * their source did not change, can be reported and merged with the same safeguards.
 * See https://github.com/CatalogueOfLife/backend/issues/1608
 */
public class PlacementCheckJob extends DatasetBlockingJob {
  private static final Logger LOG = LoggerFactory.getLogger(PlacementCheckJob.class);
  private final SqlSessionFactory factory;
  private final UsageMatcherFactory matcherFactory;
  private final DatasetImportDao diDao;
  private final NameUsageIndexService indexService;
  private final List<String> references;
  private int genera;
  private int conflicts;

  public record Params(List<String> references) {
  }

  /**
   * @param references the reference checklists, dataset keys or project keys with an LR or LXR suffix, e.g. 3LR
   */
  public PlacementCheckJob(int userKey, int datasetKey, List<String> references, SqlSessionFactory factory,
                           UsageMatcherFactory matcherFactory, DatasetImportDao diDao, NameUsageIndexService indexService) {
    super(datasetKey, userKey, JobPriority.LOW);
    DaoUtils.notProject(datasetKey);
    Preconditions.checkArgument(references != null && !references.isEmpty(), "A placement reference is required");
    references.forEach(AddIdentifiersSpec::parse); // fail early on a bad reference
    this.references = List.copyOf(references);
    this.factory = factory;
    this.matcherFactory = matcherFactory;
    this.diDao = diDao;
    this.indexService = indexService;
  }

  @Override
  public Object getParams() {
    return new Params(references);
  }

  @Override
  public boolean isDuplicate(BackgroundJob other) {
    return other instanceof PlacementCheckJob job && job.datasetKey == datasetKey;
  }

  @Override
  protected void runWithLock() throws Exception {
    var checker = PlacementChecker.open(references, matcherFactory, factory, datasetKey);
    if (checker == null) {
      throw new IllegalStateException("None of the placement references " + references + " can be used");
    }
    setStep("checking genera");
    List<TaxonWithClassification> flagged = new ArrayList<>();
    try (checker;
         SqlSession session = factory.openSession()
    ) {
      try (var cursor = session.getMapper(NameUsageMapper.class).processDatasetWithClassification(datasetKey, Rank.GENUS, Rank.GENUS, false, false)) {
        for (var u : cursor) {
          checkIfCancelled();
          if (u instanceof TaxonWithClassification t) {
            genera++;
            var conflict = checker.check(new SimpleName(t), t.getClassification());
            if (conflict.isPresent()) {
              LOG.info("Genus {} [{}] placed in {} by {} is known in {} only in {}", t.getLabel(), t.getId(), conflict.get().group(),
                t.getClassification(), checker.getReferenceDatasetKeys(), conflict.get().referenceGroups());
              flagged.add(t);
            }
          }
        }
      }
    }
    setStep("flagging genera");
    List<String> reindex = new ArrayList<>();
    try (SqlSession session = factory.openSession(true)) {
      var vm = session.getMapper(VerbatimRecordMapper.class);
      // the previous flags, which may no longer apply, are reindexed too
      reindex.addAll(session.getMapper(NameUsageMapper.class).listUsageIDsByVerbatimIssue(datasetKey, Issue.PLACEMENT_CONFLICT));
      vm.removeIssue(datasetKey, Issue.PLACEMENT_CONFLICT);
      for (var t : flagged) {
        if (t.getVerbatimKey() != null) {
          vm.addIssue(DSID.of(datasetKey, t.getVerbatimKey()), Issue.PLACEMENT_CONFLICT);
          conflicts++;
          reindex.add(t.getId());
        }
      }
    }
    diDao.updateIssueCounts(datasetKey);
    if (!reindex.isEmpty()) {
      setStep("reindexing genera");
      indexService.update(datasetKey, reindex);
    }
  }

  @Override
  protected void onFinishLocked() throws Exception {
    // a successful job's step is cleared before onFinish runs, so the outcome has to be set here to be kept
    if (getStatus() == JobStatus.FINISHED) {
      setStep(conflicts + " of " + genera + " accepted genera with a placement conflict");
    }
  }
}
