package org.col.importer.acef;

import java.util.List;
import java.util.Optional;

import com.google.common.base.Strings;
import org.col.importer.MappingFlags;
import org.col.importer.InterpreterBase;
import org.col.importer.NameValidator;
import org.col.importer.neo.NeoDb;
import org.col.importer.neo.model.NeoUsage;
import org.col.importer.reference.ReferenceFactory;
import org.col.api.model.*;
import org.col.api.vocab.Issue;
import org.col.api.vocab.Origin;
import org.col.api.vocab.TaxonomicStatus;
import org.col.parser.EnumNote;
import org.col.parser.RankParser;
import org.col.parser.SafeParser;
import org.col.parser.TaxonomicStatusParser;
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

  AcefInterpreter(Dataset dataset, MappingFlags metadata, ReferenceFactory refFactory, NeoDb store) {
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
  
  Optional<NeoUsage> interpretSpecies(VerbatimRecord v) {
    return interpretUsage(AcefTerm.AcceptedTaxonID, v, false);
  }

  Optional<NeoUsage> interpretInfraspecies(VerbatimRecord v) {
    requireTerm(v, AcefTerm.ParentSpeciesID, Issue.PARENT_ID_INVALID);
    return interpretUsage(AcefTerm.AcceptedTaxonID, v, false);
  }

  Optional<NeoUsage> interpretSynonym(VerbatimRecord v) {
    requireTerm(v, AcefTerm.AcceptedTaxonID, Issue.ACCEPTED_ID_INVALID);
    return interpretUsage(AcefTerm.ID, v, true);
  }
  
  List<VernacularName> interpretVernacular(VerbatimRecord rec) {
    return super.interpretVernacular(rec,
        this::setReference,
        AcefTerm.CommonName,
        AcefTerm.TransliteratedName,
        AcefTerm.Language,
        AcefTerm.Area,
        AcefTerm.Country
    );
  }
  
  List<Distribution> interpretDistribution(VerbatimRecord rec) {
    return super.interpretDistribution(rec, this::setReference,
        AcefTerm.DistributionElement,
        AcefTerm.StandardInUse,
        AcefTerm.DistributionStatus);
  }
  
  private Optional<NeoUsage> interpretUsage(Term idTerm, VerbatimRecord v, boolean synonym) {
    // name
    Optional<NameAccordingTo> nat = interpretName(idTerm, v);
    if (!nat.isPresent()) {
      return Optional.empty();
    }
    
    // status
    TaxonomicStatus status = parse(TaxonomicStatusParser.PARSER, v.get(AcefTerm.Sp2000NameStatus))
        .orElse(new EnumNote<>(synonym ? TaxonomicStatus.SYNONYM : TaxonomicStatus.ACCEPTED, null), Issue.TAXONOMIC_STATUS_INVALID, v).val;
    if (synonym != status.isSynonym()) {
      v.addIssue(Issue.TAXONOMIC_STATUS_INVALID);
      // override status as we require some accepted status on Taxon and some synonym status for
      // Synonym
      status = synonym ? TaxonomicStatus.SYNONYM : TaxonomicStatus.PROVISIONALLY_ACCEPTED;
    }
  
    NeoUsage u;
    // synonym
    if (synonym) {
      u = NeoUsage.createSynonym(Origin.SOURCE, nat.get().getName(), status);

    } else {
      // taxon
      u = NeoUsage.createTaxon(Origin.SOURCE, nat.get().getName(), status);
      Taxon t = u.getTaxon();
      t.setOrigin(Origin.SOURCE);
      t.setAccordingTo(v.get(AcefTerm.LTSSpecialist));
      t.setAccordingToDate(date(v, Issue.ACCORDING_TO_DATE_INVALID, AcefTerm.LTSDate));
      t.setWebpage(uri(v, Issue.URL_INVALID, AcefTerm.InfraSpeciesURL, AcefTerm.SpeciesURL));
      t.setFossil(bool(v, Issue.IS_FOSSIL_INVALID, AcefTerm.HasPreHolocene, AcefTerm.IsFossil));
      t.setRecent(bool(v, Issue.IS_RECENT_INVALID, AcefTerm.HasModern, AcefTerm.IsRecent));
      Boolean extinct = bool(v, AcefTerm.IsExtinct);
      if (Boolean.FALSE.equals(extinct)) {
        t.setRecent(false);
      }
      t.setRemarks(v.get(AcefTerm.AdditionalData));
  
      // lifezones
      setLifezones(t, v, AcefTerm.LifeZone);
    }
    // for both synonyms and taxa
    u.usage.addAccordingTo(nat.get().getAccordingTo());
  
    u.setId(v.get(idTerm));
    u.setVerbatimKey(v.getKey());
    // flat classification for any usage
    u.classification = interpretClassification(v, synonym);

    return Optional.of(u);
  }

  private void setReference(Referenced obj, VerbatimRecord v) {
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
      rank = v.getOrDefault(AcefTerm.InfraSpeciesMarker, Rank.SUBSPECIES.name());
      authorship = v.get(AcefTerm.InfraSpeciesAuthorString);
    } else {
      if (v.hasTerm(AcefTerm.SpeciesEpithet)) {
        rank = Rank.SPECIES.name();
      } else {
        rank = Rank.GENUS.name();
      }
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
      opt = interpretName(v.get(idTerm), rank, null, authorship,
          v.get(AcefTerm.Genus), v.get(AcefTerm.SubGenusName), v.get(AcefTerm.SpeciesEpithet), v.get(AcefTerm.InfraSpeciesEpithet),
          null, null,
          null, v.get(AcefTerm.GSDNameStatus), null,null, v);
    }
    return opt;
  }
  
}
