package life.catalogue.matching.person.harvest;



import java.nio.file.Path;
import java.util.List;

/**
 * Persons from a ZooBank author dump. ZooBank has no bulk access; author dumps have been requested, so this is a
 * placeholder whose reader is written once a dump and its format exist. What it maps to is fixed already:
 * the author UUID becomes the zb: id and the zoobank column; the names ZooBank cites the author by become
 * zoological CITATION forms and other name records of the author VARIANT forms; the lifespan becomes born and died;
 * a Wikidata, IPNI or ORCID link the dump carries is a join key. The merge side is tested with hand made records.
 */
public class ZooBankDumpSource implements HarvestSource {
  private final Path dump;

  public ZooBankDumpSource(Path dump) {
    this.dump = dump;
  }

  @Override
  public String name() {
    return "zoobank";
  }

  @Override
  public List<PersonRecord> read() {
    throw new UnsupportedOperationException("No reader for ZooBank dumps yet, the format of " + dump
      + " is not known. Write ZooBankDumpSource.read() for it, see docs/AUTHOR-PERSONS.md");
  }
}
