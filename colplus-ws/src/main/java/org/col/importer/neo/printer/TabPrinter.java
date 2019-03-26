package org.col.importer.neo.printer;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;

import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import org.col.importer.neo.model.RankedUsage;
import org.col.importer.neo.model.RelType;
import org.col.common.io.TabWriter;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Relationship;

/**
 * Dumps a normalizer db in a simple tab delimited format used by the nub integration tests.
 * Expects no pro parte relations in the walker!
 */
public class TabPrinter extends BasePrinter{
  private final TabWriter writer;
  private static final Joiner ID_CONCAT = Joiner.on(";").skipNulls();

  public TabPrinter(Writer writer) {
    super(true);
    this.writer = new TabWriter(writer);
  }
  
  @Override
  public void close() {
  }
  
  @Override
  public void start(RankedUsage u) {
    try {
      String[] row = new String[7];
      row[0] = String.valueOf(u.usageNode.getId());
      if (u.isSynonym()) {
        // we can have multiple accepted parents for pro parte synonyms
        Set<Long> parentKeys = Sets.newHashSet();
        for (Relationship synRel : u.usageNode.getRelationships(RelType.SYNONYM_OF, Direction.OUTGOING)) {
          parentKeys.add(synRel.getOtherNode(u.usageNode).getId());
        }
        row[1] = ID_CONCAT.join(parentKeys);
      } else {
        if (u.usageNode.hasRelationship(RelType.PARENT_OF, Direction.INCOMING)) {
          row[1] = String.valueOf(u.usageNode.getSingleRelationship(RelType.PARENT_OF, Direction.INCOMING).getOtherNode(u.usageNode).getId());
        }
      }
      if (u.nameNode.hasRelationship(RelType.HAS_BASIONYM, Direction.OUTGOING)) {
        row[2] = String.valueOf(u.nameNode.getSingleRelationship(RelType.HAS_BASIONYM, Direction.OUTGOING).getOtherNode(u.nameNode).getId());
      }
      if (u.rank != null) {
        row[3] = u.rank.name();
      }
      row[4] = u.isSynonym() ? "synonym" : "accepted";
      row[6] = u.getNameWithAuthor();
      writer.write(row);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

}
