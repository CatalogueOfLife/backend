package life.catalogue.importer.acef;

import com.google.common.base.Strings;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.Origin;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.importer.InterpreterBase;
import life.catalogue.importer.MappingFlags;
import life.catalogue.importer.NameValidator;
import life.catalogue.importer.neo.NeoDb;
import life.catalogue.importer.neo.model.NeoUsage;
import life.catalogue.importer.reference.ReferenceFactory;
import life.catalogue.parser.EnumNote;
import life.catalogue.parser.RankParser;
import life.catalogue.parser.SafeParser;
import life.catalogue.parser.TaxonomicStatusParser;
import org.gbif.dwc.terms.AcefTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

import static life.catalogue.parser.SafeParser.parse;

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
        null,
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
      t.setAccordingToDate(fuzzydate(v, Issue.ACCORDING_TO_DATE_INVALID, AcefTerm.LTSDate));
      t.setExtinct(bool(v, Issue.IS_EXTINCT_INVALID, AcefTerm.IsExtinct));
      t.setRemarks(v.get(AcefTerm.AdditionalData));
  
      // lifezones
      setLifezones(t, v, AcefTerm.LifeZone);
    }
    // for both synonyms and taxa
    u.usage.setWebpage(uri(v, Issue.URL_INVALID, AcefTerm.InfraSpeciesURL, AcefTerm.SpeciesURL));
    u.usage.addAccordingTo(nat.get().getAccordingTo());
  
    u.setId(v.get(idTerm));
    u.setVerbatimKey(v.getId());
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
      } else if (v.hasTerm(AcefTerm.SubGenusName)) {
        rank = Rank.SUBGENUS.name();
      } else if (v.hasTerm(AcefTerm.Genus)) {
        rank = Rank.GENUS.name();
      } else {
        // missing name data!
        v.addIssue(Issue.NOT_INTERPRETED);
        v.addIssue(Issue.MISSING_GENUS);
        return Optional.empty();
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

    } else if (dataset.getCode() == NomCode.VIRUS) {
      // we shortcut building the ACEF virus name here as we don't want the genus classification to end up in the full name
      NameAccordingTo nat = new NameAccordingTo();
      nat.setName(new Name());
      nat.getName().setId(v.get(idTerm));
      nat.getName().setType(NameType.VIRUS);
      nat.getName().setCode(NomCode.VIRUS);
      String fullname = v.get(AcefTerm.SpeciesEpithet).trim() + " " + authorship.trim();
      nat.getName().setScientificName(fullname.trim());
      nat.getName().setRank(
          SafeParser.parse(RankParser.PARSER, rank).orElse(Rank.SPECIES)
      );
      opt = Optional.of(nat);
      
    } else {
      opt = interpretName(v.get(idTerm), rank, null, authorship,
          v.get(AcefTerm.Genus), v.get(AcefTerm.SubGenusName), v.get(AcefTerm.SpeciesEpithet), v.get(AcefTerm.InfraSpeciesEpithet),
          null, null,
          null, v.get(AcefTerm.GSDNameStatus),
          null, null, null,null, v);
    }
    return opt;
  }
  
}
