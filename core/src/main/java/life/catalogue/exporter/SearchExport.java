package life.catalogue.exporter;

import life.catalogue.api.jackson.PermissiveEnumSerde;
import life.catalogue.api.model.*;
import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.api.util.RankUtils;
import life.catalogue.api.vocab.JobPriority;
import life.catalogue.api.vocab.terms.ClbTerm;
import life.catalogue.coldp.ColdpTerm;
import life.catalogue.common.io.CompressionUtil;
import life.catalogue.common.io.TermWriter;
import life.catalogue.common.lang.InterruptedRuntimeException;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.DatasetJob;
import life.catalogue.config.NormalizerConfig;
import life.catalogue.es.search.NameUsageSearchService;
import life.catalogue.metadata.coldp.DatasetYamlWriter;

import org.gbif.dwc.terms.Term;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Background job that exports the result of a name usage search as a ColDP archive.
 *
 * Unlike the regular dataset exports this reads exclusively from Elasticsearch, streaming the entire
 * search result in a single scrolled pass into a ColDP NameUsage.tsv. The archive also contains the
 * datasets metadata.yaml and a human readable README.md describing the executed search, the dataset
 * citation and version, and a repeatable link back to the search in ChecklistBank.
 */
public class SearchExport extends DatasetJob {
  private static final Logger LOG = LoggerFactory.getLogger(SearchExport.class);
  private static final String METADATA_FILENAME = "metadata.yaml";
  private static final String README_FILENAME = "README.md";
  private static final int BATCH_SIZE = 1000;

  /** The ColDP NameUsage columns plus the custom clb:merged flag, as used by the extended ColDP export. */
  static final List<Term> COLUMNS;
  static {
    var cols = new ArrayList<Term>(ColdpTerm.RESOURCES.get(ColdpTerm.NameUsage));
    cols.add(ClbTerm.merged);
    COLUMNS = List.copyOf(cols);
  }

  private final NameUsageSearchRequest searchRequest;
  private final NameUsageSearchService searchService;
  private final URI clbURI;
  private final JobResult result;
  private final File tmpDir;
  private int counter = 0;

  public SearchExport(int datasetKey, NameUsageSearchRequest searchRequest, int userKey,
                      NameUsageSearchService searchService, SqlSessionFactory factory,
                      NormalizerConfig nCfg, URI clbURI) {
    super(datasetKey, userKey, JobPriority.LOW);
    this.logToFile = true;
    this.searchRequest = scopedRequest(datasetKey, searchRequest);
    this.searchService = searchService;
    this.clbURI = clbURI;
    this.result = new JobResult(getKey());
    this.tmpDir = new File(nCfg.scratchDir, "search-export/" + getKey());
    this.dataset = loadDataset(factory, datasetKey);
  }

  /**
   * Returns a copy of the search request that is always scoped to the given dataset.
   */
  private static NameUsageSearchRequest scopedRequest(int datasetKey, NameUsageSearchRequest req) {
    NameUsageSearchRequest copy = req == null ? new NameUsageSearchRequest() : req.copy();
    copy.getFilters().remove(NameUsageSearchParameter.DATASET_KEY);
    copy.addFilter(NameUsageSearchParameter.DATASET_KEY, datasetKey);
    return copy;
  }

  public NameUsageSearchRequest getSearchRequest() {
    return searchRequest;
  }

  @Override
  public String getEmailTemplatePrefix() {
    return "searchexport";
  }

  @Override
  public Object getParams() {
    return searchRequest;
  }

  @Override
  public JobResult getResult() {
    return result;
  }

  @Override
  public boolean isDuplicate(BackgroundJob other) {
    if (other instanceof SearchExport) {
      SearchExport o = (SearchExport) other;
      return getUserKey() == o.getUserKey() && Objects.equals(searchRequest, o.searchRequest);
    }
    return false;
  }

  @Override
  public Class<? extends BackgroundJob> maxPerUserClass() {
    return SearchExport.class;
  }

  @Override
  public void execute() throws Exception {
    super.execute(); // sets the dataset MDC
    FileUtils.forceMkdir(tmpDir);
    try {
      setStep("processing");
      writeData();
      writeMetadata();
      writeReadme();

      setStep("archiving");
      File archive = result.getFile();
      FileUtils.forceMkdir(archive.getParentFile());
      LOG.info("Bundling search export archive for dataset {} with {} usages to {}", datasetKey, counter, archive);
      CompressionUtil.zipDir(tmpDir, archive, true);
      result.calculateSizeAndMd5();
      LOG.info("Search export {} of dataset {} completed: {} [{}]", getKey(), datasetKey, archive, result.getSizeWithUnit());

    } finally {
      try {
        FileUtils.deleteDirectory(tmpDir);
      } catch (IOException e) {
        LOG.warn("Failed to remove temporary search export directory {}", tmpDir, e);
      }
    }
  }

