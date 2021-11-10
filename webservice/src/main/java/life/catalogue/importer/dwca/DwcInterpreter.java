package life.catalogue.importer.dwca;

import com.google.common.collect.Lists;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.*;
import life.catalogue.coldp.ColDwcTerm;
import life.catalogue.importer.InterpreterBase;
import life.catalogue.importer.MappingFlags;
import life.catalogue.importer.neo.NeoDb;
import life.catalogue.importer.neo.model.NeoRel;
import life.catalogue.importer.neo.model.NeoUsage;
import life.catalogue.importer.neo.model.RelType;
import life.catalogue.importer.reference.ReferenceFactory;
import life.catalogue.parser.EnumNote;
import life.catalogue.parser.NomRelTypeParser;
import life.catalogue.parser.SafeParser;
import org.gbif.dwc.terms.DcTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.DwcaTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Interprets a verbatim record and transforms it into a name, taxon and unique references.
 */
public class DwcInterpreter extends InterpreterBase {
  private static final Logger LOG = LoggerFactory.getLogger(DwcInterpreter.class);
  private static final EnumNote<TaxonomicStatus> NO_STATUS = new EnumNote<>(TaxonomicStatus.ACCEPTED, null);

  private final MappingFlags mappingFlags;

  public DwcInterpreter(DatasetSettings settings, MappingFlags mappingFlags, ReferenceFactory refFactory, NeoDb store) {
    super(settings, refFactory, store);
    this.mappingFlags = mappingFlags;
  }

  public Optional<NeoUsage> interpretUsage(VerbatimRecord v) {
    // name
    return interpretName(v).map(pnu -> {
      NeoUsage u = interpretUsage(pnu, DwcTerm.taxonomicStatus, TaxonomicStatus.ACCEPTED, v, DwcTerm.taxonID, DwcaTerm.ID);
      if (u.isNameUsageBase()) {
        u.asNameUsageBase().setLink(uri(v, Issue.URL_INVALID, DcTerm.references));
      }
      u.usage.setRemarks(v.get(DwcTerm.taxonRemarks));
      // explicit accordingTo & namePhrase - the authorship could already have set these properties!
      if (v.hasTerm(DwcTerm.nameAccordingTo)) {
        setAccordingTo(u.usage, v.get(DwcTerm.nameAccordingTo), v);
      }
      interpretTaxon(u, v);
      return u;
    });
  }
  
  Optional<NeoRel> interpretNameRelations(VerbatimRecord rec) {
    NeoRel rel = new NeoRel();
    SafeParser<NomRelType> type = SafeParser.parse(NomRelTypeParser.PARSER, rec.get(ColDwcTerm.relationType));
    if (type.isPresent()) {
      rel.setType(RelType.from(type.get()));
      rel.setRemarks(rec.get(ColDwcTerm.relationRemarks));
      if (rec.hasTerm(ColDwcTerm.publishedIn)) {
        Reference ref = refFactory.fromDWC(rec.get(ColDwcTerm.publishedInID), rec.get(ColDwcTerm.publishedIn), null, rec);
        rel.setReferenceId(ref.getId());
      }
      return Optional.of(rel);
    }
    return Optional.empty();
  }
  
  List<Reference> interpretReference(VerbatimRecord rec) {
    return Lists.newArrayList(refFactory.fromDC(rec.getRaw(DcTerm.identifier),
        rec.get(DcTerm.bibliographicCitation),
        rec.get(DcTerm.creator),
        rec.get(DcTerm.date),
        rec.get(DcTerm.title),
        rec.get(DcTerm.source),
        rec
    ));
  }
  
