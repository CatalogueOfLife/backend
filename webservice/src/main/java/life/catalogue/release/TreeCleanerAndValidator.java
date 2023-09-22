package life.catalogue.release;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.IssueContainer;
import life.catalogue.api.model.SimpleNameUsage;
import life.catalogue.api.vocab.Issue;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.VerbatimSourceMapper;
import life.catalogue.importer.neo.traverse.Traversals;
import life.catalogue.matching.NameValidator;

import org.gbif.api.vocabulary.NameUsageIssue;
import org.gbif.nameparser.api.Rank;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.neo4j.graphdb.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consumer of an entire tree of accepted names, validates taxa and resolves data.
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
public class TreeCleanerAndValidator implements Consumer<SimpleNameUsage> {
  static final Logger LOG = LoggerFactory.getLogger(TreeCleanerAndValidator.class);

  final SqlSessionFactory factory;
  final int datasetKey;
  final ParentStack<SimpleNameUsage> parents;

  private SimpleNameUsage genus;

  public TreeCleanerAndValidator(SqlSessionFactory factory, int datasetKey, boolean removeEmptyGenera) {
    this.factory = factory;
    this.datasetKey = datasetKey;
    if (removeEmptyGenera) {
      parents = new ParentStack<>(this::endClassificationStack);
    } else {
      parents = new ParentStack<>(null);
    }
  }

  /**
   * To be called from the parent stack when a taxon gets removed from the stack
   * It considers to remove empty genera and creates missing autonyms
   * @param taxon
   */
  void endClassificationStack(ParentStack.SNC<SimpleNameUsage> taxon) {
    // remove tracked genus
    if (taxon.sn.getRank() == Rank.GENUS) {
      genus = null;
    }
    // remove empty genera?
    if (taxon.sn.getRank().isGenusGroup() && taxon.children == 0 && fromXSource(taxon.sn)) {
      LOG.info("Remove empty {}", taxon.sn);
      final var key = DSID.of(datasetKey, taxon.sn.getId());
      try (SqlSession session = factory.openSession(true)) {
        var vm = session.getMapper(VerbatimSourceMapper.class);
        var um = session.getMapper(NameUsageMapper.class);
        // first remove all synonyms
        for (var c : um.childrenIds(key)) {
          vm.delete(key.id(c));
          um.delete(key);
        }
        vm.delete(key.id(taxon.sn.getId()));
        um.delete(key);
        // names, references and related are removed as orphans at the end of the release
      }
    }
  }

  private boolean fromXSource(SimpleNameUsage sn) {
    return sn.getId().contains("-"); // a temp UUID identifier!
  }

  @Override
  public void accept(SimpleNameUsage sn) {
    final IssueContainer issues = IssueContainer.simple();

    // main parsed name validation
    NameValidator.flagIssues(sn.getName(), issues);

    if (sn.getRank().isSpeciesOrBelow()) {
      // flag parent mismatches
      if (sn.getName().isParsed()) {
        if (sn.getRank().isInfraspecific()) {
          // we have a trinomial, compare species
          var sp = parents.find(Rank.SPECIES);
          if (sp == null) {
            issues.addIssue(Issue.PARENT_SPECIES_MISSING);
          } else if (sp.getName().isParsed()
                    && !Objects.equals(sn.getName().getGenus(), sp.getName().getGenus())
                    || !Objects.equals(sn.getName().getSpecificEpithet(), sp.getName().getSpecificEpithet())
          ) {
            issues.addIssue(Issue.PARENT_NAME_MISMATCH);
          }
        } else {
          // we have a binomial, compare genus only
          if (genus == null) {
            issues.addIssue(Issue.MISSING_GENUS);
          } else if (genus.getName().isParsed()
                   && !Objects.equals(sn.getName().getGenus(), genus.getName().getGenus())
          ) {
            issues.addIssue(Issue.PARENT_NAME_MISMATCH);
          }
        }
        // flag if published before the genus
        if (!issues.hasIssue(Issue.PARENT_NAME_MISMATCH)
            && !issues.hasIssue(Issue.MISSING_GENUS)
            && genus != null
            && genus.getName().getPublishedInYear() != null
            && sn.getName().getPublishedInYear() != null
            && sn.getName().getPublishedInYear() > 1600
            && genus.getName().getPublishedInYear() > sn.getName().getPublishedInYear()
        ) {
            // flag if the accepted bi/trinomial the ones that have an earlier publication date!
            issues.addIssue(Issue.PUBLISHED_BEFORE_GENUS);
        }

        // TODO: create missing autonyms
      }
    }
    if (sn.getRank() == Rank.GENUS) {
      genus = sn;
    }
    parents.push(sn);
    if (!issues.hasIssues()) {
      addIssues(sn, issues);
    }
  }

  DSID<String> dsid(SimpleNameUsage u){
    return DSID.of(datasetKey, u.getId());
  }

  void addIssues(SimpleNameUsage sn, IssueContainer issues) {
    try (SqlSession session = factory.openSession(true)) {
      var vsm = session.getMapper(VerbatimSourceMapper.class);
      vsm.addIssues(dsid(sn), issues.getIssues());
    }
  }
}
