package life.catalogue.event;

import life.catalogue.api.event.*;
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

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.util.Pool;

import de.undercouch.citeproc.csl.CSLType;

import static life.catalogue.common.kryo.ApiKryoPool.registerCollectionClasses;


/**
 * Creates a kryo factory usable for thread safe kryo pools that can deal with event instances.
 */
public class EventKryoPool extends Pool<Kryo> {

  public EventKryoPool(int maximumCapacity) {
    super(true, true, maximumCapacity);
  }

  @Override
  public Kryo create() {
    return configure(new Kryo());
  }

  public static Kryo configure(Kryo kryo) {
    kryo.setRegistrationRequired(true);

    // events
    kryo.register(Event.class);
    kryo.register(ChangeDoi.class);
    kryo.register(DatasetChanged.class);
    kryo.register(DatasetDataChanged.class);
    kryo.register(DatasetLogoChanged.class);
    kryo.register(DeleteSector.class);
    kryo.register(UserChanged.class);
    kryo.register(UserPermissionChanged.class);
    kryo.register(EventType.class);

    // dependent api classes
    kryo.register(Agent.class);
    kryo.register(Citation.class);
    kryo.register(Classification.class);
    kryo.register(DOI.class);
    kryo.register(Dataset.UrlDescription.class);
    kryo.register(Dataset.class);
    kryo.register(DatasetImport.class);
    kryo.register(Identifier.class);
    kryo.register(User.class);

    // date/time
    kryo.register(FuzzyDate.class, new FuzzyDateSerializer());
    kryo.register(LocalDate.class);
    kryo.register(LocalDateTime.class);

    // java & commons
    kryo.register(Class.class);
    kryo.register(int[].class);
    kryo.register(URI.class, new URISerializer());
    kryo.register(UUID.class, new UUIDSerializer());
    registerCollectionClasses(kryo);

    // enums
    kryo.register(Country.class);
    kryo.register(DataFormat.class);
    kryo.register(DatasetOrigin.class);
    kryo.register(DatasetType.class);
    kryo.register(EnumMap.class);
    kryo.register(EnumSet.class);
    kryo.register(ImportState.class);
    kryo.register(License.class);
    kryo.register(Environment.class);
    kryo.register(User.Role.class);

    return kryo;
  }
}
