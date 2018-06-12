package org.col.admin.importer.dwca;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.Lists;
import org.col.admin.importer.InsertMetadata;
import org.col.admin.importer.InterpreterBase;
import org.col.admin.importer.neo.ReferenceStore;
import org.col.admin.importer.neo.model.NeoNameRel;
import org.col.admin.importer.neo.model.NeoTaxon;
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
  private static final EnumNote<TaxonomicStatus> NO_STATUS =
      new EnumNote<>(TaxonomicStatus.DOUBTFUL, null);

  private final InsertMetadata insertMetadata;

  public DwcInterpreter(Dataset dataset, InsertMetadata insertMetadata, ReferenceStore refStore, ReferenceFactory refFactory) {
    super(dataset, refStore, refFactory);
    this.insertMetadata = insertMetadata;
  }

  public Optional<NeoTaxon> interpret(TermRecord v) {
    // name
    Optional<NameAccordingTo> nat = interpretName(v);
    if (nat.isPresent()) {
      // taxon
      NeoTaxon t = NeoTaxon.createTaxon(Origin.SOURCE, nat.get().getName(), false);
      // add taxon in any case - we can swap status of a synonym during normalization
      EnumNote<TaxonomicStatus> status = SafeParser
          .parse(TaxonomicStatusParser.PARSER, v.get(DwcTerm.taxonomicStatus)).orElse(NO_STATUS);
      interpretTaxon(t, v, status, nat.get().getAccordingTo());
      // a synonym by status?
      // we deal with relations via DwcTerm.acceptedNameUsageID and DwcTerm.acceptedNameUsage during
      // relation insertion
      if (status.val.isSynonym()) {
        t.synonym = new Synonym();
        t.synonym.setStatus(status.val);
        t.synonym.setAccordingTo(nat.get().getAccordingTo());
        t.homotypic = TaxonomicStatusParser.isHomotypic(status);
      }
      // flat classification
      t.classification = new Classification();
      for (DwcTerm dwc : DwcTerm.HIGHER_RANKS) {
        t.classification.setByTerm(dwc, v.get(dwc));
      }
      return Optional.of(t);
    }
    return Optional.empty();
  }

  Optional<NeoNameRel> interpretNameRelations(TermRecord rec) {
    NeoNameRel rel = new NeoNameRel();
    SafeParser<NomRelType> type = SafeParser.parse(NomRelTypeParser.PARSER, rec.get(ColTerm.relationType));
    if (type.isPresent()) {
      rel.setType(RelType.from(type.get()));
      rel.setNote(rec.get(ColTerm.relationRemarks));
      if (rec.hasTerm(ColTerm.publishedIn)) {
        Reference ref = refFactory.fromDWC(rec.get(ColTerm.publishedInID), rec.get(ColTerm.publishedIn), null);
        refStore.put(ref);
        rel.setRefKey(ref.getKey());
      }
      return Optional.of(rel);
    }
    return Optional.empty();
  }

  List<Reference> interpretReference(TermRecord rec) {
    return Lists.newArrayList(refFactory.fromDC(rec.get(DcTerm.identifier),
        rec.get(DcTerm.bibliographicCitation),
        rec.get(DcTerm.creator),
        rec.get(DcTerm.title),
        rec.get(DcTerm.date),
        rec.get(DcTerm.source)
    ));
  }

  List<Distribution> interpretDistribution(TermRecord rec) {
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

  List<VernacularName> interpretVernacularName(TermRecord rec) {
    return super.interpretVernacular(rec,
        this::addReferences,
        DwcTerm.vernacularName,
        null,
        DcTerm.language,
        DwcTerm.countryCode, DwcTerm.country
    );
  }

  private Distribution createDistribution(String area, Gazetteer standard, TermRecord rec) {
    Distribution d = new Distribution();
    d.setVerbatimKey(rec.getKey());
    d.setArea(area);
    d.setGazetteer(standard);
    addReferences(d, rec);
    // TODO: parse status!!!
    d.setStatus(DistributionStatus.NATIVE);
    return d;
  }

  private void addReferences(Referenced obj, TermRecord v) {
    if (v.hasTerm(DcTerm.source)) {
      lookupReference(null, v.get(DcTerm.source)).ifPresent(r -> {
        obj.addReferenceKey(r.getKey());
      });
    }
  }

  private void interpretTaxon(NeoTaxon t, TermRecord v, EnumNote<TaxonomicStatus> status, String accordingTo) {
    // and it keeps the taxonID for resolution of relations
    t.taxon.setVerbatimKey(v.getKey());
    t.taxon.setId(v.getFirst(DwcTerm.taxonID, DwcaReader.DWCA_ID));
    // this can be a synonym at this stage which the class does not accept
    t.taxon.setDoubtful(TaxonomicStatus.DOUBTFUL == status.val || status.val.isSynonym());
    t.taxon.setAccordingTo(ObjectUtils.coalesce(v.get(DwcTerm.nameAccordingTo), accordingTo));
    t.taxon.setAccordingToDate(null);
    t.taxon.setOrigin(Origin.SOURCE);
    t.taxon.setDatasetUrl(uri(v, t, Issue.URL_INVALID, DcTerm.references));
    t.taxon.setFossil(null);
    t.taxon.setRecent(null);
    // t.setLifezones();
    t.taxon.setSpeciesEstimate(null);
    t.taxon.setSpeciesEstimateReferenceKey(null);
    t.taxon.setRemarks(v.get(DwcTerm.taxonRemarks));
  }

  private Optional<NameAccordingTo> interpretName(TermRecord v) {
    Optional<NameAccordingTo> opt = interpretName(v.getFirst(DwcTerm.scientificNameID, DwcTerm.taxonID, DwcaReader.DWCA_ID),
        v.getFirst(DwcTerm.taxonRank, DwcTerm.verbatimTaxonRank), v.get(DwcTerm.scientificName),
        v.get(DwcTerm.scientificNameAuthorship),
        v.getFirst(GbifTerm.genericName, DwcTerm.genus), v.get(DwcTerm.subgenus),
        v.get(DwcTerm.specificEpithet), v.get(DwcTerm.infraspecificEpithet),
        v.get(DwcTerm.nomenclaturalCode), v.get(DwcTerm.nomenclaturalStatus),
        v.getRaw(DcTerm.references), v.get(DwcTerm.nomenclaturalStatus));

    // publishedIn
    if (opt.isPresent()) {
      Name n = opt.get().getName();
      n.setVerbatimKey(v.getKey());
      if (v.hasTerm(DwcTerm.namePublishedInID) || v.hasTerm(DwcTerm.namePublishedIn)) {
        lookupReference(v.get(DwcTerm.namePublishedInID), v.get(DwcTerm.namePublishedIn)).ifPresent(r -> {
          n.setPublishedInKey(r.getKey());
          n.setPublishedInPage(r.getPage());
        });
      }
    }
    return opt;
  }

}
