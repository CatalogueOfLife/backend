package org.col.admin.importer.coldp;

import java.util.List;
import java.util.Optional;

import org.col.admin.importer.InsertMetadata;
import org.col.admin.importer.InterpreterBase;
import org.col.admin.importer.neo.NeoDb;
import org.col.admin.importer.neo.model.NeoName;
import org.col.admin.importer.neo.model.NeoNameRel;
import org.col.admin.importer.neo.model.NeoUsage;
import org.col.admin.importer.neo.model.RelType;
import org.col.admin.importer.reference.ReferenceFactory;
import org.col.api.datapackage.ColTerm;
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

  ColdpInterpreter(Dataset dataset, InsertMetadata metadata, ReferenceFactory refFactory, NeoDb store) {
    super(dataset, refFactory, store);
    // turn on normalization of flat classification
    metadata.setDenormedClassificationMapped(true);
  }

  Optional<Reference> interpretReference(VerbatimRecord rec) {
    return Optional.of(refFactory.fromCol(
        rec.get(ColTerm.ID),
        rec.get(ColTerm.author),
        rec.get(ColTerm.year),
        rec.get(ColTerm.title),
        rec.get(ColTerm.source),
        rec
    ));
  }
  
  Optional<NeoUsage> interpretTaxon(VerbatimRecord v) {
    return findName(v, ColTerm.nameID).map(n -> {
      
      //TODO: make sure no TAXON label already exists!!!
  
      NeoUsage u = NeoUsage.createTaxon(Origin.SOURCE, false);
      u.node = n.node;
      u.setId(v.get(ColTerm.ID));
      u.setVerbatimKey(v.getKey());
    
      // taxon
      Taxon t = u.getTaxon();
      t.setOrigin(Origin.SOURCE);
      t.setDoubtful(false); //TODO: v.get(ColTerm.provisional)
      t.setAccordingTo(v.get(ColTerm.accordingTo));
      t.setAccordingToDate(date(v, Issue.ACCORDING_TO_DATE_INVALID, ColTerm.accordingToDate));
      //TODO: ColTerm.accordingToDateID for ORCIDS
      t.setDatasetUrl(uri(v, Issue.URL_INVALID, ColTerm.link));
      t.setFossil(bool(v, Issue.IS_FOSSIL_INVALID, ColTerm.fossil));
      t.setRecent(bool(v, Issue.IS_RECENT_INVALID, ColTerm.recent));
      t.setRemarks(v.get(ColTerm.remarks));
    
      // lifezones
      setLifezones(t, v, ColTerm.lifezone);
    
      t.setSpeciesEstimate(null);
      t.setSpeciesEstimateReferenceId(null);
    
      // flat classification for any usage
      u.classification = interpretClassification(v);
    
      return u;
    });
  }
  
  private Optional<NeoName> findName(VerbatimRecord v, Term nameId) {
    NeoName n = store.names().objByID(v.get(nameId));
    if (n == null) {
      v.addIssue(Issue.NAME_ID_INVALID);
      v.addIssue(Issue.NOT_INTERPRETED);
      return Optional.empty();
    }
    return Optional.of(n);
  }
  
  Optional<NeoUsage> interpretSynonym(VerbatimRecord v) {
    return findName(v, ColTerm.nameID).map(n -> {
      TaxonomicStatus status = parse(TaxonomicStatusParser.PARSER, v.get(ColTerm.status)).orElse(SYN_NOTE).val;
      if (!status.isSynonym()) {
        v.addIssue(Issue.TAXONOMIC_STATUS_INVALID);
        // override status as we require some accepted status on Taxon and some synonym status for
        status = TaxonomicStatus.SYNONYM;
      }
  
      NeoUsage u = NeoUsage.createSynonym(Origin.SOURCE, status);
      u.node = n.node;
      u.setId(v.get(ColTerm.ID));
      u.setVerbatimKey(v.getKey());
  
      Synonym s = u.getSynonym();
      s.setRemarks(v.get(ColTerm.remarks));
      s.setAccordingTo(n.accordingTo);
      return u;
    });
  }
  
  Optional<NeoNameRel> interpretNameRelations(VerbatimRecord rec) {
    NeoNameRel rel = new NeoNameRel();
    SafeParser<NomRelType> type = SafeParser.parse(NomRelTypeParser.PARSER, rec.get(ColTerm.type));
    if (type.isPresent()) {
      rel.setType(RelType.from(type.get()));
      rel.setNote(rec.get(ColTerm.remarks));
      setReference(rel, rec);
      return Optional.of(rel);
    }
    return Optional.empty();
  }
  
  List<VernacularName> interpretVernacular(VerbatimRecord rec) {
    return super.interpretVernacular(rec,
        this::setReference,
        ColTerm.name,
        ColTerm.transliteration,
        ColTerm.language,
        ColTerm.country
    );
  }

  List<Distribution> interpretDistribution(VerbatimRecord rec) {
    return super.interpretDistribution(rec, this::setReference,
        ColTerm.area,
        ColTerm.gazetteer,
        ColTerm.status);
  }
  
  List<Description> interpretDescription(VerbatimRecord rec) {
    return interpretDescription(rec, this::setReference,
        ColTerm.description,
        ColTerm.category,
        ColTerm.language);
  }
  
  List<Media> interpretMedia(VerbatimRecord rec) {
    return interpretMedia(rec, this::setReference,
        ColTerm.type,
        ColTerm.url,
        ColTerm.link,
        ColTerm.license,
        ColTerm.creator,
        ColTerm.created,
        ColTerm.title,
        ColTerm.format);
  }

  Optional<NeoName> interpretName(VerbatimRecord v) {
    Optional<NameAccordingTo> opt = interpretName(v.get(ColTerm.ID),
        v.get(ColTerm.rank), v.get(ColTerm.scientificName), v.get(ColTerm.authorship),
        v.get(ColTerm.genus), v.get(ColTerm.subgenus), v.get(ColTerm.specificEpithet), v.get(ColTerm.infraspecificEpithet),
        v.get(ColTerm.code), v.get(ColTerm.status), v.get(ColTerm.link), v.get(ColTerm.remarks), v);
    return opt.map(NeoName::new);
  }
  
  private Classification interpretClassification(VerbatimRecord v) {
    Classification cl = new Classification();
    cl.setKingdom(v.get(ColTerm.kingdom));
    cl.setPhylum(v.get(ColTerm.phylum));
    cl.setClass_(v.get(ColTerm.class_));
    cl.setOrder(v.get(ColTerm.order));
    cl.setSuperfamily(v.get(ColTerm.superfamily));
    cl.setFamily(v.get(ColTerm.family));
    cl.setGenus(v.get(ColTerm.genus));
    cl.setSubgenus(v.get(ColTerm.subgenus));
    return cl;
  }
  
  private void setReference(Referenced obj, VerbatimRecord v) {
    if (v.hasTerm(ColTerm.referenceID)) {
      Reference r = refFactory.find(v.get(ColTerm.referenceID), null);
      if (r != null) {
        obj.setReferenceId(r.getId());
      } else {
        LOG.info("ReferenceID {} not existing but referred from {} {}",
            v.get(ColTerm.referenceID),
            obj.getClass().getSimpleName(),
            v.fileLine()
        );
        v.addIssue(Issue.REFERENCE_ID_INVALID);
      }
    }
  }
  
  
}
