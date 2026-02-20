package life.catalogue.es;

import life.catalogue.api.model.*;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.api.search.SimpleDecision;
import life.catalogue.api.vocab.*;
import life.catalogue.common.date.FuzzyDate;
import life.catalogue.common.kryo.ApiKryoPool;
import life.catalogue.common.kryo.FuzzyDateSerializer;
import life.catalogue.common.kryo.URISerializer;
import life.catalogue.common.kryo.UUIDSerializer;

import org.gbif.nameparser.api.*;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.util.Pool;


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
    kryo.register(Authorship.class);
    kryo.register(BareName.class);
    kryo.register(Identifier.class);
    kryo.register(Name.class);
    kryo.register(NameUsageWrapper.class);
    kryo.register(SimpleDecision.class);
    kryo.register(SimpleName.class);
    kryo.register(SimpleVernacularName.class);
    kryo.register(Synonym.class);
    kryo.register(Taxon.class);

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
    kryo.register(SimpleNameCached.class);

    return kryo;
  }
}
