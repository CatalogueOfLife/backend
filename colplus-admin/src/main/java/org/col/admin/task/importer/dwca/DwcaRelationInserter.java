package org.col.admin.task.importer.dwca;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import org.col.admin.task.importer.InsertMetadata;
import org.col.admin.task.importer.NormalizationFailedException;
import org.col.admin.task.importer.neo.NeoDb;
import org.col.admin.task.importer.neo.model.*;
import org.col.api.model.Name;
import org.col.api.vocab.Issue;
import org.col.api.vocab.Origin;
import org.col.parser.NameParser;
import org.gbif.dwc.terms.DwcTerm;
import org.neo4j.graphdb.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 *
 */
public class DwcaRelationInserter implements NeoDb.NodeBatchProcessor {
  private static final Logger LOG = LoggerFactory.getLogger(DwcaRelationInserter.class);

  private static final List<Splitter> COMMON_SPLITTER = Lists.newArrayList();
  static {
    for (char del : "[|;, ]".toCharArray()) {
      COMMON_SPLITTER.add(Splitter.on(del).trimResults().omitEmptyStrings());
    }
  }

  private final NeoDb store;
  private final InsertMetadata meta;
  
  public DwcaRelationInserter(NeoDb store, InsertMetadata meta) {
    this.store = store;
    this.meta = meta;
  }

  @Override
  public void process(Node n) {
    try {
      NeoTaxon t = store.get(n);
      insertAcceptedRel(t);
      insertParentRel(t);
      insertBasionymRel(t);
      store.put(t);

    } catch (Exception e) {
      LOG.error("error processing explicit relations for {} {}", n, NeoProperties.getScientificNameWithAuthor(n), e);
    }
  }

  /**
   * Creates synonym_of relationship based on the verbatim dwc:acceptedNameUsageID and dwc:acceptedNameUsage term values.
   * Assumes pro parte basionymGroup are dealt with before and the remaining accepted identifier refers to a single taxon only.
   *
   * @param t the full neotaxon to process
   */
  private void insertAcceptedRel(NeoTaxon t) {
    List<RankedName> accepted = null;
    if (t.verbatim != null && meta.isAcceptedNameMapped()) {
      accepted = lookupByIdOrName(t, DwcTerm.acceptedNameUsageID, Issue.ACCEPTED_ID_INVALID, DwcTerm.acceptedNameUsage, Origin.VERBATIM_ACCEPTED);
      for (RankedName acc : accepted) {
        store.createSynonymRel(t.node, acc.node);
      }
    }

    // if status is synonym but we aint got no idea of the accepted insert an incertae sedis record of same rank
    if ((accepted == null || accepted.isEmpty())
        && (t.isSynonym() || t.issues.containsKey(Issue.ACCEPTED_ID_INVALID))
        ) {
      t.addIssue(Issue.ACCEPTED_NAME_MISSING);
      NeoDb.PLACEHOLDER.setRank(t.name.getRank());
      RankedName acc = store.createDoubtfulFromSource(Origin.MISSING_ACCEPTED, NeoDb.PLACEHOLDER, t, t.name.getRank(), null, Issue.ACCEPTED_NAME_MISSING, t.name.getScientificName());
      // now remove any denormed classification from this synonym as we have copied it already to the accepted placeholder
      t.classification = null;
      store.createSynonymRel(t.node, acc.node);
    }
  }

  /**
   * Sets up the parent relations using the parentNameUsage(ID) term values.
   * The denormed, flat classification is used in a next step later.
   */
  private void insertParentRel(NeoTaxon t) {
    if (t.verbatim != null && meta.isParentNameMapped()) {
      RankedName parent = lookupSingleByIdOrName(t, DwcTerm.parentNameUsageID, Issue.PARENT_ID_INVALID, DwcTerm.parentNameUsage, Origin.VERBATIM_PARENT);
      if (parent != null) {
        store.assignParent(parent.node, t.node);
      }
    }
  }

  private void insertBasionymRel(NeoTaxon t) {
    if (t.verbatim != null && meta.isOriginalNameMapped()) {
      RankedName bas = lookupSingleByIdOrName(t, DwcTerm.originalNameUsageID, Issue.BASIONYM_ID_INVALID, DwcTerm.originalNameUsage, Origin.VERBATIM_BASIONYM);
      if (bas != null) {
        bas.node.createRelationshipTo(t.node, RelType.BASIONYM_OF);
      }
    }
  }

  private RankedName lookupSingleByIdOrName(NeoTaxon t, DwcTerm idTerm, Issue invlidIdIssue, DwcTerm nameTerm, Origin createdNameOrigin) {
    List<RankedName> names = lookupByIdOrName(t, false, idTerm, invlidIdIssue, nameTerm, createdNameOrigin);
    return names.isEmpty() ? null : names.get(0);
  }

  private List<RankedName> lookupByIdOrName(NeoTaxon t, DwcTerm idTerm, Issue invlidIdIssue, DwcTerm nameTerm, Origin createdNameOrigin) {
    return lookupByIdOrName(t, true, idTerm, invlidIdIssue, nameTerm, createdNameOrigin);
  }

