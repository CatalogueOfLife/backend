package life.catalogue.common.fury;

import life.catalogue.api.model.*;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.api.search.SimpleDecision;
import life.catalogue.api.vocab.*;
import life.catalogue.api.vocab.terms.*;
import life.catalogue.coldp.ColdpTerm;
import life.catalogue.common.date.FuzzyDate;

import org.gbif.dwc.terms.BibTexTerm;
import org.gbif.dwc.terms.TermFactory;
import org.gbif.dwc.terms.UnknownTerm;
import org.gbif.nameparser.api.*;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import org.apache.fury.Fury;
import org.apache.fury.ThreadLocalFury;
import org.apache.fury.ThreadSafeFury;

import de.undercouch.citeproc.csl.CSLType;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;


/**
 * Creates a thread safe fury instance with pre registered API classes
 */
public class FuryFactory {

  public static final ThreadSafeFury FURY = new ThreadLocalFury(classLoader -> {
    Fury f = Fury.builder()
      .withLanguage(org.apache.fury.config.Language.JAVA)
      .withClassLoader(classLoader)
      .requireClassRegistration(false) // some non public class like JumboEnumSet cannot be registered
      .build();
    return configure(f);
  });

  public static Fury configure(Fury fury) {
    // clb core
    fury.register(Agent.class);
    fury.register(Authorship.class);
    fury.register(BareName.class);
    fury.register(Citation.class);
    fury.register(Classification.class);
    fury.register(Coordinate.class);
    fury.register(DOI.class);
    fury.register(Dataset.class);
    fury.register(DatasetImport.class);
    fury.register(Distribution.class);
    fury.register(EditorialDecision.Mode.class);
    fury.register(EditorialDecision.class);
    fury.register(Identifier.class);
    fury.register(IndexName.class);
    fury.register(Media.class);
    fury.register(Name.class);
    fury.register(NameRelation.class);
    fury.register(NameUsageWrapper.class);
    fury.register(Page.class);
    fury.register(ParsedName.State.class);
    fury.register(ParsedName.class);
    fury.register(ParsedNameUsage.class);
    fury.register(Reference.class);
    fury.register(Sector.Mode.class);
    fury.register(Sector.class);
    fury.register(SimpleDecision.class);
    fury.register(SimpleName.class);
    fury.register(SpeciesEstimate.class);
    fury.register(SpeciesInteraction.class);
    fury.register(Synonym.class);
    fury.register(TaxGroup.class);
    fury.register(Taxon.class);
    fury.register(TaxonConceptRelation.class);
    fury.register(TaxonProperty.class);
    fury.register(Treatment.class);
    fury.register(TypeMaterial.class);
    fury.register(VerbatimRecord.class);
    fury.register(VernacularName.class);

    // search
    fury.register(NameUsageWrapper.class);
    fury.register(SimpleDecision.class);

    // CSL classes & enums
    fury.register(CslData.class);
    fury.register(CslDate.class);
    fury.register(CslName.class);
    fury.register(CslName[].class);
    fury.register(CSLType.class);
    fury.register(int[][].class);
    fury.register(String[].class);

    // date/time
    fury.register(FuzzyDate.class);
    fury.register(LocalDate.class);
    fury.register(LocalDateTime.class);

    // java & commons
    fury.register(int[].class);
    fury.register(URI.class);
    fury.register(UUID.class);
    registerCollectionClasses(fury);

    // areas
    fury.register(Area.class);
    fury.register(AreaImpl.class);
    fury.register(LonghurstArea.class);
    fury.register(TdwgArea.class);

    // enums
    fury.register(Country.class);
    fury.register(DataFormat.class);
    fury.register(DatasetOrigin.class);
    fury.register(Setting.class);
    fury.register(DatasetType.class);
    fury.register(DistributionStatus.class);
    fury.register(EnumMap.class);
    fury.register(EnumSet.class);
    fury.register(EstimateType.class);
    fury.register(Frequency.class);
    fury.register(Gender.class);
    fury.register(Gazetteer.class);
    fury.register(GeoTime.class);
    fury.register(GeoTimeType.class);
    fury.register(ImportState.class);
    fury.register(Issue.class);
    fury.register(JobStatus.class);
    fury.register(License.class);
    fury.register(Environment.class);
    fury.register(MatchType.class);
    fury.register(MediaType.class);
    fury.register(NamePart.class);
    fury.register(NameType.class);
    fury.register(NomCode.class);
    fury.register(NomRelType.class);
    fury.register(NomStatus.class);
    fury.register(Origin.class);
    fury.register(Rank.class);
    fury.register(Sex.class);
    fury.register(SpeciesInteractionType.class);
    fury.register(TaxonomicStatus.class);
    fury.register(TaxonConceptRelType.class);
    fury.register(TreatmentFormat.class);
    fury.register(TypeStatus.class);
    fury.register(InfoGroup.class);

    // term enums
    TermFactory.instance().registerTermEnum(BiboOntTerm.class);
    TermFactory.instance().registerTermEnum(ColdpTerm.class);
    TermFactory.instance().registerTermEnum(EolDocumentTerm.class);
    TermFactory.instance().registerTermEnum(EolReferenceTerm.class);
    TermFactory.instance().registerTermEnum(InatTerm.class);
    TermFactory.instance().registerTermEnum(TxtTreeTerm.class);
    TermFactory.instance().registerTermEnum(WfoTerm.class);
    for (Class<?> cl : TermFactory.instance().listRegisteredTermEnums()) {
      fury.register(cl);
    }
    fury.register(UnknownTerm.class);
    fury.register(BibTexTerm.class);
    return fury;
  }

  public static void registerCollectionClasses(Fury fury) {
    fury.register(ArrayList.class);
    fury.register(HashMap.class);
    fury.register(HashSet.class);
    fury.register(EnumMap.class);
    fury.register(EnumSet.class);
    fury.register(LinkedHashMap.class);
    fury.register(LinkedList.class);
    fury.register(Collections.emptyList().getClass());
    // private class, special registration
    try {
      Class clazz = Class.forName("java.util.Arrays$ArrayList");
      fury.register(clazz);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
    // fastutils
    fury.register(IntSet.class);
    fury.register(IntOpenHashSet.class);
    fury.register(ObjectArrayList.class);
  }
}
