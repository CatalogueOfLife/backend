package life.catalogue.es;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.util.Pool;

import de.undercouch.citeproc.csl.CSLType;

import life.catalogue.api.model.*;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.api.search.SimpleDecision;
import life.catalogue.api.vocab.*;
import life.catalogue.api.vocab.terms.*;
import life.catalogue.coldp.ColdpTerm;
import life.catalogue.common.date.FuzzyDate;
import life.catalogue.common.kryo.*;
import life.catalogue.common.kryo.jdk.JdkImmutableListSerializer;
import life.catalogue.common.kryo.jdk.JdkImmutableMapSerializer;
import life.catalogue.common.kryo.jdk.JdkImmutableSetSerializer;

import org.gbif.dwc.terms.BibTexTerm;
import org.gbif.dwc.terms.TermFactory;
import org.gbif.dwc.terms.UnknownTerm;
import org.gbif.nameparser.api.*;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;


/**
 * Creates a kryo factory usable for thread safe kryo pools that can deal with converting NameUsageWrappers to payloads.
 */
public class EsKryoPool extends Pool<Kryo> {

  public EsKryoPool(int maximumCapacity) {
    super(true, true, maximumCapacity);
  }

  @Override
  public Kryo create() {
    return configure(new Kryo());
  }

  public static Kryo configure(Kryo kryo) {
    kryo.setRegistrationRequired(true);

    // wrapper classes
    kryo.register(NameUsageWrapper.class);
    kryo.register(SimpleDecision.class);
    kryo.register(Taxon.class);
    kryo.register(Synonym.class);
    kryo.register(BareName.class);
    kryo.register(Name.class);
    kryo.register(Identifier.class);
    kryo.register(Authorship.class);
    kryo.register(SimpleName.class);

    // date/time
    kryo.register(LocalDate.class);
    kryo.register(LocalDateTime.class);
    kryo.register(FuzzyDate.class, new FuzzyDateSerializer());

    // java & commons
    kryo.register(int[].class);
    kryo.register(URI.class, new URISerializer());
    kryo.register(UUID.class, new UUIDSerializer());
    // collections
    ApiKryoPool.registerCollectionClasses(kryo);

    // enums
    kryo.register(EditorialDecision.Mode.class);
    kryo.register(Environment.class);
    kryo.register(Gender.class);
    kryo.register(GeoTime.class);
    kryo.register(GeoTimeType.class);
    kryo.register(InfoGroup.class);
    kryo.register(Issue.class);
    kryo.register(MatchType.class);
    kryo.register(NamePart.class);
    kryo.register(NameType.class);
    kryo.register(NomCode.class);
    kryo.register(NomStatus.class);
    kryo.register(Origin.class);
    kryo.register(Rank.class);
    kryo.register(Sector.Mode.class);
    kryo.register(Sex.class);
    kryo.register(TaxGroup.class);
    kryo.register(TaxonomicStatus.class);

    return kryo;
  }
}
