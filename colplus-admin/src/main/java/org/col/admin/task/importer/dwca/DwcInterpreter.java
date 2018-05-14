package org.col.admin.task.importer.dwca;

import org.col.admin.task.importer.InsertMetadata;
import org.col.admin.task.importer.InterpreterBase;
import org.col.admin.task.importer.neo.ReferenceStore;
import org.col.admin.task.importer.neo.model.NeoTaxon;
import org.col.admin.task.importer.neo.model.UnescapedVerbatimRecord;
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

import static com.google.common.base.Strings.emptyToNull;

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

  public NeoTaxon interpret(UnescapedVerbatimRecord v) {
    NeoTaxon t = new NeoTaxon();
    // verbatim
    t.verbatim = v;
    // name
    NameAccordingTo nat = interpretName(v);
    t.name = nat.getName();
    // flat classification
    t.classification = new Classification();
    for (DwcTerm dwc : DwcTerm.HIGHER_RANKS) {
      t.classification.setByTerm(dwc, v.getTerm(dwc));
    }
    // add taxon in any case - we can swap status of a synonym during normalization
    EnumNote<TaxonomicStatus> status = SafeParser
        .parse(TaxonomicStatusParser.PARSER, v.getTerm(DwcTerm.taxonomicStatus)).orElse(NO_STATUS);
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

    return t;
  }

  void interpretBibliography(NeoTaxon t) {
    if (t.verbatim.hasExtension(GbifTerm.Reference)) {
      for (TermRecord rec : t.verbatim.getExtensionRecords(GbifTerm.Reference)) {
        String id = emptyToNull(rec.get(DcTerm.identifier));
        Reference ref = refStore.refById(id);
        if (ref == null) {
          ref = refFactory.fromDC(id,
              emptyToNull(rec.get(DcTerm.bibliographicCitation)),
              emptyToNull(rec.get(DcTerm.creator)),
              emptyToNull(rec.get(DcTerm.title)),
              emptyToNull(rec.get(DcTerm.date)),
              emptyToNull(rec.get(DcTerm.source))
          );
          refStore.put(ref);
        }
        t.bibliography.add(ref.getKey());
      }
    }
  }

  void interpretDistributions(NeoTaxon t) {
    if (t.verbatim.hasExtension(GbifTerm.Distribution)) {
      for (TermRecord rec : t.verbatim.getExtensionRecords(GbifTerm.Distribution)) {
        // try to figure out an area
        if (rec.hasTerm(DwcTerm.locationID)) {
          for (String loc : MULTIVAL.split(rec.get(DwcTerm.locationID))) {
            AreaParser.Area area = SafeParser.parse(AreaParser.PARSER, loc).orNull();
            if (area != null) {
              addDistribution(t, area.area, area.standard, rec);
            } else {
              t.addIssue(Issue.DISTRIBUTION_AREA_INVALID);
            }
          }

        } else if (rec.hasTerm(DwcTerm.countryCode) || rec.hasTerm(DwcTerm.country)) {
          for (String craw : MULTIVAL.split(rec.getFirst(DwcTerm.countryCode, DwcTerm.country))) {
            Country country = SafeParser.parse(CountryParser.PARSER, craw).orNull();
            if (country != null) {
              addDistribution(t, country.getIso2LetterCode(), Gazetteer.ISO, rec);
            } else {
              t.addIssue(Issue.DISTRIBUTION_COUNTRY_INVALID);
            }
          }

        } else if (rec.hasTerm(DwcTerm.locality)) {
          addDistribution(t, rec.get(DwcTerm.locality), Gazetteer.TEXT, rec);

        } else {
          t.addIssue(Issue.DISTRIBUTION_INVALID);
        }
      }
    }
  }

  void addDistribution(NeoTaxon t, String area, Gazetteer standard, TermRecord rec) {
    Distribution d = new Distribution();
    d.setArea(area);
    d.setGazetteer(standard);
    addReferences(t, d, rec);
    // TODO: parse status!!!
    d.setStatus(DistributionStatus.NATIVE);
    t.distributions.add(d);
  }

  private void addReferences(NeoTaxon t, Referenced obj, TermRecord v) {
    if (v.hasTerm(DcTerm.source)) {
      lookupReference(null, v.get(DcTerm.source)).ifPresent(r -> {
        obj.addReferenceKey(r.getKey());
      });
    }
  }

  void interpretVernacularNames(NeoTaxon t) {
    if (t.verbatim.hasExtension(GbifTerm.VernacularName)) {
      for (TermRecord rec : t.verbatim.getExtensionRecords(GbifTerm.VernacularName)) {
        VernacularName vn = new VernacularName();
        vn.setName(rec.get(DwcTerm.vernacularName));
        vn.setLanguage(SafeParser.parse(LanguageParser.PARSER, rec.get(DcTerm.language)).orNull());
        vn.setCountry(SafeParser
            .parse(CountryParser.PARSER, rec.getFirst(DwcTerm.countryCode, DwcTerm.country))
            .orNull());
        addReferences(t, vn, rec);
        addAndTransliterate(t, vn);
      }
    }
  }

  private Taxon interpretTaxon(VerbatimRecord v, EnumNote<TaxonomicStatus> status, String accordingTo) {
    // and it keeps the taxonID for resolution of relations
    Taxon t = new Taxon();
    t.setId(v.getFirst(DwcTerm.taxonID, DwcaReader.DWCA_ID));
    // this can be a synonym at this stage which the class does not accept
    t.setDoubtful(TaxonomicStatus.DOUBTFUL == status.val || status.val.isSynonym());
    // TODO: interpret all of Taxon via new dwca extension
    t.setAccordingTo(ObjectUtils.coalesce(v.getTerm(DwcTerm.nameAccordingTo), accordingTo));
    t.setAccordingToDate(null);
    t.setOrigin(Origin.SOURCE);
    t.setDatasetUrl(SafeParser.parse(UriParser.PARSER, v.getTerm(DcTerm.references))
        .orNull(Issue.URL_INVALID, t.getIssues()));
    t.setFossil(null);
    t.setRecent(null);
    // t.setLifezones();
    t.setSpeciesEstimate(null);
    t.setSpeciesEstimateReferenceKey(null);
    t.setRemarks(v.getTerm(DwcTerm.taxonRemarks));

    return t;
  }

  private NameAccordingTo interpretName(VerbatimRecord v) {
    // TODO: or use v.getID() ???
    // TODO: should we also get remarks through an extension, e.g. species profile or a nomenclature
    // extension?
    NameAccordingTo nat = interpretName(v.getFirst(DwcTerm.scientificNameID, DwcTerm.taxonID, DwcaReader.DWCA_ID),
        v.getFirst(DwcTerm.taxonRank, DwcTerm.verbatimTaxonRank), v.getTerm(DwcTerm.scientificName),
        v.getTerm(DwcTerm.scientificNameAuthorship),
        v.getFirst(GbifTerm.genericName, DwcTerm.genus), v.getTerm(DwcTerm.subgenus),
        v.getTerm(DwcTerm.specificEpithet), v.getTerm(DwcTerm.infraspecificEpithet),
        v.getTerm(DwcTerm.nomenclaturalCode), v.getTerm(DwcTerm.nomenclaturalStatus),
        v.getTerm(DcTerm.references), v.getTerm(DwcTerm.nomenclaturalStatus));

    // publishedIn
    if (v.hasTerm(DwcTerm.namePublishedInID) || v.hasTerm(DwcTerm.namePublishedIn)) {
      lookupReference(v.getTerm(DwcTerm.namePublishedInID), v.getTerm(DwcTerm.namePublishedIn)).ifPresent(r -> {
        nat.getName().setPublishedInKey(r.getKey());
        nat.getName().setPublishedInPage(r.getPage());
      });
    }
    return nat;
  }

}
