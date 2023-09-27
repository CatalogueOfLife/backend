package life.catalogue.orthvar;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.api.model.LinneanNameUsage;
import life.catalogue.basgroup.SectorPriority;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.common.tax.SciNameNormalizer;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.matching.authorship.AuthorComparator;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

import java.util.*;
import java.util.function.Function;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class OrthographicVariationConsolidator {
  private static final Logger LOG = LoggerFactory.getLogger(OrthographicVariationConsolidator.class);
  private static final List<TaxonomicStatus> STATUS_ORDER = List.of(TaxonomicStatus.ACCEPTED, TaxonomicStatus.PROVISIONALLY_ACCEPTED, TaxonomicStatus.SYNONYM, TaxonomicStatus.AMBIGUOUS_SYNONYM);
  private static final Comparator<LinneanNameUsage> PREFERRED_STATUS_ORDER = Comparator.comparing(u -> STATUS_ORDER.indexOf(u.getStatus()));

  private final SqlSessionFactory factory;
  private final int datasetKey;
  private final DSID<String> dsid;
  private final List<SimpleName> taxa;
  private Map<String, Set<String>> exclusions = new HashMap<>();
  private final AuthorComparator authorComparator;
  private final Function<LinneanNameUsage, Integer> priorityFunc;
  private int synCounter;
  private Map<String, LinneanNameUsage> usages; // lookup by id for each taxon group being consolidated

  /**
   * @return a consolidator that will group an entire dataset family by family
   */
  public static OrthographicVariationConsolidator entireDataset(SqlSessionFactory factory, int datasetKey) {
    SectorPriority prio = new SectorPriority(datasetKey, factory);
    return OrthographicVariationConsolidator.entireDataset(factory, datasetKey, prio::priority);
  }

  public static OrthographicVariationConsolidator entireDataset(SqlSessionFactory factory, int datasetKey, Function<LinneanNameUsage, Integer> priorityFunc) {
    final List<SimpleName> families = new ArrayList<>();
    try (SqlSession session = factory.openSession(true)) {
      NameUsageMapper num = session.getMapper(NameUsageMapper.class);
      for (TaxonomicStatus status : TaxonomicStatus.values()) {
        if (status.isTaxon()) {
          families.addAll(num.findSimple(datasetKey, null, status, Rank.FAMILY, null));
        }
      }
    }
    return new OrthographicVariationConsolidator(factory, datasetKey, families, priorityFunc);
  }

  /**
   * @return a consolidator that will group names within each of the given taxa separately.
   */
  public static OrthographicVariationConsolidator forTaxa(SqlSessionFactory factory, int datasetKey, List<SimpleName> taxa) {
    SectorPriority prio = new SectorPriority(datasetKey, factory);
    return OrthographicVariationConsolidator.forTaxa(factory, datasetKey, taxa, prio::priority);
  }

  public static OrthographicVariationConsolidator forTaxa(SqlSessionFactory factory, int datasetKey, List<SimpleName> taxa, Function<LinneanNameUsage, Integer> priorityFunc) {
    return new OrthographicVariationConsolidator(factory, datasetKey, taxa, priorityFunc);
  }

  private OrthographicVariationConsolidator(SqlSessionFactory factory, int datasetKey, List<SimpleName> taxa, Function<LinneanNameUsage, Integer> priorityFunc) {
    this.factory = factory;
    this.datasetKey = datasetKey;
    this.dsid = DSID.root(datasetKey);
    this.priorityFunc = priorityFunc;
    this.taxa = taxa;
    authorComparator = new AuthorComparator(AuthorshipNormalizer.INSTANCE);
  }

  public void setExclusions(Map<String, Set<String>> exclusions) {
    this.exclusions = exclusions;
  }

  public void consolidate() {
    LOG.info("Discover orthographic variations in {} accepted taxa of dataset {}", taxa.size(), datasetKey);
    for (var tax : taxa) {
      consolidate(tax);
    }
  }

  /**
   * Goes through all usages of a given parent taxon and tries to discover basionyms by comparing the specific or infraspecific epithet and the authorships.
   * As we often see missing brackets from author names we must code defensively and allow several original names in the data for a single epithet.
   *
   * Each homotypic group is then consolidated so that only one accepted name remains.
   */
  private void consolidate(SimpleName tax) {
    synCounter = 0;
    int newBasionyms = 0;
    int newBasionymRelations = 0;
    int newHomotypicRelations = 0;
    int newSpellingRelations = 0;
    LOG.info("Detect homotypic relations within {}", tax);
    final Map<String, List<LinneanNameUsage>> epithets = Maps.newHashMap();
    final Set<String> ignore = exclusions.get(tax.getName());
    // key all names by their terminal epithet
    try (SqlSession session = factory.openSession(true)) {
      NameUsageMapper num = session.getMapper(NameUsageMapper.class);

      TreeTraversalParameter traversal = TreeTraversalParameter.dataset(datasetKey, tax.getId());
      traversal.setSynonyms(true);
      PgUtils.consume(()->num.processTree(traversal), nuBIG -> {
        // configured to be ignored?
        if (ignore != null && ignore.contains(nuBIG.getName().getTerminalEpithet())) {
          LOG.info("Ignore epithet {} in {} because of configs", nuBIG.getName().getTerminalEpithet(), tax);
        } else if (nuBIG.getName().getType() == NameType.OTU || nuBIG.getName().getRank().isSupraspecific() || nuBIG.getName().isAutonym()){
          // ignore all supra specific names, autonyms and unparsed OTUs
        } else {
          // we transform it into a smaller object as we keep quite a few of those in memory
          // consider to implelemt a native mapper method to preocess the tree
          final LinneanNameUsage nu = new LinneanNameUsage(nuBIG);
          String epithet = SciNameNormalizer.normalizeEpithet(nu.getTerminalEpithet());
          if (!epithets.containsKey(epithet)) {
            epithets.put(epithet, Lists.newArrayList(nu));
          } else {
            epithets.get(epithet).add(nu);
          }
        }
      });
      LOG.debug("{} distinct epithets found in {}", epithets.size(), tax);
    }

    // keep identity map of all usages
    usages = new HashMap<>();
    for (var lnus : epithets.values()) {
      for (var lnu : lnus) {
        usages.put(lnu.getId(), lnu);
      }
    }

    LOG.info("Discovered {} new basionym, {} homotypic and {} spelling relations. Created {} basionym placeholders and converted {} taxa into synonyms in {}", newBasionymRelations, newHomotypicRelations, newSpellingRelations, newBasionyms, synCounter, tax);
    usages = null;
  }
}
