package life.catalogue.matching;

import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.model.SimpleNameClassified;
import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.matching.authorship.AuthorComparator;
import life.catalogue.matching.nidx.NameIndexImpl;

import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.util.*;

import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

/**
 * Tells whether a source placed an accepted genus in a taxonomic group the reference checklists, usually the COL base
 * release and IRMNG, do not know that genus in. Literature sources regularly file a genus under the classification of
 * the paper's subject rather than its own: a fungus under the plant family of its host, a parasite under its vector.
 * Merged, such a genus attracts every later species of that name into the wrong kingdom.
 * <p>
 * The genera of all references are pooled: a genus any reference knows in a non disparate group is no conflict.
 * A single reference has gaps that look like conflicts - COL lacks the fossil bovid Duboisia, IRMNG some cicada genera -
 * so another reference vetoes far more false flags than it hides real ones.
 * <p>
 * The verdict is deliberately cautious and stays silent whenever the evidence is thin:
 * <ul>
 *   <li>only accepted genera are judged, so the flagged share of a dataset is a share of its accepted genera</li>
 *   <li>a source group of eukaryotes or none is too coarse to contradict anything</li>
 *   <li>a genus no reference holds, or one holds without a known group, is new information, not a conflict</li>
 *   <li>an authored genus whose authorship differs from every reference genus is a real homonym the references lack</li>
 * </ul>
 * Only a genus whose group is disparate to the group of every reference genus of that name is a conflict.
 *
 * @see <a href="https://github.com/CatalogueOfLife/backend/issues/1608">#1608</a>
 */
public class PlacementChecker implements AutoCloseable {
  private static final Logger LOG = LoggerFactory.getLogger(PlacementChecker.class);
  private final List<UsageMatcher> references;
  private final boolean closeReferences;
  private final TaxGroupAnalyzer analyzer = new TaxGroupAnalyzer();
  private final AuthorComparator authComp;

  /**
   * The group the source placed the genus in and the groups the references know the genus in.
   */
  public record Conflict(TaxGroup group, Set<TaxGroup> referenceGroups) {
  }

  /**
   * @param references matchers of the reference checklists, at least one. They share one names index.
   * @param closeReferences if true the checker owns the reference matchers and closes them in {@link #close()}
   */
  public PlacementChecker(List<UsageMatcher> references, boolean closeReferences) {
    Preconditions.checkArgument(!references.isEmpty(), "At least one reference is required");
    this.references = List.copyOf(references);
    this.closeReferences = closeReferences;
    this.authComp = references.getFirst().getNameIndex() instanceof NameIndexImpl ni
      ? ni.getAuthComp()
      : new AuthorComparator(AuthorshipNormalizer.INSTANCE);
  }

  /**
   * Opens a checker against all references that can be resolved and have a matcher. The others are logged and left
   * out, so a missing release or matcher weakens the check but never fails it.
   *
   * @param references dataset keys or project keys with an LR or LXR suffix, see {@link AddIdentifiersSpec}
   * @param datasetKey the dataset to be checked, never its own reference
   * @return the checker owning the reference matchers, or null if no reference could be opened
   */
  public static @Nullable PlacementChecker open(@Nullable List<String> references, UsageMatcherFactory matcherFactory,
                                                SqlSessionFactory factory, int datasetKey) {
    if (references == null || references.isEmpty()) {
      return null;
    }
    List<UsageMatcher> matchers = new ArrayList<>();
    for (String ref : references) {
      try {
        int refKey = AddIdentifiersSpec.parse(ref).resolveDatasetKey(factory);
        if (refKey == datasetKey) {
          continue;
        }
        var matcher = matcherFactory.get(refKey);
        if (matcher == null) {
          LOG.warn("No matcher for placement reference {} (resolved={}), left out of the placement check of dataset {}", ref, refKey, datasetKey);
        } else {
          matchers.add(matcher);
        }
      } catch (IllegalStateException e) {
        // no such release, e.g. on a fresh environment
        LOG.warn("Cannot resolve placement reference {}, left out of the placement check of dataset {}: {}", ref, datasetKey, e.getMessage());
      } catch (IOException | RuntimeException e) {
        LOG.warn("Cannot use placement reference {}, left out of the placement check of dataset {}", ref, datasetKey, e);
      }
    }
    if (matchers.isEmpty()) {
      return null;
    }
    LOG.info("Check the placement of genera in dataset {} against {}", datasetKey, matchers.stream().map(UsageMatcher::getDatasetKey).toList());
    return new PlacementChecker(matchers, true);
  }

  public List<Integer> getReferenceDatasetKeys() {
    return references.stream().map(UsageMatcher::getDatasetKey).toList();
  }

  /**
   * @param genus the usage to check. Anything but an accepted genus is never a conflict.
   * @param classification the higher classification of the genus in its source, in any order
   * @return the conflict if the source placement contradicts the references
   */
  public Optional<Conflict> check(SimpleName genus, Collection<? extends SimpleName> classification) {
    if (genus.getRank() != Rank.GENUS || genus.getStatus() == null || !genus.getStatus().isTaxon()) {
      return Optional.empty();
    }
    var group = analyzer.analyze(genus, classification);
    if (group == null || group == TaxGroup.Eukaryotes) {
      return Optional.empty();
    }
    var nm = references.getFirst().getNameIndex().match(genus, false, false);
    if (nm == null || nm.getNidx() == null) {
      return Optional.empty();
    }
    List<SimpleNameClassified<SimpleNameCached>> candidates = new ArrayList<>();
    for (var ref : references) {
      ref.store().usagesByCanonicalId(nm.getNidx()).stream()
        .filter(u -> u.getRank() == Rank.GENUS && !u.getStatus().isBareName())
        .forEach(candidates::add);
    }
    if (candidates.isEmpty()) {
      return Optional.empty();
    }
    // a differently authored genus is a homonym the references lack, not a misplaced one
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
    if (closeReferences) {
      references.forEach(UsageMatcher::close);
    }
  }
}
