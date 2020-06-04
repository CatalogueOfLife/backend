package life.catalogue.importer.coldp;

import life.catalogue.api.datapackage.ColdpTerm;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.Origin;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.api.vocab.TypeStatus;
import life.catalogue.importer.InterpreterBase;
import life.catalogue.importer.MappingFlags;
import life.catalogue.importer.neo.NeoDb;
import life.catalogue.importer.neo.model.NeoName;
import life.catalogue.importer.neo.model.NeoRel;
import life.catalogue.importer.neo.model.NeoUsage;
import life.catalogue.importer.neo.model.RelType;
import life.catalogue.importer.reference.ReferenceFactory;
import life.catalogue.parser.*;
import org.gbif.dwc.terms.Term;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import static life.catalogue.parser.SafeParser.parse;

/**
 * Interprets a verbatim ACEF record and transforms it into a name, taxon and unique references.
 */
public class ColdpInterpreter extends InterpreterBase {
  private static final Logger LOG = LoggerFactory.getLogger(ColdpInterpreter.class);
  private static final EnumNote<TaxonomicStatus> SYN_NOTE = new EnumNote<>(TaxonomicStatus.SYNONYM, null);

  ColdpInterpreter(DatasetSettings settings, MappingFlags metadata, ReferenceFactory refFactory, NeoDb store) {
    super(settings, refFactory, store);
    // turn on normalization of flat classification
    metadata.setDenormedClassificationMapped(true);
  }

  Optional<Reference> interpretReference(VerbatimRecord v) {
    if (!v.hasTerm(ColdpTerm.ID)) {
      return Optional.empty();
    }
    return Optional.of(refFactory.fromColDP(
        v.get(ColdpTerm.ID),
        v.get(ColdpTerm.citation),
        v.get(ColdpTerm.author),
        v.get(ColdpTerm.year),
        v.get(ColdpTerm.title),
        v.get(ColdpTerm.source),
        v.get(ColdpTerm.details),
        v.get(ColdpTerm.doi),
        v.get(ColdpTerm.link),
        v.get(ColdpTerm.remarks),
        v
    ));
  }
  
  Optional<NeoUsage> interpretTaxon(VerbatimRecord v) {
    return findName(v, ColdpTerm.nameID).map(n -> {
      if (!v.hasTerm(ColdpTerm.ID)) {
        return null;
      }
      //TODO: make sure no TAXON label already exists!!!
      NeoUsage u = NeoUsage.createTaxon(Origin.SOURCE, TaxonomicStatus.ACCEPTED);

      // shared usage base
      interpretUsageBase(u, n, v);

      // taxon
      Taxon t = u.getTaxon();
      t.setScrutinizer(v.get(ColdpTerm.scrutinizer));
      t.setScrutinizerDate(fuzzydate(v, Issue.SCRUTINIZER_DATE_INVALID, ColdpTerm.scrutinizerDate));
      t.setExtinct(bool(v, Issue.IS_EXTINCT_INVALID, ColdpTerm.extinct));
      // geotime
      t.setTemporalRangeStart(parse(GeoTimeParser.PARSER, v.get(ColdpTerm.temporalRangeStart)).orNull(Issue.GEOTIME_INVALID, v));
      t.setTemporalRangeEnd(parse(GeoTimeParser.PARSER, v.get(ColdpTerm.temporalRangeEnd)).orNull(Issue.GEOTIME_INVALID, v));
      // status
      if (Objects.equals(Boolean.TRUE, bool(v, Issue.PROVISIONAL_STATUS_INVALID, ColdpTerm.provisional))) {
        t.setStatus(TaxonomicStatus.PROVISIONALLY_ACCEPTED);
      }
      // lifezones
      setLifezones(t, v, ColdpTerm.lifezone);
    
      // flat classification for any usage
      u.classification = interpretClassification(v);
    
      return u;
    });
  }
  
  Optional<NeoUsage> interpretSynonym(VerbatimRecord v) {
    return findName(v, ColdpTerm.nameID).map(n -> {
      TaxonomicStatus status = parse(TaxonomicStatusParser.PARSER, v.get(ColdpTerm.status)).orElse(SYN_NOTE).val;
      if (!status.isSynonym()) {
        v.addIssue(Issue.TAXONOMIC_STATUS_INVALID);
        // override status as we require some accepted status on Taxon and some synonym status for
        status = TaxonomicStatus.SYNONYM;
      }
  
      NeoUsage u = NeoUsage.createSynonym(Origin.SOURCE, status);
      interpretUsageBase(u, n, v);
      if (!v.hasTerm(ColdpTerm.ID)) {
        u.setId(v.getRaw(ColdpTerm.taxonID) + "-" + v.getRaw(ColdpTerm.nameID));
      }
      return u;
    });
  }

  private Optional<NeoName> findName(VerbatimRecord v, Term nameId) {
    NeoName n = store.names().objByID(v.getRaw(nameId));
    if (n == null) {
      v.addIssue(Issue.NAME_ID_INVALID);
      v.addIssue(Issue.NOT_INTERPRETED);
      return Optional.empty();
    }
    return Optional.of(n);
  }

