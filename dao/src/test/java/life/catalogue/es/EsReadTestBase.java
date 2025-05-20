package life.catalogue.es;

import life.catalogue.api.TestEntityUnmodifiedRule;
import life.catalogue.api.model.*;
import life.catalogue.api.search.*;
import life.catalogue.api.search.NameUsageSearchRequest.SortBy;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.common.kryo.ApiKryoPool;
import life.catalogue.es.nu.NameUsageWrapperConverter;
import life.catalogue.es.nu.search.NameUsageSearchServiceEs;
import life.catalogue.es.nu.suggest.NameUsageSuggestionServiceEs;
import life.catalogue.es.query.EsSearchRequest;
import life.catalogue.es.query.Query;

import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.elasticsearch.client.RestClient;
import org.junit.ClassRule;
import org.junit.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esotericsoftware.kryo.Kryo;
import com.google.common.base.Preconditions;

import static java.util.stream.Collectors.toList;

/**
 * Base class for tests that only read from ES. Does not provide postgres functionality and saves setup/initialization time accordingly.
 */
public class EsReadTestBase {

  final static Kryo kryo = ApiKryoPool.configure(new Kryo());
  static {
    kryo.register(NameUsageWrapper.class);
  }

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(EsReadTestBase.class);

  @ClassRule
  public static EsSetupRule esSetupRule = new EsSetupRule();

  @Rule
  public final TestEntityUnmodifiedRule unomidifedRule = new TestEntityUnmodifiedRule();

  protected EsConfig getEsConfig() {
    return esSetupRule.getEsConfig();
  }

  protected RestClient getEsClient() {
    return esSetupRule.getClient();
  }

  // Useful for @Before or @BeforeClass methods
  protected static void destroyAndCreateIndex() {
    try {
      EsUtil.deleteIndex(esSetupRule.getClient(), esSetupRule.getEsConfig().nameUsage);
      EsUtil.createIndex(esSetupRule.getClient(), EsNameUsage.class, esSetupRule.getEsConfig().nameUsage);
    } catch (IOException e) {
      throw new EsException(e);
    }
  }

  public IndexConfig index() {
    return esSetupRule.getEsConfig().nameUsage;
  }

  public String indexName() {
    return index().name;
  }

  protected void indexRaw(Collection<EsNameUsage> documents) {
    try {
      for (EsNameUsage doc : documents) {
        EsUtil.insert(getEsClient(), indexName(), doc);
      }
      EsUtil.refreshIndex(getEsClient(), indexName());
    } catch (IOException e) {
      throw new EsException(e);
    }
  }

  protected NameUsageWrapper indexNewTaxon(Rank rank, String uninomial, String authorship) {
    Preconditions.checkArgument(rank.isGenusOrSuprageneric());
    return indexNewTaxon(rank, uninomial, null, authorship);
  }

  protected NameUsageWrapper indexNewTaxon(Rank rank, String genus, String species, String authorship) {
    Name n = new Name();
    n.setRank(rank);
    if (rank.isGenusOrSuprageneric()) {
      n.setUninomial(genus);
    } else {
      n.setGenus(genus);
      n.setSpecificEpithet(species);
    }
    n.setAuthorship(authorship);
    n.rebuildScientificName();
    NameUsageWrapper nuw = new NameUsageWrapper(new Taxon(n));
    index(nuw);
    return nuw;
  }

  protected void indexRaw(EsNameUsage... documents) {
    indexRaw(Arrays.asList(documents));
  }

  protected List<EsNameUsage> queryRaw(Query query) {
    EsSearchRequest esr = EsSearchRequest.emptyRequest().where(query);
    return new NameUsageSearchServiceEs(indexName(), getEsClient()).getDocuments(esr);
  }

  protected List<EsNameUsage> queryRaw(EsSearchRequest rawRequest) {
    return new NameUsageSearchServiceEs(indexName(), getEsClient()).getDocuments(rawRequest);
  }

