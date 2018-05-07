package org.col.admin.task.importer.acef;

import com.google.common.collect.Lists;
import org.col.admin.task.importer.InsertMetadata;
import org.col.admin.task.importer.InterpreterBase;
import org.col.admin.task.importer.neo.ReferenceStore;
import org.col.admin.task.importer.neo.model.NeoTaxon;
import org.col.admin.task.importer.neo.model.UnescapedVerbatimRecord;
import org.col.admin.task.importer.reference.ReferenceFactory;
import org.col.api.model.*;
import org.col.api.vocab.*;
import org.col.parser.*;
import org.gbif.dwc.terms.AcefTerm;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

import static org.col.parser.SafeParser.parse;

/**
 * Interprets a verbatim ACEF record and transforms it into a name, taxon and unique references.
 */
public class AcefInterpreter extends InterpreterBase {
  private static final Logger LOG = LoggerFactory.getLogger(AcefInterpreter.class);

  public AcefInterpreter(Dataset dataset, InsertMetadata metadata, ReferenceStore refStore, ReferenceFactory refFactory) {
    super(dataset, refStore, refFactory);
    // turn on normalization of flat classification
    metadata.setDenormedClassificationMapped(true);
  }

  public NeoTaxon interpretTaxon(UnescapedVerbatimRecord v, boolean synonym) {
    NeoTaxon t = new NeoTaxon();
    // verbatim
    t.verbatim = v;
    // name
    NameAccordingTo nat = interpretName(v);
    t.name = nat.getName();

    // status
    TaxonomicStatus status = parse(TaxonomicStatusParser.PARSER, v.getTerm(AcefTerm.Sp2000NameStatus))
        .orElse(new EnumNote<>(synonym ? TaxonomicStatus.SYNONYM : TaxonomicStatus.ACCEPTED, null))
        .val;
    if (synonym != status.isSynonym()) {
      t.taxon.addIssue(Issue.TAXONOMIC_STATUS_INVALID);
      // override status as we require some accepted status on Taxon and some synonym status for
      // Synonym
      status = synonym ? TaxonomicStatus.SYNONYM : TaxonomicStatus.DOUBTFUL;
    }

    // taxon
    t.taxon = new Taxon();
    t.taxon.setId(v.getId());
    t.taxon.setOrigin(Origin.SOURCE);
    t.taxon.setAccordingTo(v.getTerm(AcefTerm.LTSSpecialist));
    t.taxon.setAccordingToDate(date(t, Issue.ACCORDING_TO_DATE_INVALID, AcefTerm.LTSDate));
    t.taxon.setOrigin(Origin.SOURCE);
    t.taxon.setDatasetUrl(uri(t, Issue.URL_INVALID, AcefTerm.InfraSpeciesURL, AcefTerm.SpeciesURL));
    t.taxon.setFossil(bool(t, Issue.IS_FOSSIL_INVALID, AcefTerm.IsFossil, AcefTerm.HasPreHolocene));
    t.taxon.setRecent(bool(t, Issue.IS_RECENT_INVALID, AcefTerm.IsRecent, AcefTerm.HasModern));
    t.taxon.setRemarks(v.getTerm(AcefTerm.AdditionalData));

    // lifezones
    String raw = t.verbatim.getTerm(AcefTerm.LifeZone);
    if (raw != null) {
      for (String lzv : MULTIVAL.split(raw)) {
        Lifezone lz = parse(LifezoneParser.PARSER, lzv).orNull(Issue.LIFEZONE_INVALID, t.issues);
        if (lz != null) {
          t.taxon.getLifezones().add(lz);
        }
      }
    }

    t.taxon.setSpeciesEstimate(null);
    t.taxon.setSpeciesEstimateReferenceKey(null);

    // synonym
    if (synonym) {
      t.synonym = new Synonym();
      t.synonym.setStatus(status);
      t.synonym.setAccordingTo(nat.getAccordingTo());

    } else {
      t.taxon.setDoubtful(TaxonomicStatus.DOUBTFUL == status);
    }

    // flat classification
    t.classification = interpretClassification(v, synonym);

    return t;
  }

  void interpretVernaculars(NeoTaxon t) {
    for (TermRecord rec : t.verbatim.getExtensionRecords(AcefTerm.CommonNames)) {
      VernacularName vn = new VernacularName();
      vn.setName(rec.get(AcefTerm.CommonName));
      vn.setLanguage(SafeParser.parse(LanguageParser.PARSER, rec.get(AcefTerm.Language)).orNull());
      vn.setCountry(SafeParser.parse(CountryParser.PARSER, rec.get(AcefTerm.Country)).orNull());
      vn.setLatin(rec.get(AcefTerm.TransliteratedName));
      addReferences(vn, rec, t.issues);
      addAndTransliterate(t, vn);
    }
  }

