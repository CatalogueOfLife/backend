package org.col.admin.task.importer.dwca;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.Lists;
import org.col.admin.task.importer.InsertMetadata;
import org.col.admin.task.importer.InterpreterBase;
import org.col.admin.task.importer.neo.ReferenceStore;
import org.col.admin.task.importer.neo.model.NeoTaxon;
import org.col.admin.task.importer.reference.ReferenceFactory;
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
    NeoTaxon t = new NeoTaxon();
    // name
    NameAccordingTo nat = interpretName(v);
    t.name = nat.getName();
    t.name.setVerbatimKey(v.getKey());
    // flat classification
    t.classification = new Classification();
    for (DwcTerm dwc : DwcTerm.HIGHER_RANKS) {
      t.classification.setByTerm(dwc, v.get(dwc));
    }
    // add taxon in any case - we can swap status of a synonym during normalization
    EnumNote<TaxonomicStatus> status = SafeParser
        .parse(TaxonomicStatusParser.PARSER, v.get(DwcTerm.taxonomicStatus)).orElse(NO_STATUS);
    t.taxon = interpretTaxon(v, status, nat.getAccordingTo());
    // a synonym by status?
    // we deal with relations via DwcTerm.acceptedNameUsageID and DwcTerm.acceptedNameUsage during
    // relation insertion
    if (status.val.isSynonym()) {
      t.synonym = new Synonym();
      t.synonym.setStatus(status.val);
      t.synonym.setAccordingTo(nat.getAccordingTo());
      t.homotypic = TaxonomicStatusParser.isHomotypic(status);
    }

    return Optional.of(t);
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
    VernacularName vn = new VernacularName();
    vn.setVerbatimKey(rec.getKey());
    vn.setName(rec.get(DwcTerm.vernacularName));
    vn.setLanguage(SafeParser.parse(LanguageParser.PARSER, rec.get(DcTerm.language))
        .orNull());
    vn.setCountry(SafeParser.parse(CountryParser.PARSER, rec.getFirst(DwcTerm.countryCode, DwcTerm.country))
        .orNull());
    addReferences(vn, rec);
    transliterate(vn);
    return Lists.newArrayList(vn);
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

  private Taxon interpretTaxon(TermRecord v, EnumNote<TaxonomicStatus> status, String accordingTo) {
    // and it keeps the taxonID for resolution of relations
    Taxon t = new Taxon();
    t.setVerbatimKey(v.getKey());
    t.setId(v.getFirst(DwcTerm.taxonID, DwcaReader.DWCA_ID));
    // this can be a synonym at this stage which the class does not accept
    t.setDoubtful(TaxonomicStatus.DOUBTFUL == status.val || status.val.isSynonym());
    t.setAccordingTo(ObjectUtils.coalesce(v.get(DwcTerm.nameAccordingTo), accordingTo));
    t.setAccordingToDate(null);
    t.setOrigin(Origin.SOURCE);
    t.setDatasetUrl(SafeParser.parse(UriParser.PARSER, v.get(DcTerm.references))
        .orNull(Issue.URL_INVALID, t.getIssues()));
    t.setFossil(null);
    t.setRecent(null);
    // t.setLifezones();
    t.setSpeciesEstimate(null);
    t.setSpeciesEstimateReferenceKey(null);
    t.setRemarks(v.get(DwcTerm.taxonRemarks));

    return t;
  }

  private NameAccordingTo interpretName(TermRecord v) {
    // TODO: or use v.getID() ???
    // TODO: should we also get remarks through an extension, e.g. species profile or a nomenclature
    // extension?
    NameAccordingTo nat = interpretName(v.getFirst(DwcTerm.scientificNameID, DwcTerm.taxonID, DwcaReader.DWCA_ID),
        v.getFirst(DwcTerm.taxonRank, DwcTerm.verbatimTaxonRank), v.get(DwcTerm.scientificName),
        v.get(DwcTerm.scientificNameAuthorship),
        v.getFirst(GbifTerm.genericName, DwcTerm.genus), v.get(DwcTerm.subgenus),
        v.get(DwcTerm.specificEpithet), v.get(DwcTerm.infraspecificEpithet),
        v.get(DwcTerm.nomenclaturalCode), v.get(DwcTerm.nomenclaturalStatus),
        v.get(DcTerm.references), v.get(DwcTerm.nomenclaturalStatus));

    // publishedIn
    if (v.hasTerm(DwcTerm.namePublishedInID) || v.hasTerm(DwcTerm.namePublishedIn)) {
      lookupReference(v.get(DwcTerm.namePublishedInID), v.get(DwcTerm.namePublishedIn)).ifPresent(r -> {
        nat.getName().setPublishedInKey(r.getKey());
        nat.getName().setPublishedInPage(r.getPage());
      });
    }
    return nat;
  }

}
