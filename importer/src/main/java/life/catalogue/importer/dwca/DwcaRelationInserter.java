package life.catalogue.importer.dwca;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.Origin;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.common.text.StringUtils;
import life.catalogue.csv.MappingInfos;
import life.catalogue.importer.neo.NeoDb;
import life.catalogue.importer.neo.model.*;
import life.catalogue.parser.NameParser;

import org.gbif.dwc.terms.DwcTerm;
import org.gbif.nameparser.api.Rank;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;

/**
 *
 */
public class DwcaRelationInserter implements BiConsumer<Node, Transaction> {
  private static final Logger LOG = LoggerFactory.getLogger(DwcaRelationInserter.class);
  
  private static final List<Splitter> COMMON_SPLITTER = Lists.newArrayList();
  
  static {
    for (char del : "[|;, ]".toCharArray()) {
      COMMON_SPLITTER.add(Splitter.on(del).trimResults().omitEmptyStrings());
    }
  }
  
  private final NeoDb store;
  private final MappingInfos meta;
  
  public DwcaRelationInserter(NeoDb store, MappingInfos meta) {
    this.store = store;
    this.meta = meta;
  }

  @Override
  public void accept(Node n, Transaction tx) {
    try {
      if (n.hasLabel(Labels.USAGE)) {
        NeoUsage u = store.usageWithName(n);
        if (u.getVerbatimKey() != null) {
          VerbatimRecord v = store.getVerbatim(u.getVerbatimKey());
          insertAcceptedRel(u, v, tx);
          insertParentRel(u, v, tx);
          insertBasionymRel(u.getNeoName(), v, tx);
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
  private void insertAcceptedRel(NeoUsage u, VerbatimRecord v, Transaction tx) {
    List<RankedUsage> accepted = Collections.emptyList();
    if (meta.isAcceptedNameMapped()) {
      accepted = usagesByIdOrName(v, u, true, DwcTerm.acceptedNameUsageID, Issue.ACCEPTED_ID_INVALID, DwcTerm.acceptedNameUsage, Origin.VERBATIM_ACCEPTED, tx);
      for (RankedUsage acc : accepted) {
        if (store.createSynonymRel(u.node, acc.usageNode)) {
          // for homotypic synonyms also create a homotypic name relation
          if (u.homotypic) {
            NeoRel rel = new NeoRel();
            rel.setType(RelType.HOMOTYPIC);
            rel.setVerbatimKey(v.getId());
            store.createNeoRel(u.nameNode, acc.nameNode, rel);
          }
        } else {
          v.add(Issue.ACCEPTED_ID_INVALID);
        }
      }
      
      if (!accepted.isEmpty() && !u.isSynonym()) {
        u.convertToSynonym(TaxonomicStatus.SYNONYM);
        // store the updated object
        store.usages().update(u, tx);
      }
    }
    
    // if status is synonym but we ain't got no idea of the accepted flag it
    if (accepted.isEmpty() && (u.isSynonym() || v.contains(Issue.ACCEPTED_ID_INVALID))) {
      v.add(Issue.ACCEPTED_NAME_MISSING);
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
  private void insertParentRel(NeoUsage u, VerbatimRecord v, Transaction tx) {
    if (v != null && meta.isParentNameMapped()) {
      List<RankedUsage> parents = usagesByIdOrName(v, u, false, DwcTerm.parentNameUsageID, Issue.PARENT_ID_INVALID, DwcTerm.parentNameUsage, Origin.VERBATIM_PARENT, tx);
      if (!parents.isEmpty()) {
        store.assignParent(parents.get(0).usageNode, u.node);
      }
    }
  }

  private void insertBasionymRel(NeoName n, VerbatimRecord v, Transaction tx) {
    if (v != null && meta.isOriginalNameMapped()) {
      RankedName bas = nameByIdOrName(v, n, DwcTerm.originalNameUsageID, Issue.BASIONYM_ID_INVALID, DwcTerm.originalNameUsage, Origin.VERBATIM_BASIONYM, tx);
      if (bas != null) {
        NeoRel rel = new NeoRel();
        rel.setType(RelType.HAS_BASIONYM);
        rel.setVerbatimKey(v.getId());
        store.createNeoRel(n.node, bas.nameNode, rel);
      }
    }
  }
  
  /**
   * Reads a verbatim given term that should represent a foreign key to another record via the taxonID.
   * If the value is not the same as the original records taxonID it tries to split the ids into multiple keys and lookup the matching nodes.
   *
   * Ignores IDs and names which are exactly the same as the NeoUsage t - often the terms are used to point to itself for accepted names or basionyms.
   *
   * @return queue of potentially split ids with their matching neo node if found, otherwise null
   */
  private List<RankedUsage> usagesByIdOrName(VerbatimRecord v, NeoUsage t, boolean allowMultiple, DwcTerm idTerm, Issue invalidIdIssue,
                                             DwcTerm nameTerm, Origin createdNameOrigin, Transaction tx
  ) {
    List<RankedUsage> usages = Lists.newArrayList();
    final String unsplitIds = v.getRaw(idTerm);
    boolean pointsToSelf = unsplitIds != null && unsplitIds.equals(t.getId());
    if (pointsToSelf) return usages;

    if (unsplitIds != null) {
      if (allowMultiple && meta.getMultiValueDelimiters().containsKey(idTerm)) {
        usages.addAll(usagesByIds(meta.getMultiValueDelimiters().get(idTerm).splitToList(unsplitIds), t, tx));
      } else {
        // match by taxonID to see if this is an existing identifier or if we should try to split it
        Node a = store.usages().nodeByID(unsplitIds, tx);
        if (a != null) {
          usages.add(NeoProperties.getRankedUsage(a));
        
        } else if (allowMultiple){
          for (Splitter splitter : COMMON_SPLITTER) {
            List<String> vals = splitter.splitToList(unsplitIds);
            if (vals.size() > 1) {
              usages.addAll(usagesByIds(vals, t, tx));
              break;
            }
          }
        }
      }
      // could not find anything?
      if (usages.isEmpty()) {
        v.add(invalidIdIssue);
        LOG.info("{} {} not existing", idTerm.simpleName(), unsplitIds);
      }
    }

    if (usages.isEmpty() && v.hasTerm(nameTerm)) {
      // try to setup rel via the name if it is different
      String relatedName = v.get(nameTerm);
      pointsToSelf = relatedName.equals(t.usage.getName().getScientificName());
      if (!pointsToSelf) {
        RankedUsage ru = usageByName(nameTerm, v, t, createdNameOrigin, tx);
        if (ru != null) {
          usages.add(ru);
        }
      }
    }
    return usages;
  }
  
  private List<RankedUsage> usagesByIds(Iterable<String> taxonIDs, DSID usage, Transaction tx) {
    List<RankedUsage> rankedNames = Lists.newArrayList();
    for (String id : taxonIDs) {
      if (!id.equals(usage.getId())) {
        Node n = store.usages().nodeByID(id, tx);
        if (n != null) {
          rankedNames.add(NeoProperties.getRankedUsage(n));
        }
      }
    }
    return rankedNames;
  }
  
  
  private RankedName nameByIdOrName(VerbatimRecord v, NeoName nn, DwcTerm idTerm, Issue invalidIdIssue, DwcTerm nameTerm, Origin createdOrigin, Transaction tx) {
    final String id = v.getRaw(idTerm);
    Node n = null;
    if (id != null && !id.equals(nn.getId())) {
      n = store.names().nodeByID(id, tx);
      // could not find anything?
      if (n == null) {
        v.add(invalidIdIssue);
        LOG.debug("{} {} not existing", idTerm.simpleName(), id);
      }
    }
    
    // if nothing til here, try by name
    if (n == null) {
      return nameByName(nameTerm, v, nn, createdOrigin, tx);
    
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
  private RankedName nameByName(DwcTerm term, VerbatimRecord v, NeoName n, Origin createdOrigin, Transaction tx) {
    return byName(term, v, new RankedName(n), false, tx,
      NeoProperties::getRankedName,
      name -> {
          name.setOrigin(createdOrigin);
          NeoName nn = new NeoName((name));
          nn.setVerbatimKey(v.getId());
          Node n2 = store.names().create(nn, tx);
          return new RankedName(n2, name.getScientificName(), name.getAuthorship(), name.getRank());
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
  private RankedUsage usageByName(DwcTerm term, VerbatimRecord v, NeoUsage u, final Origin createdOrigin, Transaction tx) {
    return byName(term, v, NeoProperties.getRankedUsage(u), true, tx,
        NeoProperties::getRankedUsage,
        name -> store.createProvisionalUsageFromSource(createdOrigin, name, u, u.usage.getName().getRank(), tx));
  }
  
  private Set<Node> usagesByNamesPreferAccepted(Set<Node> nameNodes) {
    // convert to usages
    Set<Node> usageMatches = nameNodes.stream()
        .map(store::usageNodesByName)
        .flatMap(List::stream)
        .collect(Collectors.toSet());
  
    // if multiple matches remove non accepted names
    if (usageMatches.size() > 1) {
      Set<Node> accepted = new HashSet<>();
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
                                          boolean transformToUsages, Transaction tx,
                                          Function<Node, T> getByNode,
                                          Function<Name, T> createMissing) {
    if (v.hasTerm(term)) {
      final Name name = NameParser.PARSER.parse(v.get(term)).get().getName();
      // force unranked name for non binomials or unparsed names, avoiding wrong parser decisions
      if (!name.isParsed() || !name.isBinomial()) {
        name.setRank(Rank.UNRANKED);
      }
      if (!name.getScientificName().equalsIgnoreCase(source.name)) {
        Set<Node> matches = store.names().nodesByName(name.getScientificName(), tx);
        // remove other authors, but allow names without authors
        if (!matches.isEmpty() && name.hasAuthorship()) {
          matches.removeIf(n -> !Strings.isNullOrEmpty(NeoProperties.getAuthorship(n))
            && !StringUtils.equalsIgnoreCaseAndSpace(NeoProperties.getAuthorship(n), name.getAuthorship())
          );
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
            v.add(Issue.NAME_NOT_UNIQUE);
          }
          return getByNode.apply(matches.iterator().next());
        }
      }
    }
    return null;
  }
}
