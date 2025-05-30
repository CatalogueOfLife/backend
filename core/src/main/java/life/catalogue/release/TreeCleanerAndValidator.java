package life.catalogue.release;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.IssueContainer;
import life.catalogue.api.model.LinneanNameUsage;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.Issue;
import life.catalogue.assembly.TreeMergeHandler;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.VerbatimSourceMapper;
import life.catalogue.matching.NameValidator;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consumer of an entire tree in depth first order (!) of accepted names with or without synonyms, validates taxa and resolves data.
 * It tracks the parent classification as it goes and makes it available for validations.
 * In particular this is:
 *
 *  1) all of the parsed NameValidator flags
 *
 *  2) flag parent name mismatches
 * Goes through all accepted species and infraspecies and makes sure the name matches the genus, species classification.
 * For example an accepted species Picea alba with a parent genus of Abies is imperfect, but as a result of homotypic grouping
 * and unresolved taxonomic word in sources a reality.
 *
 * Badly classified names are assigned the doubtful status and an NameUsageIssue.NAME_PARENT_MISMATCH is flagged
 *
 *  3) add missing autonyms if needed
 *
 *  4) remove empty genera generated in the xrelease (can be configured to be skipped)
 *
 *  5) flag species that have been described before the genus was published
 *
 */
public class TreeCleanerAndValidator implements Consumer<LinneanNameUsage>, AutoCloseable {
  static final Logger LOG = LoggerFactory.getLogger(TreeCleanerAndValidator.class);

  final SqlSessionFactory factory;
  final int datasetKey;
  final ParentStack<XLinneanNameUsage> parents;
  private final AtomicInteger counter = new AtomicInteger(0);
  private final AtomicInteger flagged = new AtomicInteger(0);
  private int maxDepth = 0;

  public TreeCleanerAndValidator(SqlSessionFactory factory, int datasetKey, boolean removeEmptyGenera) {
    this.factory = factory;
    this.datasetKey = datasetKey;
    this.parents = new ParentStack<>();
    if (removeEmptyGenera) {
      // add stack handler that considers to remove empty genera and creates missing autonyms
      parents.addHandler(new ParentStack.StackHandler<>() {
        @Override
        public void start(XLinneanNameUsage n) {
          // nothing
        }
        @Override
        public void end(ParentStack.SNC<XLinneanNameUsage> taxon) {
          if (taxon.usage.getRank().isGenusGroup() && taxon.children == 0 && fromXSource(taxon.usage)) {
            LOG.info("Remove empty {}", taxon.usage);
            final var key = DSID.of(datasetKey, taxon.usage.getId());
            try (SqlSession session = factory.openSession(true)) {
              var vm = session.getMapper(VerbatimSourceMapper.class);
              var um = session.getMapper(NameUsageMapper.class);
              // first remove all synonyms
              for (var c : um.childrenIds(key)) {
                vm.delete(key.id(c));
                um.delete(key);
              }
              vm.delete(key.id(taxon.usage.getId()));
              um.delete(key);
              // names, references and related are removed as orphans at the end of the release
            }
          }
        }
      });
    }
    // add stack handler that counts species and flags all supraspecific taxa without species
    // see also an alternative implementation in PgImport.insertUsages()
    // which gets applied to external dataset during imports
    parents.addHandler(new ParentStack.StackHandler<>() {
      @Override
      public void start(XLinneanNameUsage n) {
        if (n.getRank() == Rank.SPECIES) {
          parents.getParents(false).forEach(p -> p.numSpecies++);
        }
      }
      @Override
      public void end(ParentStack.SNC<XLinneanNameUsage> taxon) {
        if (taxon.usage.getRank().higherThan(Rank.SPECIES_AGGREGATE) && taxon.usage.numSpecies == 0) {
          LOG.debug("Flag taxon without species: {}", taxon.usage);
          final var key = DSID.of(datasetKey, taxon.usage.getId());
          try (SqlSession session = factory.openSession(true)) {
            var vm = session.getMapper(VerbatimSourceMapper.class);
            vm.addIssue(key, Issue.NO_SPECIES_INCLUDED);
          }
        }
      }
    });
  }

