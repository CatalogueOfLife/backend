package life.catalogue.dao;

/**
 * What archiving releases wrote into the name usage archive, or in a dry run would write. See NameUsageArchiver.
 */
public class ArchiveStats {
  public int releases;
  public int inserted;
  public int rewritten;
  /**
   * Dry runs only: rewritten records whose scientific name changes, i.e. the ones a rematch has work with.
   */
  public int renamed;
  public int keysAdded;
  public int matchesCopied;
  public int matchesDeleted;
  public int supersededApplied;
  public int supersededCleared;

  public void add(ArchiveStats other) {
    releases += other.releases;
    inserted += other.inserted;
    rewritten += other.rewritten;
    renamed += other.renamed;
    keysAdded += other.keysAdded;
    matchesCopied += other.matchesCopied;
    matchesDeleted += other.matchesDeleted;
    supersededApplied += other.supersededApplied;
    supersededCleared += other.supersededCleared;
  }

  /**
   * @return true if nothing was, or would be, written
   */
  public boolean isUnchanged() {
    return inserted + rewritten + keysAdded + matchesCopied + matchesDeleted + supersededApplied + supersededCleared == 0;
  }

  @Override
  public String toString() {
    return String.format("%d releases, %d records inserted, %d rewritten (%d renamed), %d release keys added, "
        + "%d matches copied and %d removed, %d superseded ids recorded and %d cleared",
      releases, inserted, rewritten, renamed, keysAdded, matchesCopied, matchesDeleted, supersededApplied, supersededCleared);
  }
}
