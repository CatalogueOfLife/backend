package life.catalogue.importer.acef;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.Setting;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.importer.InterpreterBase;
import life.catalogue.importer.MappingFlags;
import life.catalogue.importer.NameValidator;
import life.catalogue.importer.neo.NeoDb;
import life.catalogue.importer.neo.model.NeoUsage;
import life.catalogue.importer.reference.ReferenceFactory;
import life.catalogue.parser.RankParser;
import life.catalogue.parser.SafeParser;

import org.gbif.dwc.terms.AcefTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;

/**
 * Interprets a verbatim ACEF record and transforms it into a name, taxon and unique references.
 */
public class AcefInterpreter extends InterpreterBase {
  private static final Logger LOG = LoggerFactory.getLogger(AcefInterpreter.class);
  private static final int ACEF_AUTHOR_MAX = 100;

  AcefInterpreter(DatasetSettings settings, MappingFlags metadata, ReferenceFactory refFactory, NeoDb store) {
    super(settings, refFactory, store);
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
    return super.interpretDistributionByGazetteer(rec, this::setReference,
        AcefTerm.DistributionElement,
        AcefTerm.StandardInUse,
        AcefTerm.DistributionStatus);
  }
  
  private Optional<NeoUsage> interpretUsage(Term idTerm, VerbatimRecord v, boolean synonym) {
    // name
    return interpretName(idTerm, v).map(nat -> {
      NeoUsage u = interpretUsage(nat, AcefTerm.Sp2000NameStatus, synonym ? TaxonomicStatus.SYNONYM : TaxonomicStatus.ACCEPTED, v, idTerm);
      // status matches up?
      if (synonym != u.isSynonym()) {
        v.addIssue(Issue.TAXONOMIC_STATUS_INVALID);
        // override status as we require some accepted status on Taxon and some synonym status for Synonym
        if (synonym) {
          u.convertToSynonym(TaxonomicStatus.SYNONYM);
        } else {
          u.convertToTaxon(TaxonomicStatus.PROVISIONALLY_ACCEPTED);
        }
      }

      if (!synonym) {
        // taxon
        Taxon t = u.asTaxon();
        t.setScrutinizer(v.get(AcefTerm.LTSSpecialist));
        t.setScrutinizerDate(fuzzydate(v, Issue.SCRUTINIZER_DATE_INVALID, AcefTerm.LTSDate));
        t.setExtinct(bool(v, Issue.IS_EXTINCT_INVALID, AcefTerm.IsExtinct));
        setEnvironment(t, v, AcefTerm.LifeZone);
      }
      // for both synonyms and taxa
      u.asNameUsageBase().setLink(uri(v, Issue.URL_INVALID, AcefTerm.InfraSpeciesURL, AcefTerm.SpeciesURL));
      u.usage.setRemarks(v.get(AcefTerm.AdditionalData));
      // flat classification for any usage
      u.classification = interpretClassification(v, synonym);
      return u;
    });
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
  private Optional<ParsedNameUsage> interpretName(Term idTerm, VerbatimRecord v) {
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
    
    Optional<ParsedNameUsage> opt;
    if (v.getType() == AcefTerm.AcceptedInfraSpecificTaxa) {
      // preliminary name with just id and rank
      ParsedNameUsage nat = new ParsedNameUsage();
      nat.setName(new Name());
      nat.getName().setId(v.get(idTerm));
      nat.getName().setRank(
          SafeParser.parse(RankParser.PARSER, rank).orElse(Rank.INFRASPECIFIC_NAME)
      );
      opt = Optional.of(nat);

    } else if (settings.getEnum(Setting.NOMENCLATURAL_CODE) == NomCode.VIRUS) {
      // we shortcut building the ACEF virus name here as we don't want the genus classification to end up in the full name
      ParsedNameUsage nat = new ParsedNameUsage();
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
      opt = interpretName(true, v.get(idTerm), rank, null, authorship,
          null, v.get(AcefTerm.Genus), v.get(AcefTerm.SubGenusName), v.get(AcefTerm.SpeciesEpithet), v.get(AcefTerm.InfraSpeciesEpithet),
          null, null, v.get(AcefTerm.GSDNameStatus), null,null, v);
    }
    return opt;
  }
  
}
