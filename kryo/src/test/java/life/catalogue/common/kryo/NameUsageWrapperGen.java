package life.catalogue.common.kryo;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.*;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.api.search.SimpleDecision;
import life.catalogue.api.vocab.*;
import life.catalogue.common.date.FuzzyDate;

import org.gbif.nameparser.api.*;

import java.net.URI;
import java.util.*;

import com.esotericsoftware.kryo.Kryo;

/**
 * Test-support factory for {@link NameUsageWrapper} instances that relies on Kryo for deep copies.
 * <p>
 * These helpers live in the kryo module (test scope) rather than in api:tests because they require
 * {@link ApiKryoPool} (which now lives in the kryo module). api may not depend on kryo, so keeping
 * them here avoids a dependency cycle. Downstream test modules (e.g. dao) consume this class via the
 * kryo test-jar.
 */
public class NameUsageWrapperGen {

  private final static Kryo kryo = new ApiKryoPool(1).create();

  public static NameUsageWrapper newNameUsageWrapper(NameUsage usage) {
    NameUsageWrapper nuw = new NameUsageWrapper(usage);
    nuw.getUsage().setSectorMode(Sector.Mode.MERGE);
    nuw.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.NAME_VARIANT, Issue.DISTRIBUTION_AREA_INVALID));
    nuw.setSectorDatasetKey(42);
    nuw.setSectorPublisherKey(UUID.randomUUID());
    nuw.setSecondarySourceGroups(new HashSet<>(Set.of(InfoGroup.AUTHORSHIP, InfoGroup.PUBLISHED_IN)));
    nuw.setSecondarySourceKeys(new HashSet<>(Set.of(1010,  2123)));
    nuw.setGroup(TaxGroup.Plants);
    nuw.setDecisions(List.of(new SimpleDecision(1, 99, EditorialDecision.Mode.REVIEWED)));
    nuw.setClassification(List.of(
      SimpleName.sn(Rank.KINGDOM, "Animalia"),
      SimpleName.sn(Rank.CLASS, "Mammalia"),
      SimpleName.sn(Rank.ORDER, "Carnivora"),
      SimpleName.sn(Rank.FAMILY, "Felidae", "Fischer, 1817"),
      SimpleName.sn(Rank.SUBFAMILY, "Felinae", "Fischer, 1817")
    ));
    return copy(nuw);
  }

  public static NameUsageWrapper newNameUsageTaxonWrapper() {
    NameUsageWrapper nuw = new NameUsageWrapper();
    nuw.setUsage(new Taxon(TestEntityGenerator.TAXON1));
    nuw.getUsage().setSectorMode(Sector.Mode.MERGE);
    nuw.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.NAME_VARIANT, Issue.DISTRIBUTION_AREA_INVALID));
    nuw.setSectorPublisherKey(UUID.randomUUID());
    nuw.setSecondarySourceGroups(new HashSet<>(Set.of(InfoGroup.AUTHORSHIP, InfoGroup.PUBLISHED_IN)));
    nuw.setSecondarySourceKeys(new HashSet<>(Set.of(1010,  2123)));
    return copy(nuw);
  }

  public static NameUsageWrapper newNameUsageTaxonWrapperComplete() {
    var nuw = newNameUsageTaxonWrapper();
    nuw.setGroup(TaxGroup.Algae);
    nuw.setSectorDatasetKey(4567);
    nuw.setDecisions(new ArrayList<>(List.of( // array list to avoid kryo not doing a deep copy
      new SimpleDecision(66, 456, EditorialDecision.Mode.UPDATE),
      new SimpleDecision(62, 456, EditorialDecision.Mode.BLOCK)
    )));
    nuw.setClassification(new ArrayList<>(List.of(
      SimpleName.sn("Karambula"),
      SimpleName.sn(Rank.ORDER, "Karambulales"),
      SimpleName.sn("d4f", Rank.PHYLUM, "Karambulatae", null) // authorship is not kept !!!
    )));
    var u = (Taxon) nuw.getUsage();
    u.setParentId("P68");
    u.setSectorKey(12);
    u.setScrutinizerID("456ZT");
    u.setExtinct(true);
    u.setScrutinizer("drftg");
    u.setScrutinizerDate(FuzzyDate.of("2008-08"));
    u.setEnvironments(Set.of(Environment.TERRESTRIAL, Environment.MARINE));
    u.setTemporalRangeStart("start");
    u.setTemporalRangeEnd("end");
    u.setTemporalRangeStart(GeoTime.byName("Neoarchean"));
    u.setTemporalRangeEnd(GeoTime.byName("Cretaceous"));
    u.setOrdinal(789);
    u.setNamePhrase("ftgzhj");
    u.setLink(URI.create("http://go.to/me"));
    u.setRemarks("drfthuj gtzhu jz7ghu");
    u.setIdentifier(List.of(
      Identifier.parse("col:4r56"),
      Identifier.parse("gbif:3456789")
    ));
    var n = u.getName();
    n.setSectorKey(13);
    n.setSectorMode(Sector.Mode.MERGE);
    n.setGender(Gender.FEMININE);
    n.setGenderAgreement(true);
    n.setNomStatus(NomStatus.ESTABLISHED);
    n.setPublishedInPage("14");
    n.setPublishedInYear(1988);
    n.setLink(URI.create("http://read.me"));
    n.setRemarks("NR guzgqszugwqtfdwqw");
    n.setEtymology("etym");
    n.setNomenclaturalNote("nom notes not good");
    return  nuw;
  }

  public static NameUsageWrapper newNameUsageSynonymWrapper() {
    NameUsageWrapper nuw = new NameUsageWrapper();
    nuw.setUsage(new Synonym(TestEntityGenerator.SYN2));
    EnumSet<Issue> issues = EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.NAME_VARIANT,
        Issue.DISTRIBUTION_AREA_INVALID);
    nuw.setIssues(issues);
    return copy(nuw);
  }

  public static NameUsageWrapper newNameUsageBareNameWrapper() {
    NameUsageWrapper nuw = new NameUsageWrapper();
    BareName bn = new BareName();
    bn.setName(TestEntityGenerator.NAME4);
    nuw.setUsage(bn);
    EnumSet<Issue> issues = EnumSet.of(Issue.ID_NOT_UNIQUE);
    nuw.setIssues(issues);
    return copy(nuw);
  }

  /**
   * Deep copies objects using Kryo.
   * Object classes to be copied need to be registered with the ApiKryoPool.
   * @param obj
   * @param <T>
   * @return a deep copy of obj
   */
  public static <T> T copy(T obj) {
    return kryo.copy(obj);
  }
}