  List<Distribution> interpretDistribution(VerbatimRecord rec) {
    // try to figure out an area
    if (rec.hasTerm(DwcTerm.locationID)) {
      return createDistributions(null,
          rec.getRaw(DwcTerm.locationID),
          rec.get(DwcTerm.occurrenceStatus),
          rec, this::setReference);
      
    } else if (rec.hasTerm(DwcTerm.countryCode) || rec.hasTerm(DwcTerm.country)) {
      return createDistributions(Gazetteer.ISO,
          rec.getFirst(DwcTerm.countryCode, DwcTerm.country),
          rec.get(DwcTerm.occurrenceStatus),
          rec, this::setReference);
      
    } else if (rec.hasTerm(DwcTerm.locality)) {
      return createDistributions(Gazetteer.TEXT,
          rec.get(DwcTerm.locality),
          rec.get(DwcTerm.occurrenceStatus),
          rec, this::setReference);
      
    } else {
      rec.addIssue(Issue.DISTRIBUTION_INVALID);
      return Collections.emptyList();
    }
  }
  
  List<VernacularName> interpretVernacularName(VerbatimRecord rec) {
    return super.interpretVernacular(rec,
        this::setReference,
        DwcTerm.vernacularName,
        null,
        DcTerm.language,
        DwcTerm.sex,
        DwcTerm.locality,
        DwcTerm.countryCode, DwcTerm.country
    );
  }

  List<Media> interpretMedia(VerbatimRecord rec) {
    return interpretMedia(rec, this::setReference,
        DcTerm.type,
        DcTerm.identifier,
        DcTerm.references,
        DcTerm.license,
        DcTerm.creator,
        DcTerm.created,
        DcTerm.title,
        DcTerm.format);
  }
  
  /**
   * Reads the dc:source citation string and looks up or creates a new reference.
   * Sets the reference id of the referenced object.
   * @param obj
   * @param v
   */
  private void setReference(Referenced obj, VerbatimRecord v) {
    if (v.hasTerm(DcTerm.source)) {
      Reference ref = refFactory.fromCitation(null, v.get(DcTerm.source), v);
      if (ref != null) {
        if (ref.getVerbatimKey() == null) {
          // create new reference with verbatim key, we've never seen this before!
          ref.setVerbatimKey(v.getId());
          store.references().create(ref);
        }
        obj.setReferenceId(ref.getId());
      }
    }
  }

  private void interpretTaxon(NeoUsage u, VerbatimRecord v) {
    if (!u.isSynonym()) {
      Taxon tax = u.asTaxon();
      tax.setExtinct(null);
      // TODO: lifezones come through the species profile extension.
    }
  }
  
  private Optional<ParsedNameUsage> interpretName(VerbatimRecord v) {
    Optional<ParsedNameUsage> opt = interpretName(false, v.getFirstRaw(DwcTerm.taxonID, DwcaTerm.ID),
        v.getFirst(DwcTerm.taxonRank, DwcTerm.verbatimTaxonRank), v.get(DwcTerm.scientificName),
        v.get(DwcTerm.scientificNameAuthorship),
        null, v.getFirst(DwcTerm.genericName, DwcTerm.genus), v.getFirst(DwcTerm.infragenericEpithet, DwcTerm.subgenus),
        v.get(DwcTerm.specificEpithet), v.get(DwcTerm.infraspecificEpithet), v.get(DwcTerm.cultivarEpithet),
        v.get(DwcTerm.nomenclaturalCode), v.get(DwcTerm.nomenclaturalStatus),
        v.getRaw(DcTerm.references), null, v);
    
    // publishedIn
    if (opt.isPresent()) {
      Name n = opt.get().getName();
      if (v.hasTerm(DwcTerm.namePublishedInID) || v.hasTerm(DwcTerm.namePublishedIn)) {
        Reference ref = refFactory.fromDWC(v.getRaw(DwcTerm.namePublishedInID), v.get(DwcTerm.namePublishedIn), v.get(DwcTerm.namePublishedInYear), v);
        if (ref != null) {
          if (ref.getVerbatimKey() == null) {
            // create new reference with verbatim key, we've never seen this before!
            ref.setVerbatimKey(v.getId());
            store.references().create(ref);
          }
          n.setPublishedInId(ref.getId());
          n.setPublishedInPage(ref.getPage());
        }
      }
    }
    return opt;
  }
  
}
