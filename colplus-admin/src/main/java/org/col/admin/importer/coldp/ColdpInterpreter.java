package org.col.admin.importer.coldp;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.Lists;
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
import org.col.parser.*;
import org.gbif.dwc.terms.AcefTerm;
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
    return Optional.of(refFactory.fromACEF(
        rec.get(AcefTerm.ReferenceID),
        rec.get(AcefTerm.Author),
        rec.get(AcefTerm.Year),
        rec.get(AcefTerm.Title),
        rec.get(AcefTerm.Details),
        rec
    ));
  }
  
  Optional<NeoUsage> interpretSynonym(VerbatimRecord v) {
    TaxonomicStatus status = parse(TaxonomicStatusParser.PARSER, v.get(ColTerm.status)).orElse(SYN_NOTE).val;
    if (!status.isSynonym()) {
      v.addIssue(Issue.TAXONOMIC_STATUS_INVALID);
      // override status as we require some accepted status on Taxon and some synonym status for
      status = TaxonomicStatus.SYNONYM;
    }
    
    NeoName nn = store.names().objByID(v.get(ColTerm.nameID));
    if (nn == null) {
      v.addIssue(Issue.NAME_ID_INVALID);
      return Optional.empty();
    }
  
    NeoUsage u = NeoUsage.createSynonym(Origin.SOURCE, nn.name, status);
    u.setId(v.get(ColTerm.ID));
    u.setVerbatimKey(v.getKey());

    Synonym s = u.getSynonym();
    s.setRemarks(v.get(ColTerm.remarks));
    s.setAccordingTo(nn.accordingTo);
    return Optional.of(u);
  }
  
  Optional<NeoNameRel> interpretNameRelations(VerbatimRecord rec) {
    NeoNameRel rel = new NeoNameRel();
    SafeParser<NomRelType> type = SafeParser.parse(NomRelTypeParser.PARSER, rec.get(ColDwcTerm.relationType));
    if (type.isPresent()) {
      rel.setType(RelType.from(type.get()));
      rel.setNote(rec.get(ColDwcTerm.relationRemarks));
      if (rec.hasTerm(ColDwcTerm.publishedIn)) {
        Reference ref = refFactory.fromDWC(rec.get(ColDwcTerm.publishedInID), rec.get(ColDwcTerm.publishedIn), null, rec);
        rel.setRefId(ref.getId());
      }
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
    // require location
    if (rec.hasTerm(AcefTerm.DistributionElement)) {
      Distribution d = new Distribution();

      // which standard?
      d.setGazetteer(parse(GazetteerParser.PARSER, rec.get(AcefTerm.StandardInUse))
          .orElse(Gazetteer.TEXT, Issue.DISTRIBUTION_GAZETEER_INVALID, rec));

      // TODO: try to split location into several distributions...
      String loc = rec.get(AcefTerm.DistributionElement);
      if (d.getGazetteer() == Gazetteer.TEXT) {
        d.setArea(loc);
      } else {
        // only parse area if other than text
        AreaParser.Area textArea = new AreaParser.Area(loc, Gazetteer.TEXT);
        if (loc.indexOf(':') < 0) {
          loc = d.getGazetteer().locationID(loc);
        }
        AreaParser.Area area = SafeParser.parse(AreaParser.PARSER, loc).orElse(textArea,
            Issue.DISTRIBUTION_AREA_INVALID, rec);
        d.setArea(area.area);
        // check if we have contradicting extracted a gazetteer
        if (area.standard != Gazetteer.TEXT && area.standard != d.getGazetteer()) {
          LOG.info(
              "Area standard {} found in area {} different from explicitly given standard {} for {}",
              area.standard, area.area, d.getGazetteer(), rec);
        }
      }

      // status
      d.setStatus(parse(DistributionStatusParser.PARSER, rec.get(AcefTerm.DistributionStatus))
          .orElse(DistributionStatus.NATIVE, Issue.DISTRIBUTION_STATUS_INVALID, rec));
      setReference(d, rec);
      d.setVerbatimKey(rec.getKey());
      return Lists.newArrayList(d);
    }
    return Collections.emptyList();
  }
  
  List<Description> interpretDescription(VerbatimRecord rec) {
    return Collections.emptyList();
  }
  
  List<Media> interpretMedia(VerbatimRecord rec) {
    return Collections.emptyList();
  }

  Optional<NeoName> interpretName(VerbatimRecord v) {
    Optional<NameAccordingTo> opt = interpretName(v.get(ColTerm.ID),
        v.get(ColTerm.rank), v.get(ColTerm.scientificName), v.get(ColTerm.authorship),
        v.get(ColTerm.genus), v.get(ColTerm.subgenus), v.get(ColTerm.specificEpithet), v.get(ColTerm.infraspecificEpithet),
        v.get(ColTerm.code), v.get(ColTerm.status), v.get(ColTerm.link), v.get(ColTerm.remarks), v);
    return opt.map(NeoName::new);
  }
  
  Optional<NeoUsage> interpretTaxon(VerbatimRecord v) {
    NeoUsage u = NeoUsage.createTaxon(Origin.SOURCE, false);
    u.setId(v.get(ColTerm.ID));
    u.setVerbatimKey(v.getKey());

    // taxon
    Taxon t = u.getTaxon();
    t.setOrigin(Origin.SOURCE);
    t.setDoubtful(false); //TODO: v.get(ColTerm.provisional)
    t.setAccordingTo(v.get(AcefTerm.LTSSpecialist));
    t.setAccordingToDate(date(v, t, Issue.ACCORDING_TO_DATE_INVALID, AcefTerm.LTSDate));
    t.setDatasetUrl(uri(v, t, Issue.URL_INVALID, AcefTerm.InfraSpeciesURL, AcefTerm.SpeciesURL));
    t.setFossil(bool(v, t, Issue.IS_FOSSIL_INVALID, AcefTerm.IsFossil, AcefTerm.HasPreHolocene));
    t.setRecent(bool(v, t, Issue.IS_RECENT_INVALID, AcefTerm.IsRecent, AcefTerm.HasModern));
    t.setRemarks(v.get(AcefTerm.AdditionalData));
  
    // lifezones
    String raw = v.get(AcefTerm.LifeZone);
    if (raw != null) {
      for (String lzv : MULTIVAL.split(raw)) {
        Lifezone lz = parse(LifezoneParser.PARSER, lzv).orNull(Issue.LIFEZONE_INVALID, v);
        if (lz != null) {
          t.getLifezones().add(lz);
        }
      }
    }
  
    t.setSpeciesEstimate(null);
    t.setSpeciesEstimateReferenceId(null);
    
    // flat classification for any usage
    u.classification = interpretClassification(v, false);
    
    return Optional.of(u);
  }
  
  private Classification interpretClassification(VerbatimRecord v, boolean isSynonym) {
    Classification cl = new Classification();
    cl.setKingdom(v.get(AcefTerm.Kingdom));
    cl.setPhylum(v.get(AcefTerm.Phylum));
    cl.setClass_(v.get(AcefTerm.Class));
    cl.setOrder(v.get(AcefTerm.Order));
    cl.setSuperfamily(v.get(AcefTerm.Superfamily));
    cl.setFamily(v.get(AcefTerm.Family));
    if (!isSynonym) {
      cl.setGenus(v.get(AcefTerm.Genus));
      cl.setSubgenus(v.get(AcefTerm.SubGenusName));
    }
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
