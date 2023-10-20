package life.catalogue.importer.dwca;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.*;
import life.catalogue.api.vocab.terms.InatTerm;
import life.catalogue.api.vocab.terms.WfoTerm;
import life.catalogue.coldp.ColdpTerm;
import life.catalogue.csv.MappingInfos;
import life.catalogue.dao.ReferenceFactory;
import life.catalogue.importer.InterpreterBase;
import life.catalogue.importer.neo.NeoDb;
import life.catalogue.importer.neo.model.NeoRel;
import life.catalogue.importer.neo.model.NeoUsage;
import life.catalogue.importer.neo.model.RelType;
import life.catalogue.parser.*;

import org.gbif.dwc.terms.*;

import java.util.*;

import org.gbif.nameparser.api.Rank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

/**
 * Interprets a verbatim record and transforms it into a name, taxon and unique references.
 */
public class DwcInterpreter extends InterpreterBase {
  private static final Logger LOG = LoggerFactory.getLogger(DwcInterpreter.class);
  private static final Map<Term, Identifier.Scope> ALT_ID_TERMS = new HashMap<>();
  static {
    ALT_ID_TERMS.put(DwcTerm.taxonConceptID, null);
    ALT_ID_TERMS.put(WfoTerm.wfoID, Identifier.Scope.WFO);
    ALT_ID_TERMS.put(WfoTerm.tplID, Identifier.Scope.TPL);
    ALT_ID_TERMS.put(WfoTerm.ipniID, Identifier.Scope.IPNI);
  }

  private final MappingInfos mappingFlags;
  private final Term idTerm;
  private final Map<String, String> dwcaID2taxonID = new HashMap<>();

  public DwcInterpreter(DatasetSettings settings, MappingInfos mappingFlags, ReferenceFactory refFactory, NeoDb store) {
    super(settings, refFactory, store, false);
    this.mappingFlags = mappingFlags;
    idTerm = mappingFlags.hasTaxonId() ? DwcTerm.taxonID : DwcaTerm.ID;
  }

  public Optional<NeoUsage> interpretUsage(VerbatimRecord v) {
    // name
    return interpretName(v).map(pnu -> {
      NeoUsage u = interpretUsage(idTerm, pnu, DwcTerm.taxonomicStatus, TaxonomicStatus.ACCEPTED, v, ALT_ID_TERMS);
      if (idTerm == DwcTerm.taxonID) {
        // remember dwca ids for extension lookups
        var dwcaID = v.getRaw(DwcaTerm.ID);
        if (!u.getId().equals(dwcaID)) {
          dwcaID2taxonID.put(dwcaID, u.getId());
        }
      }
      if (u.isNameUsageBase()) {
        u.asNameUsageBase().setLink(uri(v, Issue.URL_INVALID, DcTerm.references));
        if (!u.isSynonym()) {
          Taxon tax = u.asTaxon();
          tax.setExtinct(null);
          // TODO: lifezones come through the species profile extension.
        }
        // explicit accordingTo & namePhrase - the authorship could already have set these properties!
        if (v.hasTerm(DwcTerm.nameAccordingTo)) {
          setAccordingTo(u.usage, v.get(DwcTerm.nameAccordingTo), v);
        }
      }
      u.usage.setRemarks(v.get(DwcTerm.taxonRemarks));
      return u;
    });
  }


  public List<Taxon> interpretSpeciesProfile(VerbatimRecord v) {
    Taxon t = new Taxon();
    boolean hasData = false;
    if (v.hasTerm(GbifTerm.isExtinct)) {
      t.setExtinct(bool(v, GbifTerm.isExtinct));
      hasData = true;
    }
    Set<Environment> envs = new HashSet<>();
    addEnv(v, GbifTerm.isFreshwater, Environment.FRESHWATER, envs);
    addEnv(v, GbifTerm.isMarine, Environment.MARINE, envs);
    addEnv(v, GbifTerm.isTerrestrial, Environment.TERRESTRIAL, envs);

    if (v.hasTerm(GbifTerm.livingPeriod)) {
      t.setTemporalRangeStart(v.get(GbifTerm.livingPeriod));
      hasData = true;
    }

    if (hasData || !envs.isEmpty()) {
      if (!envs.isEmpty()) {
        t.setEnvironments(envs);
      }
      return List.of(t);
    }
    return Collections.emptyList();
  }

  private void addEnv(VerbatimRecord v, GbifTerm term, Environment env, Set<Environment> envs) {
    if (v.hasTerm(term)) {
      if (bool(v, false, term)) {
        envs.add(env);
      }
    }
  }

  /**
   * Return the taxonID for the given record by preferring explicit taxonID values over dwca.ID values that make use of the
   * lookup from core records.
   */
  String taxonID(VerbatimRecord v) {
    if (v.hasTerm(DwcTerm.taxonID)) {
      return v.getRaw(DwcTerm.taxonID);
    }
    String dwcaID = v.getRaw(DwcaTerm.ID);
    if (dwcaID2taxonID.containsKey(dwcaID)) {
      return dwcaID2taxonID.get(dwcaID);
    }
    return dwcaID;
  }

