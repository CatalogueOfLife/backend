package life.catalogue.importer.coldp;

import life.catalogue.api.model.*;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.*;
import life.catalogue.coldp.ColdpTerm;
import life.catalogue.csv.MappingInfos;
import life.catalogue.dao.ReferenceFactory;
import life.catalogue.importer.InterpreterBase;
import life.catalogue.importer.store.ImportStore;
import life.catalogue.importer.store.model.NameData;
import life.catalogue.importer.store.model.NameUsageData;
import life.catalogue.importer.store.model.RelationData;
import life.catalogue.importer.store.model.UsageData;
import life.catalogue.interpreter.InterpreterUtils;
import life.catalogue.matching.NameValidator;
import life.catalogue.parser.*;

import org.gbif.dwc.terms.Term;
import org.gbif.nameparser.api.Rank;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;

import static life.catalogue.parser.SafeParser.parse;

/**
 * Interprets a verbatim ColDP record and transforms it into a name, taxon and unique references.
 */
public class ColdpInterpreter extends InterpreterBase {
  private static final EnumNote<TaxonomicStatus> SYN_NOTE = new EnumNote<>(TaxonomicStatus.SYNONYM, null);
  private static final EnumNote<TaxonomicStatus> ACC_NOTE = new EnumNote<>(TaxonomicStatus.ACCEPTED, null);
  private static final Splitter COMMA_SPLITTER = Splitter.on(',').omitEmptyStrings(); // for multi value ID fields

  ColdpInterpreter(DatasetSettings settings, MappingInfos metadata, ReferenceFactory refFactory, ImportStore store) {
    super(settings, refFactory, store, true);
    // turn on normalization of flat classification
    metadata.setDenormedClassificationMapped(true);
  }

  Optional<Reference> interpretReference(VerbatimRecord v) {
    if (!v.hasTerm(ColdpTerm.ID)) {
      return Optional.empty();
    }
    return Optional.of(refFactory.fromColDP(v));
  }

  Optional<NameUsageData> interpretNameUsage(VerbatimRecord v) {
    // name
    return interpretName(v).map(nn -> {
      if (!v.hasTerm(ColdpTerm.ID)) {
        return null;
      }

      UsageData u;
      TaxonomicStatus status = parse(TaxonomicStatusParser.PARSER, v.get(ColdpTerm.status)).orElse(ACC_NOTE, Issue.TAXONOMIC_STATUS_INVALID, v).val;
      if (status.isBareName()) {
        u = UsageData.buildBareName(Origin.SOURCE);
      } else if (status.isSynonym()) {
        u = UsageData.buildSynonym(Origin.SOURCE, status);
      } else {
        u = UsageData.buildTaxon(Origin.SOURCE, status);
        interpretTaxonInfos(u, nn, v);
      }
      var nu = new NameUsageData(nn, u);
      interpretUsageBase(u, nn, v);
      return nu;
    });
  }

  Optional<UsageData> interpretTaxon(VerbatimRecord v) {
    return findName(v, ColdpTerm.nameID).map(n -> {
      if (!v.hasTerm(ColdpTerm.ID)) {
        return null;
      }
      UsageData u = UsageData.buildTaxon(Origin.SOURCE, TaxonomicStatus.ACCEPTED);

      // shared usage base
      interpretUsageBase(u, n, v);

      // taxon
      interpretTaxonInfos(u, n, v);

      return u;
    });
  }

  private void interpretTaxonInfos(UsageData u, NameData n, VerbatimRecord v){
    if (!u.isSynonym()) {
      Taxon t = u.asTaxon();
      t.setOrdinal(v.getInt(ColdpTerm.ordinal, Issue.ORDINAL_INVALID));
      t.setScrutinizer(v.get(ColdpTerm.scrutinizer));
      t.setScrutinizerID(v.get(ColdpTerm.scrutinizerID));
      t.setScrutinizerDate(fuzzydate(v, Issue.SCRUTINIZER_DATE_INVALID, ColdpTerm.scrutinizerDate));
      if (v.hasTerm(ColdpTerm.extinct)) {
        t.setExtinct(bool(v, Issue.IS_EXTINCT_INVALID, ColdpTerm.extinct));
      } else if (n.pnu.isExtinct() || isExtinctBySetting(t.getRank())){
        t.setExtinct(true);
      }
      // geotime
      t.setTemporalRangeStart(parse(GeoTimeParser.PARSER, v.get(ColdpTerm.temporalRangeStart)).orNull(Issue.GEOTIME_INVALID, v));
      t.setTemporalRangeEnd(parse(GeoTimeParser.PARSER, v.get(ColdpTerm.temporalRangeEnd)).orNull(Issue.GEOTIME_INVALID, v));
      // status
      if (Objects.equals(Boolean.TRUE, bool(v, Issue.PROVISIONAL_STATUS_INVALID, ColdpTerm.provisional))) {
        t.setStatus(TaxonomicStatus.PROVISIONALLY_ACCEPTED);
      }
      // environment
      setEnvironment(t, v, ColdpTerm.environment);
    }
    // flat classification for any usage
    u.classification = interpretClassification(v);
  }

