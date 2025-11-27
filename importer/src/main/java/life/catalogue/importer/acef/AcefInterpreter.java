package life.catalogue.importer.acef;

import life.catalogue.api.model.*;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.Setting;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.csv.MappingInfos;
import life.catalogue.dao.ReferenceFactory;
import life.catalogue.importer.InterpreterBase;
import life.catalogue.importer.store.ImportStore;
import life.catalogue.importer.store.model.NameUsageData;
import life.catalogue.matching.NameValidator;
import life.catalogue.parser.RankParser;
import life.catalogue.parser.SafeParser;

import org.gbif.dwc.terms.AcefTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.util.Collections;
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

  AcefInterpreter(DatasetSettings settings, MappingInfos metadata, ReferenceFactory refFactory, ImportStore store) {
    super(settings, refFactory, store, true);
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
  
  Optional<NameUsageData> interpretSpecies(VerbatimRecord v) {
    return interpretUsage(AcefTerm.AcceptedTaxonID, v, false);
  }

  Optional<NameUsageData> interpretInfraspecies(VerbatimRecord v) {
    requireTerm(v, AcefTerm.ParentSpeciesID, Issue.PARENT_ID_INVALID);
    return interpretUsage(AcefTerm.AcceptedTaxonID, v, false);
  }

  Optional<NameUsageData> interpretSynonym(VerbatimRecord v) {
    requireTerm(v, AcefTerm.AcceptedTaxonID, Issue.ACCEPTED_ID_INVALID);
    return interpretUsage(AcefTerm.ID, v, true);
  }
  
  List<VernacularName> interpretVernacular(VerbatimRecord rec) {
    return super.interpretVernacular(rec,
        this::setReference,
        AcefTerm.CommonName,
        AcefTerm.TransliteratedName,
        null,
        AcefTerm.Language,
        null,
        null,
        AcefTerm.Area,
        AcefTerm.Country
    );
  }
  
  List<Distribution> interpretDistribution(VerbatimRecord rec) {
    var dists = super.interpretDistributionByGazetteer(rec, this::setReference,
        AcefTerm.DistributionElement,
        AcefTerm.StandardInUse,
        AcefTerm.DistributionStatus,
        null,null,null,null,null, null, null, null);
    return dists;
  }
  
  private Optional<NameUsageData> interpretUsage(Term idTerm, VerbatimRecord v, boolean synonym) {
    // name
    return interpretName(idTerm, v).map(nat -> {
      var u = interpretUsage(idTerm, nat, AcefTerm.Sp2000NameStatus, synonym ? TaxonomicStatus.SYNONYM : TaxonomicStatus.ACCEPTED, v, null, Collections.emptyMap());
      // status matches up?
      if (synonym != u.ud.isSynonym()) {
        v.add(Issue.TAXONOMIC_STATUS_INVALID);
        // override status as we require some accepted status on Taxon and some synonym status for Synonym
        if (synonym) {
          u.ud.convertToSynonym(TaxonomicStatus.SYNONYM);
        } else {
          u.ud.convertToTaxon(TaxonomicStatus.PROVISIONALLY_ACCEPTED);
        }
      }

      AcefTerm parentTerm;
      if (synonym) {
        parentTerm = AcefTerm.AcceptedTaxonID;
      } else {
        parentTerm = AcefTerm.ParentSpeciesID;
        // taxon
        Taxon t = u.ud.asTaxon();
        t.setScrutinizer(v.get(AcefTerm.LTSSpecialist));
        t.setScrutinizerDate(fuzzydate(v, Issue.SCRUTINIZER_DATE_INVALID, AcefTerm.LTSDate));
        t.setExtinct(bool(v, Issue.IS_EXTINCT_INVALID, AcefTerm.IsExtinct));
        setEnvironment(t, v, AcefTerm.LifeZone);
      }
      // for both synonyms and taxa
      var nub = u.ud.asNameUsageBase();
      nub.setParentId(v.getRaw(parentTerm));
      nub.setLink(uri(v, Issue.URL_INVALID, AcefTerm.InfraSpeciesURL, AcefTerm.SpeciesURL));
      u.ud.usage.setRemarks(v.get(AcefTerm.AdditionalData));
      // flat classification for any usage
      u.ud.classification = interpretClassification(v, synonym);
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
        v.add(Issue.REFERENCE_ID_INVALID);
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
    final Optional<ParsedNameUsage> opt;
    if (v.getType() == AcefTerm.AcceptedInfraSpecificTaxa) {
      final var spID = v.getRaw(AcefTerm.ParentSpeciesID);
      final var sp = store.names().objByID(spID);
      if (sp != null && sp.getName().getRank() != Rank.GENUS) {
        opt = nameInterpreter.interpret(v.getRaw(idTerm), v.get(AcefTerm.InfraSpeciesMarker), Rank.SUBSPECIES,
          null, v.get(AcefTerm.InfraSpeciesAuthorString),null,
          null, sp.getName().getGenus(), sp.getName().getInfragenericEpithet(), sp.getName().getSpecificEpithet(), v.get(AcefTerm.InfraSpeciesEpithet), null,
          null,null,null,null,null,null,
          null, null,null, AcefTerm.GSDNameStatus,
          null, null, null, v
        );
      } else {
        if (sp == null) {
          v.add(Issue.PARENT_ID_INVALID);
        }
        opt = Optional.empty();
      }

    } else {
      Rank rank;
      Term authorTerm = AcefTerm.AuthorString;
      if (v.hasTerm(AcefTerm.InfraSpeciesEpithet)) {
        rank = Rank.SUBSPECIES;
        authorTerm = AcefTerm.InfraSpeciesAuthorString;
      } else if (v.hasTerm(AcefTerm.SpeciesEpithet)) {
        rank = Rank.SPECIES;
      } else if (v.hasTerm(AcefTerm.SubGenusName)) {
        rank = Rank.SUBGENUS;
      } else if (v.hasTerm(AcefTerm.Genus)) {
        rank = Rank.GENUS;
      } else {
        // missing name data!
        v.add(Issue.NOT_INTERPRETED);
        v.add(Issue.MISSING_GENUS);
        return Optional.empty();
      }

      if (settings.getEnum(Setting.NOMENCLATURAL_CODE) == NomCode.VIRUS) {
        // we shortcut building the ACEF virus name here as we don't want the genus classification to end up in the full name
        ParsedNameUsage nat = new ParsedNameUsage();
        nat.setName(new Name());
        nat.getName().setId(v.get(idTerm));
        nat.getName().setType(NameType.VIRUS);
        nat.getName().setCode(NomCode.VIRUS);
        String fullname = v.get(AcefTerm.SpeciesEpithet).trim() + " " + v.get(AcefTerm.AuthorString).trim();
        nat.getName().setScientificName(fullname.trim());
        nat.getName().setRank(
          SafeParser.parse(RankParser.PARSER, v.get(AcefTerm.InfraSpeciesMarker)).orElse(ObjectUtils.coalesce(rank, Rank.SPECIES))
        );
        opt = Optional.of(nat);

      } else {
        opt = nameInterpreter.interpret(v.getRaw(idTerm), v.get(AcefTerm.InfraSpeciesMarker), rank, null, v.get(authorTerm),null,
          null, v.get(AcefTerm.Genus), v.get(AcefTerm.SubGenusName), v.get(AcefTerm.SpeciesEpithet), v.get(AcefTerm.InfraSpeciesEpithet), null,
          null,null,null,null,null, null,
          null,null,null, AcefTerm.GSDNameStatus, null,null, null, v);
      }
    }

    if (opt.isPresent()) {
      // spot potential truncated authorstrings. The legacy CoL assembly db used a max length of 100
      var authorship = opt.get().getName().getAuthorship();
      if (NameValidator.hasUnmatchedBrackets(authorship)) {
        v.add(Issue.UNMATCHED_NAME_BRACKETS);
      }
      if (Strings.nullToEmpty(authorship).length() == ACEF_AUTHOR_MAX) {
        v.add(Issue.TRUNCATED_NAME);
      }
    }
    return opt;
  }
  
}
