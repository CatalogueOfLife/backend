package org.col.admin.importer.dwca;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.Lists;
import org.col.admin.importer.InsertMetadata;
import org.col.admin.importer.InterpreterBase;
import org.col.admin.importer.neo.NeoDb;
import org.col.admin.importer.neo.model.NeoNameRel;
import org.col.admin.importer.neo.model.NeoUsage;
import org.col.admin.importer.neo.model.RelType;
import org.col.admin.importer.reference.ReferenceFactory;
import org.col.api.model.*;
import org.col.api.vocab.*;
import org.col.common.util.ObjectUtils;
import org.col.parser.*;
import org.gbif.dwc.terms.DcTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.GbifTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interprets a verbatim record and transforms it into a name, taxon and unique references.
 */
public class DwcInterpreter extends InterpreterBase {
  private static final Logger LOG = LoggerFactory.getLogger(DwcInterpreter.class);
  private static final EnumNote<TaxonomicStatus> NO_STATUS = new EnumNote<>(TaxonomicStatus.PROVISIONALLY_ACCEPTED, null);

  private final InsertMetadata insertMetadata;

  public DwcInterpreter(Dataset dataset, InsertMetadata insertMetadata, ReferenceFactory refFactory, NeoDb store) {
    super(dataset, refFactory, store);
    this.insertMetadata = insertMetadata;
  }

  public Optional<NeoUsage> interpret(VerbatimRecord v) {
    // name
    Optional<NameAccordingTo> nat = interpretName(v);
    if (nat.isPresent()) {
      EnumNote<TaxonomicStatus> status = SafeParser.parse(TaxonomicStatusParser.PARSER, v.get(DwcTerm.taxonomicStatus)).orElse(NO_STATUS);
      // usage
      NeoUsage u;
      // a synonym by status?
      // we deal with relations via DwcTerm.acceptedNameUsageID and DwcTerm.acceptedNameUsage
      // during relation insertion
      if (status.val.isSynonym()) {
        u = NeoUsage.createSynonym(Origin.SOURCE, nat.get().getName(), status.val);
      } else {
        u = NeoUsage.createTaxon(Origin.SOURCE, nat.get().getName(), false);
        interpretTaxon(u, v, status);
      }
  
      // shared usage props
      u.setId(v.getFirst(DwcTerm.taxonID, DwcaReader.DWCA_ID));
      u.setVerbatimKey(v.getKey());
      u.usage.setAccordingTo(ObjectUtils.coalesce(v.get(DwcTerm.nameAccordingTo), nat.get().getAccordingTo()));
      u.homotypic = TaxonomicStatusParser.isHomotypic(status);

      // flat classification
      u.classification = new Classification();
      for (DwcTerm dwc : DwcTerm.HIGHER_RANKS) {
        u.classification.setByTerm(dwc, v.get(dwc));
      }
      return Optional.of(u);
    }
    return Optional.empty();
  }

  Optional<NeoNameRel> interpretNameRelations(VerbatimRecord rec) {
    NeoNameRel rel = new NeoNameRel();
    SafeParser<NomRelType> type = SafeParser.parse(NomRelTypeParser.PARSER, rec.get(ColDwcTerm.relationType));
    if (type.isPresent()) {
      rel.setType(RelType.from(type.get()));
      rel.setNote(rec.get(ColDwcTerm.relationRemarks));
      if (rec.hasTerm(ColDwcTerm.publishedIn)) {
        Reference ref = refFactory.fromDWC(rec.get(ColDwcTerm.publishedInID), rec.get(ColDwcTerm.publishedIn), null, rec);
        rel.setReferenceId(ref.getId());
      }
      return Optional.of(rel);
    }
    return Optional.empty();
  }

  List<Reference> interpretReference(VerbatimRecord rec) {
    return Lists.newArrayList(refFactory.fromDC(rec.get(DcTerm.identifier),
        rec.get(DcTerm.bibliographicCitation),
        rec.get(DcTerm.creator),
        rec.get(DcTerm.date),
        rec.get(DcTerm.title),
        rec.get(DcTerm.source),
        rec
    ));
  }

  List<Distribution> interpretDistribution(VerbatimRecord rec) {
    List<Distribution> distributions = new ArrayList<>();
    // try to figure out an area
    if (rec.hasTerm(DwcTerm.locationID)) {
      for (String loc : MULTIVAL.split(rec.get(DwcTerm.locationID))) {
        AreaParser.Area area = SafeParser.parse(AreaParser.PARSER, loc).orNull();
        if (area != null) {
          distributions.add(createDistribution(area.area, area.standard, rec));
        } else {
          rec.addIssue(Issue.DISTRIBUTION_AREA_INVALID);
        }
      }

    } else if (rec.hasTerm(DwcTerm.countryCode) || rec.hasTerm(DwcTerm.country)) {
      for (String craw : MULTIVAL.split(rec.getFirst(DwcTerm.countryCode, DwcTerm.country))) {
        Country country = SafeParser.parse(CountryParser.PARSER, craw).orNull();
        if (country != null) {
          distributions.add(createDistribution(country.getIso2LetterCode(), Gazetteer.ISO, rec));
        } else {
          rec.addIssue(Issue.DISTRIBUTION_COUNTRY_INVALID);
        }
      }

    } else if (rec.hasTerm(DwcTerm.locality)) {
      distributions.add(createDistribution(rec.get(DwcTerm.locality), Gazetteer.TEXT, rec));

    } else {
      rec.addIssue(Issue.DISTRIBUTION_INVALID);
    }
    return distributions;
  }

