package org.col.admin.importer.dwca;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import org.col.admin.importer.MappingFlags;
import org.col.admin.importer.NormalizationFailedException;
import org.col.admin.importer.neo.NeoDb;
import org.col.admin.importer.neo.NodeBatchProcessor;
import org.col.admin.importer.neo.model.*;
import org.col.api.model.ID;
import org.col.api.model.Name;
import org.col.api.model.VerbatimRecord;
import org.col.api.vocab.Issue;
import org.col.api.vocab.Origin;
import org.col.parser.NameParser;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.nameparser.api.Rank;
import org.neo4j.graphdb.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class DwcaRelationInserter implements NodeBatchProcessor {
  private static final Logger LOG = LoggerFactory.getLogger(DwcaRelationInserter.class);
  
  private static final List<Splitter> COMMON_SPLITTER = Lists.newArrayList();
  
  static {
    for (char del : "[|;, ]".toCharArray()) {
      COMMON_SPLITTER.add(Splitter.on(del).trimResults().omitEmptyStrings());
    }
  }
  
  private final NeoDb store;
  private final MappingFlags meta;
  
  public DwcaRelationInserter(NeoDb store, MappingFlags meta) {
    this.store = store;
    this.meta = meta;
  }
  
  @Override
  public void process(Node n) {
    try {
      if (n.hasLabel(Labels.USAGE)) {
        NeoUsage u = store.usageWithName(n);
        if (u.getVerbatimKey() != null) {
          VerbatimRecord v = store.getVerbatim(u.getVerbatimKey());
          insertAcceptedRel(u, v);
          insertParentRel(u, v);
          insertBasionymRel(u.getNeoName(), v);
          store.put(v);
        }
      }

    } catch (Exception e) {
      LOG.error("error processing explicit relations for {} {}", n, NeoProperties.getScientificNameWithAuthor(n), e);
    }
  }
  
  /**
   * Creates synonym_of relationship based on the verbatim dwc:acceptedNameUsageID and dwc:acceptedNameUsage term values.
   * Assumes pro parte synonyms are dealt with before and the remaining accepted identifier refers to a single taxon only.
   *
   * @param u the full neotaxon to process
   */
  private void insertAcceptedRel(NeoUsage u, VerbatimRecord v) {
    List<RankedUsage> accepted = Collections.emptyList();
    if (meta.isAcceptedNameMapped()) {
      accepted = usagesByIdOrName(v, u, true, DwcTerm.acceptedNameUsageID, Issue.ACCEPTED_ID_INVALID, DwcTerm.acceptedNameUsage, Origin.VERBATIM_ACCEPTED);
      for (RankedUsage acc : accepted) {
        store.createSynonymRel(u.node, acc.usageNode);
      }
    }
    
    // if status is synonym but we ain't got no idea of the accepted flag it
    if (accepted.isEmpty() && (u.isSynonym() || v.hasIssue(Issue.ACCEPTED_ID_INVALID))) {
      v.addIssue(Issue.ACCEPTED_NAME_MISSING);
      // now remove any denormed classification from this synonym to avoid parent relations
      //t.classification = null;
      u.node.addLabel(Labels.SYNONYM);
      u.node.removeLabel(Labels.TAXON);
    }
  }
  
  /**
   * Sets up the parent relations using the parentNameUsage(ID) term values.
   * The denormed, flat classification is used in a next step later.
   */
  private void insertParentRel(NeoUsage u, VerbatimRecord v) {
    if (v != null && meta.isParentNameMapped()) {
      List<RankedUsage> parents = usagesByIdOrName(v, u, false, DwcTerm.parentNameUsageID, Issue.PARENT_ID_INVALID, DwcTerm.parentNameUsage, Origin.VERBATIM_PARENT);
      if (!parents.isEmpty()) {
        store.assignParent(parents.get(0).usageNode, u.node);
      }
    }
  }

  private void insertBasionymRel(NeoName n, VerbatimRecord v) {
    if (v != null && meta.isOriginalNameMapped()) {
      RankedName bas = nameByIdOrName(v, n, DwcTerm.originalNameUsageID, Issue.BASIONYM_ID_INVALID, DwcTerm.originalNameUsage, Origin.VERBATIM_BASIONYM);
      if (bas != null) {
        NeoNameRel rel = new NeoNameRel();
        rel.setType(RelType.HAS_BASIONYM);
        rel.setVerbatimKey(v.getKey());
        store.createNameRel(n.node, bas.nameNode, rel);
      }
    }
  }
  
  /**
   * Reads a verbatim given term that should represent a foreign key to another record via the taxonID.
   * If the value is not the same as the original records taxonID it tries to split the ids into multiple keys and lookup the matching nodes.
   *
   * @return queue of potentially split ids with their matching neo node if found, otherwise null
   */
  private List<RankedUsage> usagesByIdOrName(VerbatimRecord v, NeoUsage t, boolean allowMultiple, DwcTerm idTerm, Issue invalidIdIssue, DwcTerm nameTerm, Origin createdNameOrigin) {
    List<RankedUsage> usages = Lists.newArrayList();
    final String unsplitIds = v.getRaw(idTerm);
    if (unsplitIds != null && !unsplitIds.equals(t.getId())) {
      if (allowMultiple && meta.getMultiValueDelimiters().containsKey(idTerm)) {
        usages.addAll(usagesByIds(meta.getMultiValueDelimiters().get(idTerm).splitToList(unsplitIds), t));
      } else {
        // match by taxonID to see if this is an existing identifier or if we should try to split it
        Node a = store.usages().nodeByID(unsplitIds);
        if (a != null) {
          usages.add(NeoProperties.getRankedUsage(a));
        
        } else if (allowMultiple){
          for (Splitter splitter : COMMON_SPLITTER) {
            List<String> vals = splitter.splitToList(unsplitIds);
            if (vals.size() > 1) {
              usages.addAll(usagesByIds(vals, t));
              break;
            }
          }
        }
      }
      // could not find anything?
      if (usages.isEmpty()) {
        v.addIssue(invalidIdIssue);
        LOG.info("{} {} not existing", idTerm.simpleName(), unsplitIds);
      }
    }

    if (usages.isEmpty()) {
      // try to setup rel via the name
      RankedUsage ru = usageByName(nameTerm, v, t, createdNameOrigin);
      if (ru != null) {
        usages.add(ru);
      }
    }
    return usages;
  }
  
  private List<RankedUsage> usagesByIds(Iterable<String> taxonIDs, ID usage) {
    List<RankedUsage> rankedNames = Lists.newArrayList();
    for (String id : taxonIDs) {
      if (!id.equals(usage.getId())) {
        Node n = store.usages().nodeByID(id);
        if (n != null) {
          rankedNames.add(NeoProperties.getRankedUsage(n));
        }
      }
    }
    return rankedNames;
  }
  
  
  private RankedName nameByIdOrName(VerbatimRecord v, NeoName nn, DwcTerm idTerm, Issue invalidIdIssue, DwcTerm nameTerm, Origin createdOrigin) {
    final String id = v.getRaw(idTerm);
    Node n = null;
    if (id != null && !id.equals(nn.getId())) {
      n = store.names().nodeByID(id);
      // could not find anything?
      if (n == null) {
        v.addIssue(invalidIdIssue);
        LOG.info("{} {} not existing", idTerm.simpleName(), id);
      }
    }
    
    // if nothing til here, try by name
    if (n == null) {
      return nameByName(nameTerm, v, nn, createdOrigin);
    
    } else {
      return NeoProperties.getRankedName(n);
    }
  }
  
  
  /**
   * Tries to find an existing name node by its scientific name.
   *
   * It first tries to lookup existing records by the canonical name with author, but falls back to authorless lookup if no matches.
   * If the name is the same as the original records scientificName it is ignored.
   *
   * If a name cannot be found a new name node is created as an explicit name, but no usage!
   *
   * @return the name node with its name. Null if no name was mapped or equals the record itself
   */
  private RankedName nameByName(DwcTerm term, VerbatimRecord v, NeoName n, Origin createdOrigin) {
    return byName(term, v, new RankedName(n), false,
        NeoProperties::getRankedName,
        name -> {
          name.setOrigin(createdOrigin);
          NeoName nn = new NeoName((name));
          Node n2 = store.names().create(nn);
          return new RankedName(n2, name.getScientificName(), name.authorshipComplete(), name.getRank());
        }
    );
  }
  
  /**
   * Reads a verbatim given term that should represent a scientific name pointing to another record via the scientificName.
   * It first tries to lookup existing records by the canonical name with author, but falls back to authorless lookup if no matches.
   * If the name is the same as the original records scientificName it is ignored.
   *
   * If a name cannot be found it is created as explicit names
   *
   * @return the accepted node with its name. Null if no accepted name was mapped or equals the record itself
   */
  private RankedUsage usageByName(DwcTerm term, VerbatimRecord v, NeoUsage u, final Origin createdOrigin) {
    return byName(term, v, NeoProperties.getRankedUsage(u), true,
        NeoProperties::getRankedUsage,
        name -> store.createProvisionalUsageFromSource(createdOrigin, name, u, u.usage.getName().getRank()));
  }
  
  private List<Node> usagesByNamesPreferAccepted(List<Node> nameNodes) {
    // convert to usages
    List<Node> usageMatches = nameNodes.stream()
        .map(store::usageNodesByName)
        .flatMap(List::stream)
        .collect(Collectors.toList());
  
    // if multiple matches remove non accepted names
    if (usageMatches.size() > 1) {
      List<Node> accepted = new ArrayList<>();
      for (Node u : usageMatches) {
        if (u.hasLabel(Labels.TAXON)) {
          accepted.add(u);
        }
      }
      if (!accepted.isEmpty()) {
        usageMatches = accepted;
      }
    }
    return usageMatches;
  }
  
  private <T extends RankedName> T byName(DwcTerm term, VerbatimRecord v, RankedName source,
                                          boolean transformToUsages,
                                          Function<Node, T> getByNode,
                                          Function<Name, T> createMissing) {
    if (v.hasTerm(term)) {
      final Name name = NameParser.PARSER.parse(v.get(term)).get().getName();
      // force unranked name for non binomials or unparsed names, avoiding wrong parser decisions
      if (!name.isParsed() || !name.isBinomial()) {
        name.setRank(Rank.UNRANKED);
      }
      if (!name.getScientificName().equalsIgnoreCase(source.name)) {
        List<Node> matches = store.names().nodesByName(name.getScientificName());
        // remove other authors, but allow names without authors
        if (name.hasAuthorship()) {
          matches.removeIf(n -> !Strings.isNullOrEmpty(NeoProperties.getAuthorship(n)) && !NeoProperties.getAuthorship(n).equalsIgnoreCase(name.authorshipComplete()));
        }
        
        // transform to usage nodes if requested, preferring taxon nodes over synonyms
        if (transformToUsages) {
          matches = usagesByNamesPreferAccepted(matches);
        }
        
        // if we got one match, use it!
        if (matches.isEmpty()) {
          // create name
          LOG.debug("{} {} not existing, materialize it", term.simpleName(), name);
          return createMissing.apply(name);
          
        } else{
          if (matches.size() > 1) {
            // still multiple matches, pick first and log critical issue!
            v.addIssue(Issue.NAME_NOT_UNIQUE);
          }
          return getByNode.apply(matches.get(0));
        }
      }
    }
    return null;
  }
  
  @Override
  public void commitBatch(int counter) {
    if (Thread.interrupted()) {
      LOG.warn("Normalizer interrupted, exit dataset {} early with incomplete parsing", store.getDataset().getKey());
      throw new NormalizationFailedException("Normalizer interrupted");
    }
    LOG.debug("Processed relations for {} nodes", counter);
  }
}
