package life.catalogue.importer.coldp;

import life.catalogue.api.datapackage.ColdpTerm;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.NomRelType;
import life.catalogue.api.vocab.Origin;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.importer.InterpreterBase;
import life.catalogue.importer.MappingFlags;
import life.catalogue.importer.neo.NeoDb;
import life.catalogue.importer.neo.model.NeoName;
import life.catalogue.importer.neo.model.NeoNameRel;
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

import static life.catalogue.parser.SafeParser.parse;

/**
 * Interprets a verbatim ACEF record and transforms it into a name, taxon and unique references.
 */
public class ColdpInterpreter extends InterpreterBase {
  private static final Logger LOG = LoggerFactory.getLogger(ColdpInterpreter.class);
  private static final EnumNote<TaxonomicStatus> SYN_NOTE = new EnumNote<>(TaxonomicStatus.SYNONYM, null);

  ColdpInterpreter(Dataset dataset, MappingFlags metadata, ReferenceFactory refFactory, NeoDb store) {
    super(dataset, refFactory, store);
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
      u.nameNode = n.node;
      u.setId(v.getRaw(ColdpTerm.ID));
      u.setVerbatimKey(v.getId());
    
      // taxon
      Taxon t = u.getTaxon();
      t.setOrigin(Origin.SOURCE);
      t.setAccordingTo(v.get(ColdpTerm.accordingTo));
      t.setAccordingToDate(fuzzydate(v, Issue.ACCORDING_TO_DATE_INVALID, ColdpTerm.accordingToDate));
      //TODO: ColTerm.accordingToDateID for ORCIDS
      t.setWebpage(uri(v, Issue.URL_INVALID, ColdpTerm.link));
      t.setExtinct(bool(v, Issue.IS_EXTINCT_INVALID, ColdpTerm.extinct));
      t.setRemarks(v.get(ColdpTerm.remarks));
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
  
  private Optional<NeoName> findName(VerbatimRecord v, Term nameId) {
    NeoName n = store.names().objByID(v.getRaw(nameId));
    if (n == null) {
      v.addIssue(Issue.NAME_ID_INVALID);
      v.addIssue(Issue.NOT_INTERPRETED);
      return Optional.empty();
    }
    return Optional.of(n);
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
      u.nameNode = n.node;
      String id = v.get(ColdpTerm.taxonID) + "-" + v.getRaw(ColdpTerm.nameID);
      u.setId(id);
      u.setVerbatimKey(v.getId());
  
      Synonym s = u.getSynonym();
      s.setRemarks(v.get(ColdpTerm.remarks));
      s.setAccordingTo(n.accordingTo);
      return u;
    });
  }
  
  Optional<NeoNameRel> interpretNameRelations(VerbatimRecord rec) {
    NeoNameRel rel = new NeoNameRel();
    SafeParser<NomRelType> type = SafeParser.parse(NomRelTypeParser.PARSER, rec.get(ColdpTerm.type));
    if (type.isPresent()) {
      rel.setType(RelType.from(type.get()));
      rel.setNote(rec.get(ColdpTerm.remarks));
      setReference(rel, rec);
      return Optional.of(rel);
    }
    return Optional.empty();
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
  
  List<Description> interpretDescription(VerbatimRecord rec) {
    return interpretDescription(rec, this::setReference,
        ColdpTerm.description,
        ColdpTerm.category,
        ColdpTerm.format,
        ColdpTerm.language);
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
    Optional<NameAccordingTo> opt = interpretName(v.get(ColdpTerm.ID),
        v.get(ColdpTerm.rank), v.get(ColdpTerm.scientificName), v.get(ColdpTerm.authorship),
        v.get(ColdpTerm.genus), v.get(ColdpTerm.infragenericEpithet), v.get(ColdpTerm.specificEpithet), v.get(ColdpTerm.infraspecificEpithet),
        v.get(ColdpTerm.cultivarEpithet), v.get(ColdpTerm.appendedPhrase),
        v.get(ColdpTerm.code), v.get(ColdpTerm.status),
        v.get(ColdpTerm.typeStatus), v.get(ColdpTerm.typeMaterial),
        v.get(ColdpTerm.link), v.get(ColdpTerm.remarks), v);
    if (opt.isPresent()) {
      // publishedIn & typeReferenceId
      Name n = opt.get().getName();
      setReference(v, ColdpTerm.typeReferenceId, n::setTypeReferenceId);
      setReference(v, ColdpTerm.publishedInID, rid -> {
          n.setPublishedInId(rid);
          n.setPublishedInPage(v.get(ColdpTerm.publishedInPage));
          n.setPublishedInYear(parseYear(ColdpTerm.publishedInYear, v));
      });
      if (v.hasTerm(ColdpTerm.original)) {
        // TODO: what shall we do with the original flag???
      }
    }
    return opt.map(NeoName::new);
  }
  
  private Classification interpretClassification(VerbatimRecord v) {
    Classification cl = new Classification();
    for (ColdpTerm term : ColdpTerm.HIGHER_RANKS) {
      cl.setByTerm(term, v.get(term));
    }
    return cl;
  }
  
  private void setReference(Referenced obj, VerbatimRecord v) {
    super.setReference(v, ColdpTerm.referenceID, obj::setReferenceId);
  }
  
}