  ParentStack<XLinneanNameUsage> stack() {
    return parents;
  }

  public static class XLinneanNameUsage extends LinneanNameUsage {
    Integer authorYear;
    int numSpecies = 0;
    public XLinneanNameUsage(LinneanNameUsage u) {
      super(u);
    }
  }

  private boolean fromXSource(LinneanNameUsage sn) {
    return sn.getId().charAt(0) == TreeMergeHandler.ID_PREFIX; // a temp merge identifier!
  }

  @Override
  public void accept(LinneanNameUsage lnu) {
    var sn = new XLinneanNameUsage(lnu);
    counter.incrementAndGet();
    final IssueContainer issues = IssueContainer.simple();
    // main parsed name validation
    NameValidator.flagIssues(sn, issues);
    try {
      sn.authorYear = NameValidator.parseYear(sn);
    } catch (NumberFormatException e) {
      // already flagged by name validator above!
    }

    if (sn.getStatus() != null && sn.getStatus().isSynonym()) {
      // validate syn vs acc rank
      var p = parents.secondLast();
      if (p != null && sn.getRank() != p.getRank()) {
        issues.add(Issue.SYNONYM_RANK_DIFFERS);
      }

    } else {
      if (sn.getRank().isSpeciesOrBelow()) {
        // flag parent mismatches
        if (sn.isParsed()) {
          var genus = parents.getByRank(Rank.GENUS);
          if (sn.getRank().isInfraspecific()) {
            // we have a trinomial, compare species
            var sp = parents.getByRank(Rank.SPECIES);
            if (sp == null) {
              issues.add(Issue.PARENT_SPECIES_MISSING);
            } else if (sp.isParsed() && (
                !Objects.equals(sn.getGenus(), sp.getGenus()) ||
                !Objects.equals(sn.getSpecificEpithet(), sp.getSpecificEpithet()))
            ) {
              issues.add(Issue.PARENT_NAME_MISMATCH);
            }
          } else {
            // we have a binomial, compare genus only
            if (genus == null) {
              issues.add(Issue.PARENT_GENUS_MISSING);
            } else if (genus.isParsed() &&
                // genus should only have uninomial populated, but play safe here
                !Objects.equals(sn.getGenus(), ObjectUtils.coalesce(genus.getUninomial(),genus.getGenus()))
            ) {
              issues.add(Issue.PARENT_NAME_MISMATCH);
            }
          }
          // flag if published before the genus
          if (!issues.contains(Issue.PARENT_NAME_MISMATCH)
              && !issues.contains(Issue.MISSING_GENUS)
              && !issues.contains(Issue.UNLIKELY_YEAR)
              && genus != null && genus.authorYear != null
              && sn.authorYear != null
              && genus.authorYear > sn.authorYear
          ) {
              // flag if the accepted bi/trinomial the ones that have an earlier publication date!
              issues.add(Issue.PUBLISHED_BEFORE_GENUS);
          }
        }
      }
      parents.push(sn);
      // validate next higher concrete parent rank
      if (!sn.getRank().isUncomparable()) {
        parents.getLowestConcreteRank(true).ifPresent(r -> {
          if (r.lowerOrEqualsTo(sn.getRank()) && sn.getType() != NameType.OTU) {
            issues.add(Issue.CLASSIFICATION_RANK_ORDER_INVALID);
          }
        });
      }
      // track maximum depth of accepted taxa
      if (maxDepth < parents.depth()) {
        maxDepth = parents.depth();
      }
    }
    // persist if we have flagged issues
    if (issues.hasIssues()) {
      try (SqlSession session = factory.openSession(true)) {
        var vsm = session.getMapper(VerbatimSourceMapper.class);
        vsm.addIssues(dsid(sn), issues.getIssues());
        flagged.incrementAndGet();
      }
    }
  }

  DSID<String> dsid(LinneanNameUsage u){
    return DSID.of(datasetKey, u.getId());
  }

  public int getCounter() {
    return counter.get();
  }

  public int getFlagged() {
    return flagged.get();
  }

  public int getMaxDepth() {
    return maxDepth;
  }

  @Override
  public void close() throws IOException {
    // nothing so far
  }
}
