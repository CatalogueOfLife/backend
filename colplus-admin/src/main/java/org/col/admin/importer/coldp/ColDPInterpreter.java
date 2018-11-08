package org.col.admin.importer.coldp;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.Lists;
import org.col.admin.importer.InsertMetadata;
import org.col.admin.importer.InterpreterBase;
import org.col.admin.importer.NameValidator;
import org.col.admin.importer.neo.model.NeoTaxon;
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
 * Interprets a verbatim ColDP record and transforms it into a name, taxon and unique references.
 */
public class ColDPInterpreter extends InterpreterBase {
  private static final Logger LOG = LoggerFactory.getLogger(ColDPInterpreter.class);

  public ColDPInterpreter(Dataset dataset, InsertMetadata metadata, ReferenceFactory refFactory) {
    super(dataset, refFactory);
    // turn on normalization of flat classification
    metadata.setDenormedClassificationMapped(true);
  }

  public Optional<Reference> interpretReference(VerbatimRecord rec) {
    return Optional.of(refFactory.fromDC(
        rec.get(ColTerm.ID),
        rec.get(ColTerm.citation),
        rec.get(ColTerm.author),
        rec.get(ColTerm.year),
        rec.get(ColTerm.title),
        rec.get(ColTerm.source),
        rec
    ));
  }
  
  //TODO
  Optional<NeoTaxon> interpretSynonym(VerbatimRecord v) {
    return interpretTaxon(v);
  }
  
  //TODO
  public Optional<NeoTaxon> interpretTaxon(VerbatimRecord v) {
    boolean synonym = false;
    // name
    NeoTaxon t = NeoTaxon.createTaxon(Origin.SOURCE, null, false);

    // taxon
    t.taxon.setId(v.get(ColTerm.ID));
    t.taxon.setOrigin(Origin.SOURCE);
    t.taxon.setVerbatimKey(v.getKey());
    t.taxon.setAccordingTo(v.get(AcefTerm.LTSSpecialist));
    t.taxon.setAccordingToDate(date(v, t.taxon, Issue.ACCORDING_TO_DATE_INVALID, AcefTerm.LTSDate));
    t.taxon.setDatasetUrl(uri(v, t.taxon, Issue.URL_INVALID, AcefTerm.InfraSpeciesURL, AcefTerm.SpeciesURL));
    t.taxon.setFossil(bool(v, t.taxon, Issue.IS_FOSSIL_INVALID, AcefTerm.IsFossil, AcefTerm.HasPreHolocene));
    t.taxon.setRecent(bool(v, t.taxon, Issue.IS_RECENT_INVALID, AcefTerm.IsRecent, AcefTerm.HasModern));
    t.taxon.setRemarks(v.get(AcefTerm.AdditionalData));

    // lifezones
    String raw = v.get(AcefTerm.LifeZone);
    if (raw != null) {
      for (String lzv : MULTIVAL.split(raw)) {
        Lifezone lz = parse(LifezoneParser.PARSER, lzv).orNull(Issue.LIFEZONE_INVALID, v);
        if (lz != null) {
          t.taxon.getLifezones().add(lz);
        }
      }
    }

    t.taxon.setSpeciesEstimate(null);
    t.taxon.setSpeciesEstimateReferenceId(null);

    // status
    TaxonomicStatus status = parse(TaxonomicStatusParser.PARSER, v.get(AcefTerm.Sp2000NameStatus))
        .orElse(new EnumNote<>(synonym ? TaxonomicStatus.SYNONYM : TaxonomicStatus.ACCEPTED, null)).val;
    if (synonym != status.isSynonym()) {
      v.addIssue(Issue.TAXONOMIC_STATUS_INVALID);
      // override status as we require some accepted status on Taxon and some synonym status for
      // Synonym
      status = synonym ? TaxonomicStatus.SYNONYM : TaxonomicStatus.DOUBTFUL;
    }

    // synonym
    if (synonym) {
      t.synonym = new Synonym();
      t.synonym.setStatus(status);
      t.synonym.setAccordingTo(null);
      t.synonym.setVerbatimKey(v.getKey());

    } else {
      t.taxon.setDoubtful(TaxonomicStatus.DOUBTFUL == status);
    }

    // flat classification
    t.classification = interpretClassification(v, synonym);

    return Optional.of(t);
  }
  
  //TODO
  List<VernacularName> interpretVernacular(VerbatimRecord rec) {
    return super.interpretVernacular(rec,
        this::addReferences,
        AcefTerm.CommonName,
        AcefTerm.TransliteratedName,
        AcefTerm.Language,
        AcefTerm.Country
    );
  }
  
  //TODO
  private void addReferences(Referenced obj, VerbatimRecord v) {
    if (v.hasTerm(AcefTerm.ReferenceID)) {
      Reference r = refFactory.find(v.get(AcefTerm.ReferenceID), null);
      if (r != null) {
        obj.setReferenceId(r.getId());
      } else {
        LOG.info("ReferenceID {} not existing but referred from {} {}",
            v.get(AcefTerm.ReferenceID),
            obj.getClass().getSimpleName(),
            v.fileLine()
        );
        v.addIssue(Issue.REFERENCE_ID_INVALID);
      }
    }
  }
  
  //TODO
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
      addReferences(d, rec);
      d.setVerbatimKey(rec.getKey());
      return Lists.newArrayList(d);
    }
    return Collections.emptyList();
  }
  
  //TODO
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

  /**
   * @return a parsed name or in case of AcceptedInfraSpecificTaxa
   */
  public Optional<NeoTaxon> interpretName(VerbatimRecord v) {
    String authorship = v.get(ColTerm.authorship);
    
    // spot potential truncated authorstrings. CoL assembly db uses a max length of 100
    if (NameValidator.hasUnmatchedBrackets(authorship)) {
      v.addIssue(Issue.UNMATCHED_NAME_BRACKETS);
    }

    Optional<NameAccordingTo> opt = interpretName(v.get(ColTerm.ID), v.get(ColTerm.rank), v.get(ColTerm.scientificName),
        authorship, v.get(ColTerm.genus), v.get(ColTerm.subgenus),
        v.get(ColTerm.specificEpithet), v.get(ColTerm.infraspecificEpithet),
        v.get(ColTerm.code), v.get(ColTerm.status), v.get(ColTerm.link), v.get(ColTerm.remarks), v
    );
    
    
    return opt.map(nat -> {
      // taxon
      NeoTaxon nt = new NeoTaxon();
      nt.name = nat.getName();
      return  nt;
    });
  }

}
