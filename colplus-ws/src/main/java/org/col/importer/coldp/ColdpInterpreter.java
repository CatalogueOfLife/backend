package org.col.importer.coldp;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.col.importer.MappingFlags;
import org.col.importer.InterpreterBase;
import org.col.importer.neo.NeoDb;
import org.col.importer.neo.model.NeoName;
import org.col.importer.neo.model.NeoNameRel;
import org.col.importer.neo.model.NeoUsage;
import org.col.importer.neo.model.RelType;
import org.col.importer.reference.ReferenceFactory;
import org.col.api.datapackage.ColdpTerm;
import org.col.api.model.*;
import org.col.api.vocab.*;
import org.col.parser.EnumNote;
import org.col.parser.NomRelTypeParser;
import org.col.parser.SafeParser;
import org.col.parser.TaxonomicStatusParser;
import org.gbif.dwc.terms.Term;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.col.parser.SafeParser.parse;

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
    return Optional.of(refFactory.fromCol(
        v.get(ColdpTerm.ID),
        v.get(ColdpTerm.author),
        v.get(ColdpTerm.year),
        v.get(ColdpTerm.title),
        v.get(ColdpTerm.source),
        v.get(ColdpTerm.doi),
        v.get(ColdpTerm.link),
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
      u.setVerbatimKey(v.getKey());
    
      // taxon
      Taxon t = u.getTaxon();
      t.setOrigin(Origin.SOURCE);
      t.setAccordingTo(v.get(ColdpTerm.accordingTo));
      t.setAccordingToDate(date(v, Issue.ACCORDING_TO_DATE_INVALID, ColdpTerm.accordingToDate));
      //TODO: ColTerm.accordingToDateID for ORCIDS
      t.setWebpage(uri(v, Issue.URL_INVALID, ColdpTerm.link));
      t.setFossil(bool(v, Issue.IS_FOSSIL_INVALID, ColdpTerm.fossil));
      t.setRecent(bool(v, Issue.IS_RECENT_INVALID, ColdpTerm.recent));
      t.setRemarks(v.get(ColdpTerm.remarks));
      // status
      if (Objects.equals(Boolean.TRUE, bool(v, Issue.PROVISIONAL_STATUS_INVALID, ColdpTerm.provisional))) {
        t.setStatus(TaxonomicStatus.PROVISIONALLY_ACCEPTED);
      }
      // lifezones
      setLifezones(t, v, ColdpTerm.lifezone);
    
      t.setSpeciesEstimate(null);
      t.setSpeciesEstimateReferenceId(null);
    
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
      u.setVerbatimKey(v.getKey());
  
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
        v.get(ColdpTerm.genus), v.get(ColdpTerm.subgenus), v.get(ColdpTerm.specificEpithet), v.get(ColdpTerm.infraspecificEpithet),
        v.get(ColdpTerm.code), v.get(ColdpTerm.status), v.get(ColdpTerm.link), v.get(ColdpTerm.remarks), v);
    return opt.map(NeoName::new);
  }
  
  private Classification interpretClassification(VerbatimRecord v) {
    Classification cl = new Classification();
    cl.setKingdom(v.get(ColdpTerm.kingdom));
    cl.setPhylum(v.get(ColdpTerm.phylum));
    cl.setClass_(v.get(ColdpTerm.class_));
    cl.setOrder(v.get(ColdpTerm.order));
    cl.setSuperfamily(v.get(ColdpTerm.superfamily));
    cl.setFamily(v.get(ColdpTerm.family));
    cl.setGenus(v.get(ColdpTerm.genus));
    cl.setSubgenus(v.get(ColdpTerm.subgenus));
    return cl;
  }
  
  private void setReference(Referenced obj, VerbatimRecord v) {
    if (v.hasTerm(ColdpTerm.referenceID)) {
      Reference r = refFactory.find(v.get(ColdpTerm.referenceID), null);
      if (r != null) {
        obj.setReferenceId(r.getId());
      } else {
        LOG.info("ReferenceID {} not existing but referred from {} {}",
            v.get(ColdpTerm.referenceID),
            obj.getClass().getSimpleName(),
            v.fileLine()
        );
        v.addIssue(Issue.REFERENCE_ID_INVALID);
      }
    }
  }
  
  
}