  void interpretDistributions(NeoTaxon t) {
    for (TermRecord rec : t.verbatim.getExtensionRecords(AcefTerm.Distribution)) {
      // require location
      if (rec.hasTerm(AcefTerm.DistributionElement)) {
        Distribution d = new Distribution();

        // which standard?
        d.setGazetteer(parse(GazetteerParser.PARSER, rec.get(AcefTerm.StandardInUse))
            .orElse(Gazetteer.TEXT, Issue.DISTRIBUTION_GAZETEER_INVALID, t.issues));

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
              Issue.DISTRIBUTION_AREA_INVALID, t.issues);
          d.setArea(area.area);
          // check if we have contradicting extracted a gazetteer
          if (area.standard != Gazetteer.TEXT && area.standard != d.getGazetteer()) {
            LOG.info(
                "Area standard {} found in area {} different from explicitly given standard {} for taxon {}",
                area.standard, area.area, d.getGazetteer(), t.getID());
          }
        }

        // status
        d.setStatus(parse(DistributionStatusParser.PARSER, rec.get(AcefTerm.DistributionStatus))
            .orElse(DistributionStatus.NATIVE, Issue.DISTRIBUTION_STATUS_INVALID, t.issues));
        addReferences(d, rec, t.issues);
        t.distributions.add(d);

      } else {
        t.addIssue(Issue.DISTRIBUTION_INVALID);
      }
    }
  }

  private void addReferences(Referenced obj, TermRecord v, Set<Issue> issueCollector) {
    if (v.hasTerm(AcefTerm.ReferenceID)) {
      Reference r = refStore.refById(v.get(AcefTerm.ReferenceID));
      if (r != null) {
        obj.addReferenceKey(r.getKey());
      } else {
        LOG.info("ReferenceID {} not existing but referred from {} for taxon {}",
            v.get(AcefTerm.ReferenceID), obj.getClass().getSimpleName(),
            v.get(AcefTerm.AcceptedTaxonID));
        issueCollector.add(Issue.REFERENCE_ID_INVALID);
      }
    }
  }

  private Classification interpretClassification(VerbatimRecord v, boolean isSynonym) {
    Classification cl = new Classification();
    cl.setKingdom(v.getTerm(AcefTerm.Kingdom));
    cl.setPhylum(v.getTerm(AcefTerm.Phylum));
    cl.setClass_(v.getTerm(AcefTerm.Class));
    cl.setOrder(v.getTerm(AcefTerm.Order));
    cl.setSuperfamily(v.getTerm(AcefTerm.Superfamily));
    cl.setFamily(v.getTerm(AcefTerm.Family));
    if (!isSynonym) {
      cl.setGenus(v.getTerm(AcefTerm.Genus));
      cl.setSubgenus(v.getTerm(AcefTerm.SubGenusName));
    }
    return cl;
  }

  /**
   * @return a parsed name or in case of AcceptedInfraSpecificTaxa
   */
  private NameAccordingTo interpretName(VerbatimRecord v) {
    String authorship;
    String rank;
    if (v.hasTerm(AcefTerm.InfraSpeciesEpithet)) {
      rank = v.getTerm(AcefTerm.InfraSpeciesMarker);
      authorship = v.getTerm(AcefTerm.InfraSpeciesAuthorString);
    } else {
      rank = "species";
      authorship = v.getTerm(AcefTerm.AuthorString);
    }

    if (v.getTerms().getType() == AcefTerm.AcceptedInfraSpecificTaxa) {
      // preliminary name with just id and rank
      NameAccordingTo nat = new NameAccordingTo();
      nat.setName(new Name());
      nat.getName().setId(v.getId());
      nat.getName()
          .setRank(SafeParser.parse(RankParser.PARSER, rank).orElse(Rank.INFRASPECIFIC_NAME));
      return nat;
    }
    return interpretName(v.getId(), rank, null, authorship, v.getTerm(AcefTerm.Genus),
        v.getTerm(AcefTerm.SubGenusName), v.getTerm(AcefTerm.SpeciesEpithet),
        v.getTerm(AcefTerm.InfraSpeciesEpithet), null, v.getTerm(AcefTerm.GSDNameStatus), null,
        null);
  }

}