  Optional<UsageData> interpretSynonym(VerbatimRecord v) {
    return findName(v, ColdpTerm.nameID).map(n -> {
      TaxonomicStatus status = parse(TaxonomicStatusParser.PARSER, v.get(ColdpTerm.status)).orElse(SYN_NOTE).val;
      if (!status.isSynonym()) {
        v.add(Issue.TAXONOMIC_STATUS_INVALID);
        // override status as we require some accepted status on Taxon and some synonym status for
        status = TaxonomicStatus.SYNONYM;
      }
  
      UsageData u = UsageData.buildSynonym(Origin.SOURCE, status);
      interpretUsageBase(u, n, v);
      if (!v.hasTerm(ColdpTerm.ID)) {
        u.setId(v.getRaw(ColdpTerm.taxonID) + "-" + v.getRaw(ColdpTerm.nameID));
      }
      return u;
    });
  }

  private Optional<NameData> findName(VerbatimRecord v, Term nameId) {
    NameData n = store.names().objByID(v.getRaw(nameId));
    if (n == null) {
      v.add(Issue.NAME_ID_INVALID);
      v.add(Issue.NOT_INTERPRETED);
      return Optional.empty();
    }
    return Optional.of(n);
  }

  private void interpretUsageBase(UsageData u, NameData n, VerbatimRecord v) throws IllegalArgumentException {
    u.setId(v.getRaw(ColdpTerm.ID));
    u.setVerbatimKey(v.getId());
    setReference(v, ColdpTerm.accordingToID, u.usage::setAccordingToId, u.usage::setAccordingTo);
    u.usage.setOrigin(Origin.SOURCE);
    u.usage.setRemarks(getRemarks(v));
    u.usage.setNamePhrase(ObjectUtils.coalesce(v.get(ColdpTerm.namePhrase), n.pnu.getTaxonomicNote()));
    if (!u.usage.isBareName()) {
      NameUsageBase nub = (NameUsageBase) u.usage;
      setReferences(v, ColdpTerm.referenceID, COMMA_SPLITTER, nub::setReferenceIds);
      nub.setLink(uri(v, Issue.URL_INVALID, ColdpTerm.link));
      if (u.isSynonym() && v.hasTerm(ColdpTerm.taxonID)) {
        nub.setParentId(v.getRaw(ColdpTerm.taxonID));
      } else {
        nub.setParentId(v.getRaw(ColdpTerm.parentID));
      }
      nub.setIdentifier(InterpreterUtils.interpretIdentifiers(v.getRaw(ColdpTerm.alternativeID), null, v));
    }
    if (n.pnu.isDoubtful() && u.usage.isTaxon()) {
      u.usage.setStatus(TaxonomicStatus.PROVISIONALLY_ACCEPTED);
    }

    u.nameID = n.getId();
    u.usage.setName(n.getName());
    NameValidator.flagSuspicousPhrase(u.usage.getNamePhrase(), v, Issue.NAME_PHRASE_UNLIKELY);
  }

  Optional<RelationData<NomRelType>> interpretNameRelations(VerbatimRecord rec) {
    return interpretRelations(rec, ColdpTerm.type, NomRelTypeParser.PARSER, ColdpTerm.nameID, ColdpTerm.relatedNameID, ColdpTerm.relatedTaxonScientificName, ColdpTerm.remarks, ColdpTerm.referenceID);
  }

  Optional<RelationData<TaxonConceptRelType>> interpretTaxonRelations(VerbatimRecord rec) {
    return interpretRelations(rec, ColdpTerm.type, TaxonConceptRelTypeParser.PARSER, ColdpTerm.taxonID, ColdpTerm.relatedTaxonID, ColdpTerm.relatedTaxonScientificName, ColdpTerm.remarks, ColdpTerm.referenceID);
  }

  Optional<RelationData<SpeciesInteractionType>> interpretSpeciesInteractions(VerbatimRecord rec) {
    return interpretRelations(rec, ColdpTerm.type, SpeciesInteractionTypeParser.PARSER, ColdpTerm.taxonID, ColdpTerm.relatedTaxonID, ColdpTerm.relatedTaxonScientificName, ColdpTerm.remarks, ColdpTerm.referenceID);
  }

  String getRemarks(VerbatimRecord v) {
    return getRemarks(v, ColdpTerm.remarks);
  }