  Optional<NeoRel> interpretNameRelations(VerbatimRecord rec) {
    NeoRel rel = new NeoRel();
    SafeParser<NomRelType> type = SafeParser.parse(NomRelTypeParser.PARSER, rec.get(ColdpTerm.type));
    if (type.isPresent()) {
      rel.setType(RelType.from(type.get()));
      rel.setRemarks(replaceHtml(rec.get(ColdpTerm.remarks), true));
      if (rec.hasTerm(DcTerm.bibliographicCitation)) {
        Reference ref = refFactory.fromDWC(rec.get(ColdpTerm.referenceID), rec.get(DcTerm.bibliographicCitation), null, rec);
        rel.setReferenceId(ref.getId());
      }
      return Optional.of(rel);
    }
    return Optional.empty();
  }
  
  List<Reference> interpretReference(VerbatimRecord rec) {
    var r = Lists.newArrayList(refFactory.fromDC(rec.getRaw(DcTerm.identifier),
        rec.get(DcTerm.bibliographicCitation),
        rec.get(DcTerm.creator),
        rec.get(DcTerm.date),
        rec.get(DcTerm.title),
        rec.get(DcTerm.source),
        rec
    ));
    return r;
  }

  /**
   * As used by Plazi
   */
  List<Reference> interpretEolReference(VerbatimRecord v) {
    return Lists.newArrayList(refFactory.fromEOL(v));
  }
  
  List<Distribution> interpretDistribution(VerbatimRecord rec) {
    // try to figure out an area
    if (rec.hasTerm(DwcTerm.locationID)) {
      return createDistributions(null,
          rec.getRaw(DwcTerm.locationID),
          rec.get(DwcTerm.occurrenceStatus),
          rec, DwcTerm.occurrenceRemarks, this::setReference);
      
    } else if (rec.hasTerm(DwcTerm.countryCode) || rec.hasTerm(DwcTerm.country)) {
      return createDistributions(Gazetteer.ISO,
          rec.getFirst(DwcTerm.countryCode, DwcTerm.country),
          rec.get(DwcTerm.occurrenceStatus),
        rec, DwcTerm.occurrenceRemarks, this::setReference);
      
    } else if (rec.hasTerm(DwcTerm.locality)) {
      return createDistributions(Gazetteer.TEXT,
          rec.get(DwcTerm.locality),
          rec.get(DwcTerm.occurrenceStatus),
        rec, DwcTerm.occurrenceRemarks, this::setReference);
      
    } else {
      rec.addIssue(Issue.DISTRIBUTION_INVALID);
      return Collections.emptyList();
    }
  }
  
  List<VernacularName> interpretVernacularName(VerbatimRecord rec) {
    var vns = super.interpretVernacular(rec,
        this::setReference,
        DwcTerm.vernacularName,
        null,
        DcTerm.language,
        DwcTerm.sex,
        DwcTerm.taxonRemarks,
        DwcTerm.locality,
        DwcTerm.countryCode, DwcTerm.country
    );
    for (var vn : vns) {
      // try with iNat lexicon - a specific iNat hack
      if ((vn.getLanguage() == null || vn.getLanguage().equalsIgnoreCase("und")) && rec.hasTerm(InatTerm.lexicon)) {
        vn.setLanguage(SafeParser.parse(LanguageParser.PARSER, rec.get(InatTerm.lexicon)).orNull());
      }
    }
    return vns;
  }

  List<Media> interpretGbifMedia(VerbatimRecord rec) {
    return interpretMedia(rec, this::setReference,
        DcTerm.type,
        DcTerm.identifier,
        DcTerm.references,
        DcTerm.license,
        DcTerm.creator,
        DcTerm.created,
        DcTerm.title,
        DcTerm.format,
        DcTerm.description
    );
  }

  List<Media> interpretAcMedia(VerbatimRecord rec) {
    return interpretMedia(rec, this::setReference,
      Set.of(DcTerm.type, DcElement.type),
      Set.of(AcTerm.accessURI),
      Set.of(AcTerm.furtherInformationURL, DcTerm.references),
      Set.of(DcTerm.license, DcTerm.rights),
      Set.of(DcTerm.creator, DcElement.creator),
      Set.of(XmpTerm.CreateDate, DcTerm.created),
      Set.of(DcTerm.title, DcElement.title),
      Set.of(DcTerm.format),
      Set.of(AcTerm.comments, AcTerm.reviewerComments)
    );
  }

