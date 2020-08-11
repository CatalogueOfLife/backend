package life.catalogue.release;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.ints.*;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.Setting;
import life.catalogue.common.id.IdConverter;
import life.catalogue.common.text.SimpleTemplate;
import life.catalogue.common.text.StringUtils;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.db.mapper.*;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.img.ImageService;
import life.catalogue.matching.NameIndex;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class ProjectRelease extends AbstractProjectCopy {
  private static final String DEFAULT_TITLE_TEMPLATE = "{title}, {date}";
  private static final String DEFAULT_ALIAS_TEMPLATE = "{alias}-{date}";
  private static final String DEFAULT_CITATION_TEMPLATE = "{citation} released on {date}";

  private final ImageService imageService;
  private final NameIndex nameIndex;
  private final AtomicInteger keySequence = new AtomicInteger();
  // names index ids to list of usage ids (converted to ints) of previous release
  private Int2ObjectMap<IntArrayList> prevIds; // just the ids that are not part of the project already!!!
  private Int2ObjectMap<IntArrayList> prevDel; // ids that existed in any previous release, but was since deleted and not part of the previous release
  private Int2IntMap id2dataset = new Int2IntOpenHashMap(); // maps existing ids to their last used release datasetKey
  // reverse map of ID to names index ids, but to save memory only for the few IDs that have more than one index id!
  private Int2ObjectMap<IntArrayList> id2Nidx;
  // id changes in this release
  private IntSet added = new IntOpenHashSet();
  private IntSet deleted = new IntOpenHashSet();
  private IntSet resurrected = new IntOpenHashSet();
  private NameUsageMapper num;

  ProjectRelease(SqlSessionFactory factory, NameIndex nameIndex, NameUsageIndexService indexService, DatasetImportDao diDao, ImageService imageService,
                 int datasetKey, Dataset release, int userKey) {
    super("releasing", factory, diDao, indexService, userKey, datasetKey, release);
    this.imageService = imageService;
    this.nameIndex = nameIndex;
  }

  @Override
  void prepWork() throws Exception {
    // map ids
    updateState(ImportState.MATCHING);
    matchUnmatchedNames();
    mapIds();
    // archive dataset metadata & logos
    try (SqlSession session = factory.openSession(true)) {
      ProjectSourceMapper psm = session.getMapper(ProjectSourceMapper.class);
      DatasetPatchMapper dpm = session.getMapper(DatasetPatchMapper.class);
      final AtomicInteger counter = new AtomicInteger(0);
      psm.processDataset(datasetKey).forEach(d -> {
        DatasetMetadata patch = dpm.get(datasetKey, d.getKey());
        if (patch != null) {
          LOG.debug("Apply dataset patch from project {} to {}: {}", datasetKey, d.getKey(), d.getTitle());
          d.apply(patch);
        }
        d.setDatasetKey(newDatasetKey);
        LOG.debug("Archive dataset {}: {} for release {}", d.getKey(), d.getTitle(), newDatasetKey);
        psm.create(d);
        // archive logos
        try {
          imageService.archiveDatasetLogo(newDatasetKey, d.getKey());
        } catch (IOException e) {
          LOG.warn("Failed to archive logo for source dataset {} of release {}", d.getKey(), newDatasetKey);
        }
        counter.incrementAndGet();
      });
      LOG.info("Archived metadata for {} source datasets of release {}", counter.get(), newDatasetKey);
    }

    // create new dataset "import" metrics in mother project
    updateState(ImportState.ANALYZING);
    metrics();
  }

  /**
   * Makes sure all names are matched to the names index.
   * When syncing names from other sources the names index match is carried over
   * so there should really not be any name without a match.
   * We still make sure here that at least there is no such case in releases.
   */
  private void matchUnmatchedNames() {
    try (SqlSession session = factory.openSession(false)) {
      AtomicInteger counter = new AtomicInteger();
      NameMapper nm = session.getMapper(NameMapper.class);
      nm.processUnmatched(datasetKey).forEach(n -> {
        if (n.getNameIndexMatchType() == null || n.getNameIndexMatchType() == MatchType.NONE || n.getNameIndexIds().isEmpty()) {
          NameMatch match = nameIndex.match(n, true, false);
          nm.updateMatch(datasetKey, n.getId(), match.getNameIds(), match.getType());
          if (counter.getAndIncrement() % 1000 == 0) {
            session.commit();
          }
        }
      });
      session.commit();
    }
  }

  private void metrics() {
    LOG.info("Build import metrics for dataset " + datasetKey);
    diDao.updateMetrics(metrics);
    diDao.update(metrics);
    diDao.updateDatasetLastAttempt(metrics);

    // also update release datasets import attempt pointer
    try (SqlSession session = factory.openSession(true)) {
      session.getMapper(DatasetMapper.class).updateLastImport(newDatasetKey, metrics.getAttempt());
    }
  }

  private static String procTemplate(Dataset d, DatasetSettings ds, Setting setting, String defaultTemplate){
    String tmpl = defaultTemplate;
    if (ds.has(setting)) {
      tmpl = ds.getString(setting);
    }
    return SimpleTemplate.render(tmpl, d);
  }

  public static void releaseInit(Dataset d, DatasetSettings ds) {
    String title = procTemplate(d, ds, Setting.RELEASE_TITLE_TEMPLATE, DEFAULT_TITLE_TEMPLATE);
    d.setTitle(title);

    String alias = procTemplate(d, ds, Setting.RELEASE_ALIAS_TEMPLATE, DEFAULT_ALIAS_TEMPLATE);
    d.setAlias(alias);

    String citation = procTemplate(d, ds, Setting.RELEASE_CITATION_TEMPLATE, DEFAULT_CITATION_TEMPLATE);
    d.setCitation(citation);

    d.setOrigin(DatasetOrigin.RELEASED);
    final LocalDate today = LocalDate.now();
    d.setReleased(today);
    d.setVersion(today.toString());
    d.setCitation(buildCitation(d));
  }

  @VisibleForTesting
  protected static String buildCitation(Dataset d){
    // ${d.authorsAndEditors?join(", ")}, eds. (${d.released.format('yyyy')}). ${d.title}, ${d.released.format('yyyy-MM-dd')}. Digital resource at www.catalogueoflife.org/col. Species 2000: Naturalis, Leiden, the Netherlands. ISSN 2405-8858.
    StringBuilder sb = new StringBuilder();
    for (String au : d.getAuthorsAndEditors()) {
      if (sb.length() > 1) {
        sb.append(", ");
      }
      sb.append(au);
    }
    sb.append(" (")
      .append(d.getReleased().getYear())
      .append("). ")
      .append(d.getTitle())
      .append(", ")
      .append(d.getReleased().toString())
      .append(". Digital resource at www.catalogueoflife.org/col. Species 2000: Naturalis, Leiden, the Netherlands. ISSN 2405-8858.");
    return sb.toString();
  }

  /**
   * Maps name usage ids, replacing temporary UUIDs with new or ids from previously existing releases.
   * Proper IDs are left untouched even if the classification has changed.
   */
  private void mapIds() {
    LOG.info("Map name usage IDs");
    // TODO: load prev releases via NameUsageIndexIds
    // just the ids that are not already part of the project!!!
    prevIds = new Int2ObjectOpenHashMap<>();
    prevDel = new Int2ObjectOpenHashMap<>();
    // keep reverse map of ID to names index ids, but to save memory only for the few IDs that have more than one index id!
    id2Nidx = new Int2ObjectOpenHashMap<>();
    for (Int2ObjectMap.Entry<IntArrayList> x : prevIds.int2ObjectEntrySet()) {
    }
    // TODO: populate id2dataset
    id2dataset.clear();
    // TODO: retrieve max of all usage ids ever issued
    // max has the longest id.
    // check non temp ids from curr project, then also check all ids from the previous (curr & deleted)
    keySequence.set(1000);

    // we keep a list of usages that have ambiguous matches to mulitple index names
    // and deal with those names last.
    AtomicInteger counter = new AtomicInteger();
    List<SimpleNameWithNidx> ambiguous = new ArrayList<>();
    DSID<String> key = DSID.of(datasetKey, "");
    try (SqlSession session = factory.openSession(false)) {
      num = session.getMapper(NameUsageMapper.class);
      num.processTemporary(datasetKey).forEach(u -> {
        if (u.getNameIndexIds().isEmpty()) {
          LOG.debug("Usage with no name match id - keep the temporary id");
        } else if (u.getNameIndexIds().size() == 1) {
          String id = issueID(u, u.getNameIndexIds().toIntArray());
          num.updateId(key.id(u.getId()), id);
          if (counter.incrementAndGet() % 1000 == 0) {
            session.commit();
          }
        } else {
          ambiguous.add(u);
        }
      });
      session.commit();

      LOG.info("Updated {} temporary ids for simple matches, doing {} ambiguous matches next", counter, ambiguous.size());
      for (SimpleNameWithNidx u : ambiguous){
        String id = issueID(u, u.getNameIndexIds().toIntArray());
        num.updateId(key.id(u.getId()), id);
        if (counter.incrementAndGet() % 1000 == 0) {
          session.commit();
        }
      }
      session.commit();
    }
  }


  private String issueID(SimpleName name, int... nidxs) {
    int id;
    // does a previous id match?
    for (int nidx : nidxs) {
      if (prevIds.containsKey(nidx)) {
        id = selectID(prevIds.get(nidx), name);
        if (id > 0) {
          removeID(id, nidx);
          return encode(id);
        }
      }
    }
    // if not, does a previously deleted id match?
    for (int nidx : nidxs) {
      if (prevDel.containsKey(nidx)) {
        id = selectID(prevDel.get(nidx), name);
        if (id > 0) {
          removeID(id, nidx);
          resurrected.add(id);
          return encode(id);
        }
      }
    }
    // if not, issue a new id
    id = keySequence.incrementAndGet();
    added.add(id);
    return encode(id);
  }

  /**
   * Removes an CoL ID from the previous maps so its not issued again in this release
   */
  private void removeID(int id, int nidx){
    _removeID(id, nidx);
    // do we have more index ids associated with the same id that we need to remove?
    if (id2Nidx.containsKey(id)) {
      for (int nidx2 : id2Nidx.get(id)){
        if (nidx == nidx2) continue;
        _removeID(id, nidx2);
      }
      id2Nidx.remove(id);
    }
  }
  private void _removeID(int id, int nidx){
    if (prevIds.containsKey(nidx)) {
      prevIds.get(nidx).removeInt(id);
    }
    if (prevDel.containsKey(nidx)) {
      prevDel.get(nidx).removeInt(id);
    }
  }

  /**
   * Takes several existing ids as candidates and checks whether one of matches the given simple name.
   * It is assumed all ids match the canonical name at least.
   * Selecting considers authorship and the parent name to select between multiple candidates.
   *
   * @param ids id candidates from any previous release (but not from the current project)
   * @param name the name to match against
   * @return the matched id or -1 for no match
   */
  private int selectID(IntCollection ids, SimpleName name) {
    if (ids.size() == 1) return ids.iterator().nextInt();
    // select best id from multiple
    int max = 0;
    IntSet best = new IntOpenHashSet();
    for (int id : ids) {
      DSID<String> key = DSID.of(id2dataset.get(id), encode(id));
      SimpleName sn = num.getSimple(key);
      int score = 0;
      // author 0-2
      if (name.getAuthorship() != null && Objects.equals(name.getAuthorship(), sn.getAuthorship())) {
        score += 2;
      } else if (StringUtils.equalsIgnoreCaseAndSpace(name.getAuthorship(), sn.getAuthorship())) {
        score += 1;
      }
      // parent 0-2
      if (name.getParent() != null && Objects.equals(name.getParent(), sn.getParent())) {
        score += 2;
      }
      if (score > max) {
        max = score;
        best.clear();
        best.add(id);
      } else if (score == max) {
        best.add(id);
      }
    }
    if (max > 0) {
      if (best.size() > 1) {
        LOG.debug("{} ids all matching to {}", Arrays.toString(best.toArray()), name);
      }
      return best.iterator().nextInt();
    }
    return -1;
  }

  static int decode(String id) {
    return IdConverter.LATIN32.decode(id);
  }

  static String encode(int id) {
    return IdConverter.LATIN32.encode(id);
  }

}
