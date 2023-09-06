package life.catalogue.release;

import com.esotericsoftware.minlog.Log;

import life.catalogue.api.model.SimpleName;
import life.catalogue.api.vocab.Issue;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.VerbatimSourceMapper;

import life.catalogue.matching.NameValidator;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import org.gbif.nameparser.api.Rank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class TreeCleanerAndValidator implements Consumer<SimpleName> {
  static final Logger LOG = LoggerFactory.getLogger(TreeCleanerAndValidator.class);
  static final Pattern BINOMEN = Pattern.compile("^([A-Z][^ ]+)(:? ([a-z][^ ]+))?");

  final SqlSessionFactory factory;
  final int datasetKey;
  final ParentStackSimple parents;

  public TreeCleanerAndValidator(SqlSessionFactory factory, int datasetKey, boolean removeEmptyGenera) {
    this.factory = factory;
    this.datasetKey = datasetKey;
    if (removeEmptyGenera) {
      parents = new ParentStackSimple(this::removeEmptyGenera);
    } else {
      parents = new ParentStackSimple(null);
    }
  }

  void removeEmptyGenera(ParentStackSimple.SNC taxon) {
    // remove empty genera?
    if (taxon.sn.getRank().isGenusGroup() && taxon.children == 0 && fromXSource(taxon.sn)) {
      LOG.info("Remove empty {}", taxon.sn);
      try (SqlSession session = factory.openSession(true)) {
        var key = taxon.sn.toDSID(datasetKey);
        session.getMapper(VerbatimSourceMapper.class).delete(key);
        session.getMapper(NameUsageMapper.class).delete(key);
        // names, references and related are removed as orphans at the end of the release
      }
    }
  }

  private boolean fromXSource(SimpleName sn) {
    return sn.getId().contains("-"); // a temp UUID identifier!
  }

  @Override
  public void accept(SimpleName sn) {
    final Set<Issue> issues = new HashSet<>();
    if (sn.getRank().isSpeciesOrBelow()) {
      // flag parent mismatches
      var m = BINOMEN.matcher(sn.getName());
      if (m.find()) {
        if (sn.getRank().isInfraspecific()) {
          // we have a trinomial, compare species
          var sp = parents.find(Rank.SPECIES);
          if (sp == null) {
            issues.add(Issue.PARENT_SPECIES_MISSING);
          } else {
            if (!m.group().equalsIgnoreCase(sp.getName())) {
              issues.add(Issue.PARENT_NAME_MISMATCH);
            }
          }
        } else {
          // we have a binomial, compare genus only
          var gen = parents.find(Rank.GENUS);
          if (gen == null) {
            issues.add(Issue.MISSING_GENUS);
          } else if (!m.group(1).equalsIgnoreCase(gen.getName())) {
            issues.add(Issue.PARENT_NAME_MISMATCH);
          }
        }
      }

      // TODO: create missing autonyms
      // TODO: remove empty genera

      //TODO: use full name validator, but needs Name instance
      if (NameValidator.hasUnmatchedBrackets(sn.getName()) || NameValidator.hasUnmatchedBrackets(sn.getAuthorship())) {
        issues.add(Issue.UNMATCHED_NAME_BRACKETS);
      }
    }
    parents.push(sn);
    if (!issues.isEmpty()) {
      addIssues(sn, issues);
    }
  }

  void addIssues(SimpleName sn, Set<Issue> issues) {
    try (SqlSession session = factory.openSession(true)) {
      var vsm = session.getMapper(VerbatimSourceMapper.class);
      vsm.addIssues(sn.toDSID(datasetKey), issues);
    }
  }
}
