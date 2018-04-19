package org.col.admin.task.importer.dwca;

import java.util.List;
import org.col.admin.task.importer.InsertMetadata;
import org.col.admin.task.importer.InterpreterBase;
import org.col.admin.task.importer.neo.ReferenceStore;
import org.col.admin.task.importer.neo.model.NeoTaxon;
import org.col.admin.task.importer.neo.model.UnescapedVerbatimRecord;
import org.col.api.model.Classification;
import org.col.api.model.Dataset;
import org.col.api.model.Distribution;
import org.col.api.model.NameAccordingTo;
import org.col.api.model.NameAct;
import org.col.api.model.Referenced;
import org.col.api.model.Synonym;
import org.col.api.model.Taxon;
import org.col.api.model.TermRecord;
import org.col.api.model.VerbatimRecord;
import org.col.api.model.VernacularName;
import org.col.api.vocab.Country;
import org.col.api.vocab.DistributionStatus;
import org.col.api.vocab.Gazetteer;
import org.col.api.vocab.Issue;
import org.col.api.vocab.NomActType;
import org.col.api.vocab.Origin;
import org.col.api.vocab.TaxonomicStatus;
import org.col.dw.reference.DwcReference;
import org.col.dw.reference.ReferenceFactory;
import org.col.parser.AreaParser;
import org.col.parser.CountryParser;
import org.col.parser.EnumNote;
import org.col.parser.LanguageParser;
import org.col.parser.SafeParser;
import org.col.parser.TaxonomicStatusParser;
import org.col.parser.UriParser;
import org.col.util.ObjectUtils;
import org.gbif.dwc.terms.DcTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.GbifTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.common.collect.Lists;

/**
 * Interprets a verbatim record and transforms it into a name, taxon and unique references.
 */
public class DwcInterpreter extends InterpreterBase {
  private static final Logger LOG = LoggerFactory.getLogger(DwcInterpreter.class);
  private static final EnumNote<TaxonomicStatus> NO_STATUS =
      new EnumNote<>(TaxonomicStatus.DOUBTFUL, null);

  private final InsertMetadata insertMetadata;

  public DwcInterpreter(Dataset dataset, InsertMetadata insertMetadata, ReferenceStore refStore) {
    super(dataset, refStore);
    this.insertMetadata = insertMetadata;
  }

  public NeoTaxon interpret(UnescapedVerbatimRecord v) {
    NeoTaxon t = new NeoTaxon();
    // verbatim
    t.verbatim = v;
    // name
    NameAccordingTo nat = interpretName(v);
    t.name = nat.getName();
    // acts
    t.acts = interpretActs(t, v);
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

  private List<NameAct> interpretActs(NeoTaxon t, VerbatimRecord v) {
    List<NameAct> acts = Lists.newArrayList();

    // publication of name
    if (v.hasTerm(DwcTerm.namePublishedInID) || v.hasTerm(DwcTerm.namePublishedIn)) {
      lookupReferenceTitleID(t, v.getTerm(DwcTerm.namePublishedInID),
          v.getTerm(DwcTerm.namePublishedIn)).ifPresent(r -> {
            NameAct act = new NameAct();
            act.setType(NomActType.DESCRIPTION);
            act.setReferenceKey(r.getKey());
            acts.add(act);
          });
    }
    return acts;
  }

  void interpretBibliography(NeoTaxon t) {
    ReferenceFactory refFactory = new ReferenceFactory(dataset);
    if (t.verbatim.hasExtension(GbifTerm.Reference)) {
      for (TermRecord rec : t.verbatim.getExtensionRecords(GbifTerm.Reference)) {
        DwcReference dwc = DwcReference.fromTermRecord(rec);
        t.bibliography.add(refFactory.fromDWC(dwc));
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
      // TODO: test for multiple
      lookupReferenceTitleID(t, null, v.get(DcTerm.source)).ifPresent(r -> {
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

  private Taxon interpretTaxon(VerbatimRecord v, EnumNote<TaxonomicStatus> status,
      String accordingTo) {
    // and it keeps the taxonID for resolution of relations
    Taxon t = new Taxon();
    t.setId(v.getFirst(DwcTerm.taxonID, DwcaReader.DWCA_ID));
    // this can be a synonym at this stage which the class does not accept
    t.setStatus(status.val.isSynonym() ? TaxonomicStatus.DOUBTFUL : status.val);
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
    return interpretName(v.getFirst(DwcTerm.scientificNameID, DwcTerm.taxonID, DwcaReader.DWCA_ID),
        v.getFirst(DwcTerm.taxonRank, DwcTerm.verbatimTaxonRank), v.getTerm(DwcTerm.scientificName),
        v.getTerm(DwcTerm.scientificNameAuthorship),
        v.getFirst(GbifTerm.genericName, DwcTerm.genus), v.getTerm(DwcTerm.subgenus),
        v.getTerm(DwcTerm.specificEpithet), v.getTerm(DwcTerm.infraspecificEpithet),
        v.getTerm(DwcTerm.nomenclaturalCode), v.getTerm(DwcTerm.nomenclaturalStatus),
        v.getTerm(DcTerm.references), v.getTerm(DwcTerm.nomenclaturalStatus));
  }

}