  /**
   * Streams the entire search result from Elasticsearch into a ColDP NameUsage.tsv.
   */
  private void writeData() throws Exception {
    try (TermWriter tw = new TermWriter.TSV(tmpDir, ColdpTerm.NameUsage, COLUMNS)) {
      searchService.scroll(searchRequest, BATCH_SIZE, nuw -> {
        // surface a user cancellation as an unchecked exception that aborts the scroll
        if (Thread.currentThread().isInterrupted()) {
          throw new InterruptedRuntimeException("Search export " + getKey() + " was cancelled");
        }
        try {
          write(tw, nuw);
          tw.next();
          if (++counter % 100_000 == 0) {
            LOG.info("Exported {} usages from search of dataset {}", counter, datasetKey);
          }
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      });
    } catch (InterruptedRuntimeException e) {
      throw e.asChecked();
    }
    LOG.info("Exported {} usages in total from search of dataset {}", counter, datasetKey);
  }

  /**
   * Maps a single search hit into a rich ColDP NameUsage row, mirroring the extended ColDP export.
   * The merge source dataset and merged flag come straight from the indexed sector info.
   */
  private void write(TermWriter tw, NameUsageWrapper nuw) {
    NameUsage nu = nuw.getUsage();
    if (nu == null) {
      return;
    }
    final Name n = nu.getName();
    // name fields
    if (n != null) {
      tw.set(ColdpTerm.scientificName, n.getScientificName());
      tw.set(ColdpTerm.authorship, n.getAuthorship());
      tw.set(ColdpTerm.rank, n.getRank());
      tw.set(ColdpTerm.notho, n.getNotho());
      tw.set(ColdpTerm.uninomial, n.getUninomial());
      tw.set(ColdpTerm.genericName, n.getGenus());
      tw.set(ColdpTerm.infragenericEpithet, n.getInfragenericEpithet());
      tw.set(ColdpTerm.specificEpithet, n.getSpecificEpithet());
      tw.set(ColdpTerm.infraspecificEpithet, n.getInfraspecificEpithet());
      tw.set(ColdpTerm.cultivarEpithet, n.getCultivarEpithet());
      var ca = n.getCombinationAuthorship();
      if (ca != null) {
        tw.set(ColdpTerm.combinationAuthorship, ca.getAuthors(), "|");
        tw.set(ColdpTerm.combinationExAuthorship, ca.getExAuthors(), "|");
        tw.set(ColdpTerm.combinationAuthorshipYear, ca.getYear());
      }
      var ba = n.getBasionymAuthorship();
      if (ba != null) {
        tw.set(ColdpTerm.basionymAuthorship, ba.getAuthors(), "|");
        tw.set(ColdpTerm.basionymExAuthorship, ba.getExAuthors(), "|");
        tw.set(ColdpTerm.basionymAuthorshipYear, ba.getYear());
      }
      tw.set(ColdpTerm.originalSpelling, n.isOriginalSpelling());
      tw.set(ColdpTerm.genderAgreement, n.hasGenderAgreement());
      tw.set(ColdpTerm.gender, n.getGender());
      tw.set(ColdpTerm.etymology, n.getEtymology());
      tw.set(ColdpTerm.nameReferenceID, n.getPublishedInId());
      tw.set(ColdpTerm.namePublishedInYear, n.getPublishedInYear());
      tw.set(ColdpTerm.namePublishedInPage, n.getPublishedInPage());
      tw.set(ColdpTerm.namePublishedInPageLink, n.getPublishedInPageLink());
      tw.set(ColdpTerm.code, n.getCode());
      tw.set(ColdpTerm.nameStatus, n.getNomStatus());
      tw.set(ColdpTerm.nameRemarks, n.getRemarks());
    }
    // usage fields available on the NameUsage interface
    tw.set(ColdpTerm.ID, nu.getId());
    tw.set(ColdpTerm.sourceID, nuw.getSectorDatasetKey());
    tw.set(ColdpTerm.status, nu.getStatus());
    tw.set(ColdpTerm.namePhrase, nu.getNamePhrase());
    tw.set(ColdpTerm.accordingToID, nu.getAccordingToId());
    tw.set(ColdpTerm.remarks, nu.getRemarks());

    if (nu instanceof NameUsageBase nub) {
      tw.set(ColdpTerm.parentID, nub.getParentId());
      tw.set(ColdpTerm.referenceID, nub.getReferenceIds());
      tw.set(ColdpTerm.link, nub.getLink());
      tw.set(ClbTerm.merged, nub.isMerged());
    }
    if (nu instanceof Taxon t) {
      tw.set(ColdpTerm.scrutinizer, t.getScrutinizer());
      tw.set(ColdpTerm.scrutinizerID, t.getScrutinizerID());
      tw.set(ColdpTerm.scrutinizerDate, t.getScrutinizerDate());
      tw.set(ColdpTerm.extinct, t.isExtinct());
      tw.set(ColdpTerm.temporalRangeStart, t.getTemporalRangeStart());
      tw.set(ColdpTerm.temporalRangeEnd, t.getTemporalRangeEnd());
      tw.set(ColdpTerm.environment, t.getEnvironments(), PermissiveEnumSerde::enumValueName);
      tw.set(ColdpTerm.ordinal, t.getOrdinal());
    }
    // denormalized higher classification from the indexed wrapper
    if (nuw.getClassification() != null) {
      for (SimpleName ht : nuw.getClassification()) {
        var term = RankUtils.RANK2COLDP.get(ht.getRank());
        if (term != null) {
          tw.set(term, ht.getName());
        }
      }
    }
  }

  private void writeMetadata() throws IOException {
    LOG.info("Adding {}", METADATA_FILENAME);
    DatasetYamlWriter.write(dataset, new File(tmpDir, METADATA_FILENAME));
  }

  private void writeReadme() throws IOException {
    SearchExportReadme.write(new File(tmpDir, README_FILENAME), dataset, searchRequest, clbURI, counter, getCreated());
  }
}
