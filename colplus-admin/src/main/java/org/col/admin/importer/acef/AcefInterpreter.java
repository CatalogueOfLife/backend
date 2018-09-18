package org.col.admin.importer.acef;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import org.col.admin.importer.InsertMetadata;
import org.col.admin.importer.InterpreterBase;
import org.col.admin.importer.NameValidator;
import org.col.admin.importer.neo.model.NeoTaxon;
import org.col.admin.importer.reference.ReferenceFactory;
import org.col.api.model.*;
import org.col.api.vocab.*;
import org.col.parser.*;
import org.gbif.dwc.terms.AcefTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.col.parser.SafeParser.parse;

/**
 * Interprets a verbatim ACEF record and transforms it into a name, taxon and unique references.
 */
public class AcefInterpreter extends InterpreterBase {
  private static final Logger LOG = LoggerFactory.getLogger(AcefInterpreter.class);
  private static final int ACEF_AUTHOR_MAX = 100;

  public AcefInterpreter(Dataset dataset, InsertMetadata metadata, ReferenceFactory refFactory) {
    super(dataset, refFactory);
    // turn on normalization of flat classification
    metadata.setDenormedClassificationMapped(true);
  }

  public Optional<Reference> interpretReference(VerbatimRecord rec) {
    return Optional.of(refFactory.fromACEF(
        rec.get(AcefTerm.ReferenceID),
        rec.get(AcefTerm.Author),
        rec.get(AcefTerm.Year),
        rec.get(AcefTerm.Title),
        rec.get(AcefTerm.Details),
        rec
    ));
  }

  Optional<NeoTaxon> interpretAccepted(VerbatimRecord v) {
    return interpretTaxon(AcefTerm.AcceptedTaxonID, v, false);
  }

  Optional<NeoTaxon> interpretSynonym(VerbatimRecord v) {
    return interpretTaxon(AcefTerm.ID, v, true);
  }

  private Optional<NeoTaxon> interpretTaxon(Term idTerm, VerbatimRecord v, boolean synonym) {
    // name
    Optional<NameAccordingTo> nat = interpretName(idTerm, v);
    if (!nat.isPresent()) {
      return Optional.empty();
    }

    NeoTaxon t = NeoTaxon.createTaxon(Origin.SOURCE, nat.get().getName(), false);

    // taxon
    t.taxon.setId(v.get(idTerm));
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
      t.synonym.setAccordingTo(nat.get().getAccordingTo());
      t.synonym.setVerbatimKey(v.getKey());

    } else {
      t.taxon.setDoubtful(TaxonomicStatus.DOUBTFUL == status);
    }

    // flat classification
    t.classification = interpretClassification(v, synonym);

    return Optional.of(t);
  }

  List<VernacularName> interpretVernacular(VerbatimRecord rec) {
    return super.interpretVernacular(rec,
        this::addReferences,
        AcefTerm.CommonName,
        AcefTerm.TransliteratedName,
        AcefTerm.Language,
        AcefTerm.Country
    );
  }
  private void addReferences(Referenced obj, VerbatimRecord v) {
    if (v.hasTerm(AcefTerm.ReferenceID)) {
      Reference r = refFactory.find(v.get(AcefTerm.ReferenceID), null);
      if (r != null) {
        obj.addReferenceId(r.getId());
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
  private Optional<NameAccordingTo> interpretName(Term idTerm, VerbatimRecord v) {
    String authorship;
    String rank;
    if (v.hasTerm(AcefTerm.InfraSpeciesEpithet)) {
      rank = v.get(AcefTerm.InfraSpeciesMarker);
      authorship = v.get(AcefTerm.InfraSpeciesAuthorString);
    } else {
      rank = "species";
      authorship = v.get(AcefTerm.AuthorString);
    }

    // spot potential truncated authorstrings. CoL assembly db uses a max length of 100
    if (NameValidator.hasUnmatchedBrackets(authorship)) {
      v.addIssue(Issue.UNMATCHED_NAME_BRACKETS);
    }
    if (Strings.nullToEmpty(authorship).length() == ACEF_AUTHOR_MAX) {
      v.addIssue(Issue.TRUNCATED_NAME);
    }

    Optional<NameAccordingTo> opt;
    if (v.getType() == AcefTerm.AcceptedInfraSpecificTaxa) {
      // preliminary name with just id and rank
      NameAccordingTo nat = new NameAccordingTo();
      nat.setName(new Name());
      nat.getName().setId(v.get(idTerm));
      nat.getName().setRank(
          SafeParser.parse(RankParser.PARSER, rank).orElse(Rank.INFRASPECIFIC_NAME)
      );
      opt = Optional.of(nat);
    } else {
      opt = interpretName(v.get(idTerm), rank, null, authorship, v.get(AcefTerm.Genus),
          v.get(AcefTerm.SubGenusName), v.get(AcefTerm.SpeciesEpithet),
          v.get(AcefTerm.InfraSpeciesEpithet), null, v.get(AcefTerm.GSDNameStatus), null,
          null, v);
    }
    return opt;
  }

}