  List<VernacularName> interpretVernacularName(VerbatimRecord rec) {
    return super.interpretVernacular(rec,
        this::setReference,
        DwcTerm.vernacularName,
        null,
        DcTerm.language,
        DwcTerm.countryCode, DwcTerm.country
    );
  }
  
  List<Description> interpretDescription(VerbatimRecord rec) {
    return interpretDescription(rec, this::setReference,
        DcTerm.description,
        DcTerm.type,
        DcTerm.language);
  }
  
  List<Media> interpretMedia(VerbatimRecord rec) {
    return interpretMedia(rec, this::setReference,
        DcTerm.type,
        DcTerm.identifier,
        DcTerm.references,
        DcTerm.license,
        DcTerm.creator,
        DcTerm.created,
        DcTerm.title,
        DcTerm.format);
  }
  private Distribution createDistribution(String area, Gazetteer standard, VerbatimRecord rec) {
    Distribution d = new Distribution();
    d.setVerbatimKey(rec.getKey());
    d.setArea(area);
    d.setGazetteer(standard);
    setReference(d, rec);
    // TODO: parse status!!!
    d.setStatus(DistributionStatus.NATIVE);
    return d;
  }

  private void setReference(Referenced obj, VerbatimRecord v) {
    if (v.hasTerm(DcTerm.source)) {
      Reference ref = refFactory.fromCitation(null, v.get(DcTerm.source), v);
      setRefKey(obj, ref);
    }
  }

  private void interpretTaxon(NeoUsage u, VerbatimRecord v, EnumNote<TaxonomicStatus> status) {
    Taxon tax = u.getTaxon();
    // this can be a synonym at this stage which the class does not accept
    tax.setProvisional(TaxonomicStatus.PROVISIONALLY_ACCEPTED == status.val || status.val.isSynonym());
    tax.setDatasetUrl(uri(v, Issue.URL_INVALID, DcTerm.references));
    tax.setFossil(null);
    tax.setRecent(null);
    // t.setLifezones();
    if (v.hasTerm(ColDwcTerm.speciesEstimate)) {
      Integer est = v.getInt(ColDwcTerm.speciesEstimate, Issue.ESTIMATES_INVALID);
      if (est != null && est != 0) {
        if (est < 0) {
          v.addIssue(Issue.ESTIMATES_INVALID);
        } else {
          tax.setSpeciesEstimate(est);
          if (v.hasTerm(ColDwcTerm.speciesEstimateReference)) {
            Reference ref = refFactory.fromCitation(null, v.get(ColDwcTerm.speciesEstimateReference), v);
            if (ref != null) {
              tax.setSpeciesEstimateReferenceId(ref.getId());
            }
          }
        }
      }
    }
    tax.setRemarks(v.get(DwcTerm.taxonRemarks));
  }

  private Optional<NameAccordingTo> interpretName(VerbatimRecord v) {
    Optional<NameAccordingTo> opt = interpretName(v.getFirst(DwcTerm.taxonID, DwcaReader.DWCA_ID),
        v.getFirst(DwcTerm.taxonRank, DwcTerm.verbatimTaxonRank), v.get(DwcTerm.scientificName),
        v.get(DwcTerm.scientificNameAuthorship),
        v.getFirst(GbifTerm.genericName, DwcTerm.genus), v.get(DwcTerm.subgenus),
        v.get(DwcTerm.specificEpithet), v.get(DwcTerm.infraspecificEpithet),
        v.get(DwcTerm.nomenclaturalCode), v.get(DwcTerm.nomenclaturalStatus),
        v.getRaw(DcTerm.references), v.get(DwcTerm.nomenclaturalStatus), v);

    // publishedIn
    if (opt.isPresent()) {
      Name n = opt.get().getName();
      if (v.hasTerm(DwcTerm.namePublishedInID) || v.hasTerm(DwcTerm.namePublishedIn)) {
        Reference ref = refFactory.fromCitation(v.get(DwcTerm.namePublishedInID), v.get(DwcTerm.namePublishedIn), v);
        if (ref != null) {
          store.create(ref);
          n.setPublishedInId(ref.getId());
          n.setPublishedInPage(ref.getPage());
        }
      }
    }
    return opt;
  }

}
