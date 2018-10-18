package org.col.common.kryo;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.pool.KryoFactory;
import de.javakaffee.kryoserializers.UnmodifiableCollectionsSerializer;
import de.javakaffee.kryoserializers.guava.ImmutableListSerializer;
import org.col.api.model.*;
import org.col.api.vocab.*;
import org.gbif.dwc.terms.TermFactory;
import org.gbif.dwc.terms.UnknownTerm;
import org.gbif.nameparser.api.*;


/**
 * Creates a kryo factory usable for thread safe kryo pools that can deal with clb api classes.
 * We use Kryo for extremely fast byte serialization of temporary objects.
 * It is used to serialize various information in kvp stores during checklist indexing and nub builds.
 */
public class ApiKryoFactory implements KryoFactory {

  @Override
  public Kryo create() {
    Kryo kryo = new Kryo();
    kryo.setRegistrationRequired(true);

    // col core
    kryo.register(Authorship.class);
    kryo.register(Classification.class);
    kryo.register(Dataset.class);
    kryo.register(DatasetImport.class);
    kryo.register(Distribution.class);
    kryo.register(Name.class);
    kryo.register(NameAccordingTo.class);
    kryo.register(NameRelation.class);
    kryo.register(ParsedName.class);
    kryo.register(ParsedName.State.class);
    kryo.register(Reference.class);
    kryo.register(Sector.class);
    kryo.register(Synonym.class);
    kryo.register(Taxon.class);
    kryo.register(VernacularName.class);
    kryo.register(VerbatimRecord.class);
    kryo.register(Page.class);

    // CSL classes & enums
    kryo.register(CslData.class);
    kryo.register(CslName.class);
    kryo.register(CslName[].class);
    kryo.register(CslDate.class);
    kryo.register(CSLRefType.class);
    kryo.register(String[].class);
    kryo.register(int[][].class);

    // java & commons
    kryo.register(LocalDateTime.class);
    kryo.register(LocalDate.class);
    kryo.register(HashMap.class);
    kryo.register(LinkedHashMap.class);
    kryo.register(HashSet.class);
    kryo.register(ArrayList.class);
    kryo.register(LinkedList.class);
    kryo.register(UUID.class, new UUIDSerializer());
    kryo.register(URI.class, new URISerializer());
    kryo.register(int[].class);
    UnmodifiableCollectionsSerializer.registerSerializers( kryo );
    ImmutableListSerializer.registerSerializers(kryo);

    // enums
    kryo.register(Catalogue.class);
    kryo.register(Country.class);
    kryo.register(DataFormat.class);
    kryo.register(DatasetType.class);
    kryo.register(DistributionStatus.class);
    kryo.register(EnumMap.class, new EnumMapSerializer());
    kryo.register(EnumSet.class, new EnumSetSerializer());
    kryo.register(Frequency.class);
    kryo.register(Gazetteer.class);
    kryo.register(ImportState.class);
    kryo.register(Issue.class);
    kryo.register(Kingdom.class);
    kryo.register(Language.class);
    kryo.register(License.class);
    kryo.register(Lifezone.class);
    kryo.register(NamePart.class);
    kryo.register(NameType.class);
    kryo.register(NomRelType.class);
    kryo.register(NomCode.class);
    kryo.register(NomStatus.class);
    kryo.register(Origin.class);
    kryo.register(DatasetOrigin.class);
    kryo.register(Rank.class);
    kryo.register(TaxonomicStatus.class);
    kryo.register(TypeStatus.class);

    // term enums
    TermFactory.instance().registerTermEnum(ColTerm.class);
    for (Class cl : TermFactory.instance().listRegisteredTermEnums()) {
      kryo.register(cl);
    }
    kryo.register(UnknownTerm.class, new TermSerializer());

    return kryo;
  }
}