  protected EsNameUsage toDocument(NameUsageWrapper nu) {
    try {
      // create copy of content as the converter modifies the original instance
      return NameUsageWrapperConverter.toDocument(deepCopy(nu));
    } catch (Exception e) {
      throw new EsException(e);
    }
  }

  public static <T> T deepCopy(T obj) {
    return kryo.copy(obj);
  }
  protected List<EsNameUsage> toDocuments(Collection<NameUsageWrapper> nameUsages) {
    return nameUsages.stream().map(this::toDocument).collect(Collectors.toList());
  }

  protected NameUsageWrapper index(NameUsageWrapper nameUsage) {
    indexRaw(toDocument(nameUsage));
    return nameUsage;
  }

  protected void index(NameUsageWrapper... nameUsages) {
    indexRaw(toDocuments(Arrays.asList(nameUsages)));
  }

  protected void index(Collection<NameUsageWrapper> nameUsages) {
    indexRaw(toDocuments(nameUsages));
  }

  protected NameUsageSearchResponse search(NameUsageSearchRequest query) {
    if (query.getSortBy() == null) {
      // Unless we're specifically interested in sorting, sort the way we inserted the documents
      query.setSortBy(SortBy.NATIVE);
    }
    return new NameUsageSearchServiceEs(indexName(), getEsClient()).search(query, new Page(0, 1000));
  }

  protected NameUsageSuggestResponse suggest(NameUsageSuggestRequest query) {
    return new NameUsageSuggestionServiceEs(indexName(), getEsClient()).suggest(query);
  }

  protected EsNameUsage newDocument(Name n) {
    return newDocument(n, TaxonomicStatus.ACCEPTED);
  }

  protected EsNameUsage newDocument(Name n, TaxonomicStatus status, String... classification) {
    return newDocumentCL(n, status, classification(classification));
  }

  protected EsNameUsage newDocumentCL(Name n, TaxonomicStatus status, List<EsMonomial> classification) {
    EsNameUsage doc = new EsNameUsage();
    doc.setUsageId(n.getId());
    doc.setDatasetKey(n.getDatasetKey());
    doc.setScientificName(n.getScientificName());
    doc.setNameStrings(new NameStrings(n));
    doc.setDatasetKey(n.getDatasetKey());
    doc.setStatus(status);
    doc.setClassification(classification);
    return doc;
  }

  protected static List<EsMonomial> classification(String... names) {
    return Arrays.stream(names).map(n -> new EsMonomial(null, n)).collect(Collectors.toList());
  }

  /**
   * Creates the requested number of name usages (all taxa) with just enough fields set to be indexed straight away without NPEs.
   * 
   * @param howmany
   * @return
   */
  protected List<NameUsageWrapper> createNameUsages(int howmany) {
    return IntStream.rangeClosed(1, howmany).mapToObj(i -> minimalTaxon()).collect(toList());
  }

  protected NameUsageWrapper minimalTaxon(Rank rank, String scientificName, String authorship) {
    var n = new Name();
    n.setScientificName(scientificName);
    n.setAuthorship(authorship);
    n.setRank(rank);
    Taxon t = new Taxon();
    t.setName(n);
    NameUsageWrapper nuw = new NameUsageWrapper();
    nuw.setUsage(t);
    return nuw;
  }

  /**
   * Creates a new Taxon instance with just enough fields set to be indexed straight away without NPEs.
   * 
   * @return
   */
  protected NameUsageWrapper minimalTaxon() {
    Taxon t = new Taxon();
    t.setName(new Name());
    NameUsageWrapper nuw = new NameUsageWrapper();
    nuw.setUsage(t);
    return nuw;
  }

  protected NameUsageWrapper minimalSynonym() {
    Synonym s = new Synonym();
    s.setName(new Name());
    s.setAccepted((Taxon) minimalTaxon().getUsage());
    NameUsageWrapper nuw = new NameUsageWrapper();
    nuw.setUsage(s);
    return nuw;
  }

  protected List<VernacularName> create(List<String> names) {
    return names.stream().map(n -> {
      VernacularName vn = new VernacularName();
      vn.setName(n);
      return vn;
    }).collect(Collectors.toList());
  }
}