  private List<RankedName> lookupByIdOrName(NeoTaxon t, boolean allowMultiple, DwcTerm idTerm, Issue invlidIdIssue, DwcTerm nameTerm, Origin createdNameOrigin) {
    List<RankedName> names = lookupByTaxonID(idTerm, t, invlidIdIssue, allowMultiple);
    if (names.isEmpty()) {
      // try to setup rel via the name
      RankedName n = lookupByName(nameTerm, t, createdNameOrigin);
      if (n != null) {
        names.add(n);
      }
    }
    return names;
  }

  /**
   * Reads a verbatim given term that should represent a foreign key to another record via the taxonID.
   * If the value is not the same as the original records taxonID it tries to split the ids into multiple keys and lookup the matching nodes.
   *
   * @return list of potentially split ids with their matching neo node if found, otherwise null
   */
  private List<RankedName> lookupByTaxonID(DwcTerm term, NeoTaxon t, Issue invalidIdIssue, boolean allowMultiple) {
    List<RankedName> ids = Lists.newArrayList();
    final String unsplitIds = t.verbatim.getCoreTerm(term);
    if (unsplitIds != null && !unsplitIds.equals(t.getTaxonID())) {
      if (allowMultiple && meta.getMultiValueDelimiters().containsKey(term)) {
        ids.addAll(lookupRankedNames(
            meta.getMultiValueDelimiters().get(term).splitToList(unsplitIds), t)
        );
      } else {
        // match by taxonID to see if this is an existing identifier or if we should try to split it
        Node a = store.byTaxonID(unsplitIds);
        if (a != null) {
          ids.add(NeoProperties.getRankedName(a));

        } else if (allowMultiple){
          for (Splitter splitter : COMMON_SPLITTER) {
            List<String> vals = splitter.splitToList(unsplitIds);
            if (vals.size() > 1) {
              ids.addAll(lookupRankedNames(vals, t));
              break;
            }
          }
        }
      }
      // could not find anything?
      if (ids.isEmpty()) {
        t.addIssue(invalidIdIssue, unsplitIds);
        LOG.warn("{} {} not existing", term.simpleName(), unsplitIds);
      }
    }
    return ids;
  }


  private List<RankedName> lookupRankedNames(Iterable<String> taxonIDs, NeoTaxon t) {
    List<RankedName> rankedNames = Lists.newArrayList();
    for (String id : taxonIDs) {
      if (!id.equals(t.getTaxonID())) {
        Node n = store.byTaxonID(id);
        if (n != null) {
          rankedNames.add(NeoProperties.getRankedName(n));
        }
      }
    }
    return rankedNames;
  }

  /**
   * Reads a verbatim given term that should represent a scientific name pointing to another record via the scientificName.
   * It first tries to lookup existing records by the canonical name with author, but falls back to authorless lookup if no matches.
   * If the name is the same as the original records scientificName it is ignored.
   *
   * If true names that cannot be found are created as explicit names
   *
   * @return the accepted node with its name. Null if no accepted name was mapped or equals the record itself
   */
  private RankedName lookupByName(DwcTerm term, NeoTaxon t, Origin createdOrigin) {
    if (t.verbatim.hasCoreTerm(term)) {
      Name nameTmp = NameParser.PARSER.parse(t.verbatim.getCoreTerm(term)).get();
      final Name name = nameTmp;
      if (!name.getScientificName().equalsIgnoreCase(t.name.getScientificName())) {
        List<Node> matches = store.byScientificName(name.getScientificName());
        // remove other authors, but allow names without authors
        if (name.hasAuthorship()) {
          matches.removeIf(n -> !Strings.isNullOrEmpty(NeoProperties.getAuthorship(n)) && !NeoProperties.getAuthorship(n).equalsIgnoreCase(name.authorshipComplete()));
        }

        // if multiple matches remove basionymGroup
        if (matches.size() > 1) {
          matches.removeIf(n -> n.hasLabel(Labels.SYNONYM));
        }

        // if we got one match, use it!
        if (matches.isEmpty()) {
          // create
          LOG.debug("{} {} not existing, materialize it", term.simpleName(), name);
          return store.createDoubtfulFromSource(createdOrigin, name, t, t.name.getRank(), null, null, null);

        } else {
          if (matches.size() > 1) {
            // still multiple matches, pick first and log critical issue!
            t.addIssue(Issue.NAME_NOT_UNIQUE, name);
          }
          return NeoProperties.getRankedName(matches.get(0));
        }
      }
    }
    return null;
  }

  @Override
  public void commitBatch(int counter) {
    if (Thread.interrupted()) {
      LOG.warn("Normalizer interrupted, exit {} early with incomplete parsing", store.getDataset());
      throw new NormalizationFailedException("Normalizer interrupted");
    }
    LOG.debug("Processed relations for {} nodes", counter);
  }
}