  /**
   * Reads the dc:source citation string and looks up or creates a new reference.
   * Sets the reference id of the referenced object.
   * @param obj
   * @param v
   */
  private void setReference(Referenced obj, VerbatimRecord v) {
    if (v.hasTerm(DcTerm.source)) {
      Reference ref = refFactory.fromCitation(null, v.get(DcTerm.source), v);
      if (ref != null) {
        if (ref.getVerbatimKey() == null) {
          // create new reference with verbatim key, we've never seen this before!
          ref.setVerbatimKey(v.getId());
          store.references().create(ref);
        }
        obj.setReferenceId(ref.getId());
      }
    }
  }

  private Optional<ParsedNameUsage> interpretName(VerbatimRecord v) {
    Optional<ParsedNameUsage> opt = nameInterpreter.interpret(taxonID(v), v.getFirst(DwcTerm.taxonRank, DwcTerm.verbatimTaxonRank), Rank.UNRANKED,
      v.get(DwcTerm.scientificName),v.get(DwcTerm.scientificNameAuthorship), v.get(DwcTerm.namePublishedInYear),
      null, v.getFirst(DwcTerm.genericName, DwcTerm.genus), v.getFirst(DwcTerm.infragenericEpithet),
      v.get(DwcTerm.specificEpithet), v.get(DwcTerm.infraspecificEpithet), v.get(DwcTerm.cultivarEpithet),
      null, null,null, null,null, null,
      null, null, DwcTerm.nomenclaturalCode, DwcTerm.nomenclaturalStatus,
      DcTerm.references, null, DwcTerm.scientificNameID, v
    );

    // publishedIn
    if (opt.isPresent()) {
      Name n = opt.get().getName();
      if (v.hasTerm(DwcTerm.namePublishedInID) || v.hasTerm(DwcTerm.namePublishedIn)) {
        Reference ref = refFactory.fromDWC(v.getRaw(DwcTerm.namePublishedInID), v.get(DwcTerm.namePublishedIn), v.get(DwcTerm.namePublishedInYear), v);
        if (ref != null) {
          if (ref.getVerbatimKey() == null) {
            // create new reference with verbatim key, we've never seen this before!
            ref.setVerbatimKey(v.getId());
            store.references().create(ref);
          }
          n.setPublishedInId(ref.getId());
          n.setPublishedInPage(ref.getPage());
        }
      }
    }
    return opt;
  }

  /**
   * Converts occurrences with type status to TypeMaterial instances
   */
  Optional<TypeMaterial> interpretTypeMaterial(VerbatimRecord rec) {
    if (rec.hasTerm(DwcTerm.typeStatus)) {
      TypeMaterial m = new TypeMaterial();
      m.setId(rec.getRaw(DwcTerm.occurrenceID));
      m.setNameId(taxonID(rec)); // needs to point to a Name.ID !!!
      m.setCitation(rec.get(DwcTerm.verbatimLabel));
      m.setStatus(SafeParser.parse(TypeStatusParser.PARSER, rec.get(DwcTerm.typeStatus)).orElse(TypeStatus.OTHER, Issue.TYPE_STATUS_INVALID, rec));
      m.setLocality(rec.get(DwcTerm.locality));
      m.setCountry(SafeParser.parse(CountryParser.PARSER, rec.get(DwcTerm.country)).orNull(Issue.COUNTRY_INVALID, rec));
      try {
        CoordParser.PARSER.parse(rec.get(DwcTerm.decimalLatitude), rec.get(DwcTerm.decimalLongitude)).ifPresent(m::setCoordinate);
      } catch (UnparsableException e) {
        rec.addIssue(Issue.LAT_LON_INVALID);
      }
      m.setAltitude(rec.getFirst(DwcTerm.minimumElevationInMeters, DwcTerm.maximumElevationInMeters));
      m.setSex(SafeParser.parse(SexParser.PARSER, rec.get(DwcTerm.sex)).orNull(Issue.TYPE_MATERIAL_SEX_INVALID, rec));
      m.setInstitutionCode(rec.get(DwcTerm.institutionCode));
      m.setCatalogNumber(rec.get(DwcTerm.catalogNumber));
      m.setAssociatedSequences(rec.get(DwcTerm.associatedSequences));
      m.setHost(null);
      m.setDate(rec.get(DwcTerm.eventDate));
      m.setCollector(rec.get(DwcTerm.recordedBy));
      m.setLink(uri(rec, Issue.URL_INVALID, DcTerm.references));
      // pool other infos in remarks
      m.addRemarks(rec.get(DwcTerm.individualCount));
      m.addRemarks(rec.get(DwcTerm.sex));
      setReference(m, rec);
      return Optional.of(m);
    }
    return Optional.empty();
  }

  public Optional<Treatment> interpretTreatment(VerbatimRecord v) {
    if (v.hasTerm(DcTerm.description)) {
      Treatment t = new Treatment();
      t.setId(v.getFirstRaw(DwcTerm.taxonID, DwcaTerm.ID));
      t.setFormat(TreatmentFormat.HTML);
      t.setDocument(v.getRaw(DcTerm.description));
      return Optional.of(t);
    }
    return Optional.empty();
  }
}