  Optional<TypeMaterial> interpretTypeMaterial(VerbatimRecord rec) {
    TypeMaterial m = new TypeMaterial();
    m.setId(rec.getRaw(ColdpTerm.ID));
    m.setNameId(rec.getRaw(ColdpTerm.nameID));
    m.setCitation(rec.get(ColdpTerm.citation));
    m.setStatus(SafeParser.parse(TypeStatusParser.PARSER, rec.get(ColdpTerm.status)).orElse(TypeStatus.OTHER, Issue.TYPE_STATUS_INVALID, rec));
    m.setCountry(SafeParser.parse(CountryParser.PARSER, rec.get(ColdpTerm.country)).orNull(Issue.COUNTRY_INVALID, rec));
    m.setLocality(rec.get(ColdpTerm.locality));
    m.setLatitude(rec.get(ColdpTerm.latitude));
    m.setLongitude(rec.get(ColdpTerm.longitude));
    try {
      CoordParser.PARSER.parse(m.getLatitude(), m.getLongitude()).ifPresent(m::setCoordinate);
    } catch (UnparsableException e) {
      rec.add(Issue.LAT_LON_INVALID);
    }
    m.setAltitude(rec.get(ColdpTerm.altitude));
    m.setSex(SafeParser.parse(SexParser.PARSER, rec.get(ColdpTerm.sex)).orNull(Issue.TYPE_MATERIAL_SEX_INVALID, rec));
    m.setInstitutionCode(rec.get(ColdpTerm.institutionCode));
    m.setCatalogNumber(rec.get(ColdpTerm.catalogNumber));
    m.setAssociatedSequences(rec.get(ColdpTerm.associatedSequences));
    m.setHost(rec.get(ColdpTerm.host));
    m.setDate(rec.get(ColdpTerm.date));
    m.setCollector(rec.get(ColdpTerm.collector));
    m.setLink(uri(rec, Issue.URL_INVALID, ColdpTerm.link));
    m.setRemarks(getRemarks(rec));
    setReference(m, rec);
    return Optional.of(m);
  }

  List<VernacularName> interpretVernacular(VerbatimRecord rec) {
    return super.interpretVernacular(rec,
        this::setReference,
        ColdpTerm.name,
        ColdpTerm.transliteration,
        ColdpTerm.preferred,
        ColdpTerm.language,
        ColdpTerm.sex,
        ColdpTerm.remarks,
        ColdpTerm.area,
        ColdpTerm.country
    );
  }

  List<TaxonProperty> interpretProperties(VerbatimRecord rec) {
    return super.interpretProperty(rec,
      this::setReference,
      ColdpTerm.property,
      ColdpTerm.value,
      ColdpTerm.ordinal,
      ColdpTerm.page,
      ColdpTerm.remarks
    );
  }

  List<Distribution> interpretDistribution(VerbatimRecord rec) {
    List<Distribution> dists;
    if (rec.hasTerm(ColdpTerm.areaID)) {
      dists = super.interpretDistributionByGazetteer(rec, this::setReference,
        ColdpTerm.areaID,
        ColdpTerm.gazetteer,
        ColdpTerm.status, // legacy
        ColdpTerm.establishmentMeans,
        ColdpTerm.degreeOfEstablishment,
        ColdpTerm.pathway,
        ColdpTerm.threatStatus,
        ColdpTerm.year,
        ColdpTerm.season,
        ColdpTerm.lifeStage,
        ColdpTerm.remarks
      );

    } else if (rec.hasTerm(ColdpTerm.area)) {
      dists = createDistributions(Gazetteer.TEXT, rec.get(ColdpTerm.area), rec,
        ColdpTerm.status, // legacy
        ColdpTerm.establishmentMeans,
        ColdpTerm.degreeOfEstablishment,
        ColdpTerm.pathway,
        ColdpTerm.threatStatus,
        ColdpTerm.year,
        ColdpTerm.season,
        ColdpTerm.lifeStage,
        ColdpTerm.remarks,
        this::setReference
      );
    } else {
      dists = Collections.emptyList();
    }
    return dists;
  }
  
  List<Media> interpretMedia(VerbatimRecord rec) {
    return interpretMedia(rec, this::setReference,
        ColdpTerm.type,
        ColdpTerm.url,
        ColdpTerm.link,
        ColdpTerm.license,
        ColdpTerm.creator,
        ColdpTerm.created,
        ColdpTerm.title,
        ColdpTerm.format,
        ColdpTerm.remarks
    );
  }

