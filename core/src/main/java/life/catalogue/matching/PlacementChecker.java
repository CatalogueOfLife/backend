package life.catalogue.matching;

import life.catalogue.api.model.SimpleName;
import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.matching.authorship.AuthorComparator;
import life.catalogue.matching.nidx.NameIndexImpl;

import org.gbif.nameparser.api.Rank;

import java.util.*;

/**
 * Tells whether a source placed an accepted genus in a taxonomic group a reference checklist, usually the COL base
 * release, does not know that genus in. Literature sources regularly file a genus under the classification of the
 * paper's subject rather than its own: a fungus under the plant family of its host, a parasite under its vector.
 * Merged, such a genus attracts every later species of that name into the wrong kingdom.
 * <p>
 * The verdict is deliberately cautious and stays silent whenever the evidence is thin:
 * <ul>
 *   <li>only accepted genera are judged, so the flagged share of a dataset is a share of its accepted genera</li>
 *   <li>a source group of eukaryotes or none is too coarse to contradict anything</li>
 *   <li>a genus the reference does not hold, or holds once without a known group, is new information, not a conflict</li>
 *   <li>an authored genus whose authorship differs from every reference genus is a real homonym the reference lacks</li>
 * </ul>
 * Only a genus whose group is disparate to the group of every reference genus of that name is a conflict.
 *
 * @see <a href="https://github.com/CatalogueOfLife/backend/issues/1608">#1608</a>
 */
public class PlacementChecker implements AutoCloseable {
  private final UsageMatcher reference;
  private final boolean closeReference;
  private final TaxGroupAnalyzer analyzer = new TaxGroupAnalyzer();
  private final AuthorComparator authComp;

  /**
   * The group the source placed the genus in and the groups the reference knows the genus in.
   */
  public record Conflict(TaxGroup group, Set<TaxGroup> referenceGroups) {
  }

  /**
   * @param reference matcher of the reference checklist
   * @param closeReference if true the checker owns the reference matcher and closes it in {@link #close()}
   */
  public PlacementChecker(UsageMatcher reference, boolean closeReference) {
    this.reference = reference;
    this.closeReference = closeReference;
    this.authComp = reference.getNameIndex() instanceof NameIndexImpl ni
      ? ni.getAuthComp()
      : new AuthorComparator(AuthorshipNormalizer.INSTANCE);
  }

  public int getReferenceDatasetKey() {
    return reference.getDatasetKey();
  }

  /**
   * @param genus the usage to check. Anything but an accepted genus is never a conflict.
   * @param classification the higher classification of the genus in its source, in any order
   * @return the conflict if the source placement contradicts the reference
   */
  public Optional<Conflict> check(SimpleName genus, Collection<? extends SimpleName> classification) {
    if (genus.getRank() != Rank.GENUS || genus.getStatus() == null || !genus.getStatus().isTaxon()) {
      return Optional.empty();
    }
    var group = analyzer.analyze(genus, classification);
    if (group == null || group == TaxGroup.Eukaryotes) {
      return Optional.empty();
    }
    var nm = reference.getNameIndex().match(genus, false, false);
    if (nm == null || nm.getNidx() == null) {
      return Optional.empty();
    }
    var candidates = reference.store().usagesByCanonicalId(nm.getNidx()).stream()
      .filter(u -> u.getRank() == Rank.GENUS && !u.getStatus().isBareName())
      .toList();
    if (candidates.isEmpty()) {
      return Optional.empty();
    }
    // a differently authored genus is a homonym the reference lacks, not a misplaced one
    if (genus.hasAuthorship() && candidates.stream().allMatch(u -> u.hasAuthorship()
        && authComp.compare(UsageMatcher.parseSciName(u), UsageMatcher.parseSciName(genus)) == Equality.DIFFERENT)) {
      return Optional.empty();
    }
    Set<TaxGroup> referenceGroups = EnumSet.noneOf(TaxGroup.class);
    for (var u : candidates) {
      var ug = analyzer.analyze(u, u.getClassification());
      if (ug == null || !group.isDisparateTo(ug)) {
        return Optional.empty();
      }
      referenceGroups.add(ug);
    }
    return Optional.of(new Conflict(group, referenceGroups));
  }

  @Override
  public void close() {
    if (closeReference) {
      reference.close();
    }
  }
}
