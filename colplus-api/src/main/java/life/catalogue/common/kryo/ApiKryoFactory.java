package life.catalogue.common.kryo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.pool.KryoFactory;
import de.javakaffee.kryoserializers.UnmodifiableCollectionsSerializer;
import de.javakaffee.kryoserializers.guava.ImmutableListSerializer;
import life.catalogue.api.datapackage.ColdpTerm;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.*;
import life.catalogue.common.date.FuzzyDate;
import org.gbif.dwc.terms.TermFactory;
import org.gbif.dwc.terms.UnknownTerm;
import org.gbif.nameparser.api.*;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;


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
    kryo.register(Description.class);
    kryo.register(Distribution.class);
    kryo.register(Media.class);
    kryo.register(Name.class);
    kryo.register(NameAccordingTo.class);
    kryo.register(NameRelation.class);
    kryo.register(Page.class);
    kryo.register(ParsedName.class);
    kryo.register(ParsedName.State.class);
    kryo.register(Reference.class);
    kryo.register(Sector.class);
    kryo.register(Synonym.class);
    kryo.register(Taxon.class);
    kryo.register(TypeMaterial.class);
    kryo.register(VerbatimRecord.class);
    kryo.register(VernacularName.class);

    // CSL classes & enums
    kryo.register(CslData.class);
    kryo.register(CslDate.class);
    kryo.register(CslName.class);
    kryo.register(CslName[].class);
    kryo.register(CSLRefType.class);
    kryo.register(int[][].class);
    kryo.register(String[].class);

    // date/time
    kryo.register(FuzzyDate.class, new FuzzyDateSerializer());
    kryo.register(LocalDate.class);
    kryo.register(LocalDateTime.class);

    // java & commons
    kryo.register(ArrayList.class);
    kryo.register(HashMap.class);
    kryo.register(HashSet.class);
    kryo.register(int[].class);
    kryo.register(LinkedHashMap.class);
    kryo.register(LinkedList.class);
    kryo.register(URI.class, new URISerializer());
    kryo.register(UUID.class, new UUIDSerializer());
    UnmodifiableCollectionsSerializer.registerSerializers( kryo );
    ImmutableListSerializer.registerSerializers(kryo);
    
    // enums
    kryo.register(Country.class);
    kryo.register(DataFormat.class);
    kryo.register(DatasetOrigin.class);
    kryo.register(DatasetType.class);
    kryo.register(DistributionStatus.class);
    kryo.register(EnumMap.class, new EnumMapSerializer());
    kryo.register(EnumSet.class, new EnumSetSerializer());
    kryo.register(Frequency.class);
    kryo.register(Gazetteer.class);
    kryo.register(ImportState.class);
    kryo.register(Issue.class);
    kryo.register(Kingdom.class);
    kryo.register(License.class);
    kryo.register(Lifezone.class);
    kryo.register(MatchType.class);
    kryo.register(MediaType.class);
    kryo.register(NamePart.class);
    kryo.register(NameType.class);
    kryo.register(NomCode.class);
    kryo.register(NomRelType.class);
    kryo.register(NomStatus.class);
    kryo.register(Origin.class);
    kryo.register(Rank.class);
    kryo.register(Sex.class);
    kryo.register(TaxonomicStatus.class);
    kryo.register(TextFormat.class);
    kryo.register(TypeStatus.class);
    
    // term enums
    TermFactory.instance().registerTermEnum(ColdpTerm.class);
    TermFactory.instance().registerTermEnum(ColDwcTerm.class);
    for (Class cl : TermFactory.instance().listRegisteredTermEnums()) {
      kryo.register(cl);
    }
    kryo.register(UnknownTerm.class, new TermSerializer());
    
    return kryo;
  }
}