  public List<SpeciesEstimate> interpretEstimate(VerbatimRecord rec) {
    if (rec.hasTerm(ColdpTerm.estimate)) {
      Integer estimate = SafeParser.parse(IntegerParser.PARSER, rec.get(ColdpTerm.estimate)).orNull();
      if (estimate != null) {
        SpeciesEstimate est = new SpeciesEstimate();
        est.setEstimate(estimate);
        est.setVerbatimKey(rec.getId());
        est.setType(SafeParser.parse(EstimateTypeParser.PARSER, rec.get(ColdpTerm.type))
          .orElse(EstimateType.SPECIES_LIVING, Issue.ESTIMATE_TYPE_INVALID, rec));
        setReference(est, rec);
        est.setRemarks(getRemarks(rec));
        return Lists.newArrayList(est);

      } else {
        rec.add(Issue.ESTIMATE_INVALID);
      }
    }
    return Collections.emptyList();
  }

  Optional<VerbatimEntity> interpretAuthor(VerbatimRecord v) {
    //TODO: create model class, implement interpreter & persistence layer
    return Optional.empty();
  }

  Optional<NameData> interpretName(VerbatimRecord v) {
    Term nomStatusTerm = ColdpTerm.status;
    Term genusNameTerm = ColdpTerm.genus;
    Term remarksTerm = ColdpTerm.remarks;
    Term refIdTerm = ColdpTerm.referenceID;
    Term altIdTerm = ColdpTerm.alternativeID;
    if (ColdpTerm.NameUsage.equals(v.getType())) {
      nomStatusTerm = ColdpTerm.nameStatus;
      genusNameTerm = ColdpTerm.genericName;
      remarksTerm = ColdpTerm.nameRemarks;
      refIdTerm = ColdpTerm.nameReferenceID;
      altIdTerm = ColdpTerm.nameAlternativeID;
    } else if(v.hasTerm(ColdpTerm.genericName)) {
      // against COolDP specs, but people do sometimes use genericName also in Name files and we dont wanna break these
      genusNameTerm = ColdpTerm.genericName;
    }

    Optional<ParsedNameUsage> optPNU = nameInterpreter.interpret(v.getRaw(ColdpTerm.ID), v.get(ColdpTerm.rank), Rank.UNRANKED,
        v.get(ColdpTerm.scientificName), v.get(ColdpTerm.authorship), v.get(ColdpTerm.publishedInYear),
        v.get(ColdpTerm.uninomial), v.get(genusNameTerm), v.get(ColdpTerm.infragenericEpithet), v.get(ColdpTerm.specificEpithet), v.get(ColdpTerm.infraspecificEpithet), v.get(ColdpTerm.cultivarEpithet),
        ColdpTerm.combinationAuthorship, ColdpTerm.combinationExAuthorship, ColdpTerm.combinationAuthorshipYear,
        ColdpTerm.basionymAuthorship, ColdpTerm.basionymExAuthorship,ColdpTerm.basionymAuthorshipYear,
        ColdpTerm.notho, ColdpTerm.originalSpelling, ColdpTerm.code, nomStatusTerm,
        ColdpTerm.link, remarksTerm, altIdTerm, v);
    var opt = optPNU.map(NameData::new);
    if (opt.isPresent()) {
      NameData nd = opt.get();
      Name n = nd.getName();
      // etymology, gender & agreement exist only in ColDP
      // for simplicity we interpret them here and not in the base class
      n.setGenderAgreement(bool(v, ColdpTerm.genderAgreement));
      n.setGender(SafeParser.parse(GenderParser.PARSER, v.get(ColdpTerm.gender)).orNull(Issue.GENDER_INVALID, v));
      n.setEtymology(v.get(ColdpTerm.etymology));

      // explicit basionym
      nd.basionymID = v.getRawButNot(ColdpTerm.basionymID, n.getId());

      // publishedIn
      n.setPublishedInPageLink(v.get(ColdpTerm.publishedInPageLink));
      setReference(v, refIdTerm, rid -> {
          n.setPublishedInId(rid);
          n.setPublishedInPage(v.get(ColdpTerm.publishedInPage));
          n.setPublishedInYear(InterpreterUtils.parseNomenYear(ColdpTerm.publishedInYear, v));
      });
      if (optPNU.get().getPublishedIn() != null) {
        String pubInAuthorship = optPNU.get().getPublishedIn();
        if (n.getPublishedInId() == null) {
          setPublishedIn(n, pubInAuthorship, v);
        } else {
          //TODO: compare and raise issue: https://github.com/CatalogueOfLife/backend/issues/743
        }
      }
    }
    return opt;
  }
  
  private Classification interpretClassification(VerbatimRecord v) {
    Classification cl = new Classification();
    for (ColdpTerm term : ColdpTerm.DENORMALIZED_RANKS) {
      cl.setByTerm(term, v.get(term));
    }
    return cl;
  }
  
  private void setReference(Referenced obj, VerbatimRecord v) {
    super.setReference(v, ColdpTerm.referenceID, obj::setReferenceId);
  }

}
