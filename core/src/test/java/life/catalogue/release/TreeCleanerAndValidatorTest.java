package life.catalogue.release;

import life.catalogue.api.model.IssueContainer;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.Taxon;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.dao.ParentStack;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TreeCleanerAndValidatorTest {

  private static TreeCleanerAndValidator.XLinneanNameUsage usage(String id, String parentId, Rank rank,
                                                                 String uninomial, String genus, String infrageneric) {
    Name n = new Name();
    n.setId(id);
    n.setRank(rank);
    n.setCode(NomCode.ZOOLOGICAL);
    n.setType(NameType.SCIENTIFIC);
    n.setUninomial(uninomial);
    n.setGenus(genus);
    n.setInfragenericEpithet(infrageneric);
    n.rebuildScientificName();
    Taxon t = new Taxon(n, TaxonomicStatus.ACCEPTED);
    t.setId(id);
    t.setParentId(parentId);
    return new TreeCleanerAndValidator.XLinneanNameUsage(t);
  }

  private static IssueContainer validate(ParentStack<TreeCleanerAndValidator.XLinneanNameUsage> parents,
                                         TreeCleanerAndValidator.XLinneanNameUsage u) {
    var issues = IssueContainer.simple();
    TreeCleanerAndValidator.validateAndPush(u, parents, List.of(), issues);
    return issues;
  }

  /**
   * https://github.com/CatalogueOfLife/checklistbank/issues/1763
   */
  @Test
  public void infragenericParentMismatch() {
    var parents = new ParentStack<TreeCleanerAndValidator.XLinneanNameUsage>();
    validate(parents, usage("tr", null, Rank.TRIBE, "Cicindelini", null, null));
    validate(parents, usage("g", "tr", Rank.GENUS, "Cylindera", null, null));

    // the genus of the name matches the parent genus
    var issues = validate(parents, usage("sg1", "g", Rank.SUBGENUS, null, "Cylindera", "Cylindera"));
    assertFalse(issues.contains(Issue.PARENT_NAME_MISMATCH));

    // another genus
    issues = validate(parents, usage("sg2", "g", Rank.SUBGENUS, null, "Cicindela", "Cicindelina"));
    assertTrue(issues.contains(Issue.PARENT_NAME_MISMATCH));

    // no genus in the name
    issues = validate(parents, usage("sg3", "g", Rank.SUBGENUS, null, null, "Ifasina"));
    assertFalse(issues.contains(Issue.PARENT_NAME_MISMATCH));

    // no genus in the classification
    issues = validate(parents, usage("sg4", "tr", Rank.SUBGENUS, null, "Cicindela", "Cicindelina"));
    assertFalse(issues.contains(Issue.PARENT_NAME_MISMATCH));
  }
}