  private void interpretUsageBase(NeoUsage u, NeoName n, VerbatimRecord v) {
    u.nameNode = n.node;
    u.setId(v.getRaw(ColdpTerm.ID));
    u.setVerbatimKey(v.getId());
    setReference(v, ColdpTerm.accordingToID, u.usage::setAccordingToId);
    u.usage.setOrigin(Origin.SOURCE);
    u.usage.setNamePhrase( v.get(ColdpTerm.namePhrase));
    u.usage.setLink(uri(v, Issue.URL_INVALID, ColdpTerm.link));
    u.usage.setRemarks(v.get(ColdpTerm.remarks));
  }

  Optional<NeoRel> interpretNameRelations(VerbatimRecord rec) {
    return interpretRelations(rec, NomRelTypeParser.PARSER, RelType::from);
  }

  Optional<NeoRel> interpretTaxonRelations(VerbatimRecord rec) {
    return interpretRelations(rec, TaxRelTypeParser.PARSER, RelType::from);
  }

  <T extends Enum> Optional<NeoRel> interpretRelations(VerbatimRecord rec, EnumParser<T> parser, Function<T, RelType> typeFunction) {
    NeoRel rel = new NeoRel();
    SafeParser<T> type = SafeParser.parse(parser, rec.get(ColdpTerm.type));
    if (type.isPresent()) {
      rel.setType(typeFunction.apply(type.get()));
      rel.setRemarks(rec.get(ColdpTerm.remarks));
      setReference(rel, rec);
      return Optional.of(rel);
    }
    return Optional.empty();
  }

  Optional<TypeMaterial> interpretTypeMaterial(VerbatimRecord rec) {
    TypeMaterial m = new TypeMaterial();
    m.setId(rec.getRaw(ColdpTerm.ID));
    m.setNameId(rec.getRaw(ColdpTerm.nameID));
    m.setCitation(rec.get(ColdpTerm.citation));
    m.setStatus(SafeParser.parse(TypeStatusParser.PARSER, rec.get(ColdpTerm.status)).orElse(TypeStatus.OTHER, Issue.TYPE_STATUS_INVALID, rec));
    m.setLocality(rec.get(ColdpTerm.locality));
    m.setCountry(SafeParser.parse(CountryParser.PARSER, rec.get(ColdpTerm.country)).orNull(Issue.COUNTRY_INVALID, rec));
    try {
      Optional<CoordParser.LatLon> coord = CoordParser.PARSER.parse(rec.get(ColdpTerm.latitude), rec.get(ColdpTerm.longitude));
      if (coord.isPresent()) {
        m.setLatitude(coord.get().lat);
        m.setLongitude(coord.get().lon);
      }
    } catch (UnparsableException e) {
      rec.addIssue(Issue.LAT_LON_INVALID);
    }
    m.setAltitude(integer(rec, Issue.ALTITUDE_INVALID, ColdpTerm.altitude));
    m.setHost(rec.get(ColdpTerm.host));
    m.setDate(rec.get(ColdpTerm.date));
    m.setCollector(rec.get(ColdpTerm.collector));
    m.setLink(uri(rec, Issue.URL_INVALID, ColdpTerm.link));
    m.setRemarks(rec.get(ColdpTerm.remarks));
    setReference(m, rec);
    return Optional.of(m);
  }

  List<VernacularName> interpretVernacular(VerbatimRecord rec) {
    return super.interpretVernacular(rec,
        this::setReference,
        ColdpTerm.name,
        ColdpTerm.transliteration,
        ColdpTerm.language,
        ColdpTerm.sex,
        ColdpTerm.area,
        ColdpTerm.country
    );
  }

  List<Distribution> interpretDistribution(VerbatimRecord rec) {
    return super.interpretDistribution(rec, this::setReference,
        ColdpTerm.area,
        ColdpTerm.gazetteer,
        ColdpTerm.status);
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
        ColdpTerm.format);
  }

  Optional<NeoName> interpretName(VerbatimRecord v) {
    Optional<ParsedNameUsage> opt = interpretName(true, v.get(ColdpTerm.ID),
        v.get(ColdpTerm.rank), v.get(ColdpTerm.scientificName), v.get(ColdpTerm.authorship),
        v.get(ColdpTerm.genus), v.get(ColdpTerm.infragenericEpithet), v.get(ColdpTerm.specificEpithet), v.get(ColdpTerm.infraspecificEpithet),
        v.get(ColdpTerm.cultivarEpithet),
        v.get(ColdpTerm.code), v.get(ColdpTerm.status),
        v.get(ColdpTerm.link), v.get(ColdpTerm.remarks), v);
    if (opt.isPresent()) {
      // publishedIn
      Name n = opt.get().getName();
      setReference(v, ColdpTerm.publishedInID, rid -> {
          n.setPublishedInId(rid);
          n.setPublishedInPage(v.get(ColdpTerm.publishedInPage));
          n.setPublishedInYear(parseYear(ColdpTerm.publishedInYear, v));
      });
      if (opt.get().getPublishedIn() == null) {
        String pubInAuthorship = opt.get().getPublishedIn();
        if (n.getPublishedInId() == null) {
          setPublishedIn(n, pubInAuthorship, v);
        } else {
          //TODO: compare and raise issue: https://github.com/CatalogueOfLife/backend/issues/743
        }
      }
    }
    return opt.map(NeoName::new);
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
