package org.col.db.tree;

import java.io.IOException;
import java.io.Writer;
import java.util.*;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.*;
import org.col.api.util.ObjectUtils;
import org.col.db.mapper.NameUsageMapper;
import org.col.db.mapper.SectorMapper;
import org.gbif.nameparser.api.Rank;

/**
 * Print an entire dataset in the indented text format used by TxtPrinter.
 * Synonyms are prefixed with an asterisk *,
 * Pro parte synoynms with a double asterisk **,
 * basionyms are prefixed by a $ and listed first in the synonymy.
 * <p>
 * Ranks are given in brackets after the scientific name
 * <p>
 * A basic example tree would look like this:
 * <pre>
 * Plantae [kingdom]
 * Compositae Giseke [family]
 * Asteraceae [family]
 * Artemisia L. [genus]
 * Artemisia elatior (Torr. & A. Gray) Rydb.
 * $Artemisia tilesii var. elatior Torr. & A. Gray
 * $Artemisia rupestre Schrank L. [species]
 * Absinthium rupestre (L.) Schrank [species]
 * Absinthium viridifolium var. rupestre (L.) Besser
 * </pre>
 */
public class TextTreePrinter implements ResultHandler<SimpleName> {
  public static final String SYNONYM_SYMBOL = "*";
  public static final String BASIONYM_SYMBOL = "$";
  
  private static final int indentation = 2;
  private int level = 0;
  private int counter = 0;
  private final Writer writer;
  private final int datasetKey;
  private final Integer sectorKey;
  private final String startID;
  private final Set<Rank> ranks;
  private final Rank lowestRank;
  private final SqlSessionFactory factory;
  private SqlSession session;
  private final LinkedList<SimpleName> parents = new LinkedList<>();
  
  /**
   * @param sectorKey optional sectorKey to restrict printed tree to
   */
  private TextTreePrinter(int datasetKey, Integer sectorKey, String startID, Set<Rank> ranks, SqlSessionFactory factory, Writer writer) {
    this.datasetKey = datasetKey;
    this.startID = startID;
    this.sectorKey = sectorKey;
    this.factory = factory;
    this.writer = writer;
    this.ranks = ObjectUtils.coalesce(ranks, Collections.EMPTY_SET);
    if (!this.ranks.isEmpty()) {
      // spot lowest rank
      LinkedList<Rank> rs = new LinkedList<>(this.ranks);
      Collections.sort(rs);
      lowestRank = rs.getLast();
    } else {
      lowestRank = null;
    }
  }
  
  public static TextTreePrinter dataset(int datasetKey, SqlSessionFactory factory, Writer writer) {
    return new TextTreePrinter(datasetKey, null, null, null, factory, writer);
  }
  
  public static TextTreePrinter dataset(int datasetKey, String startID, Set<Rank> ranks, SqlSessionFactory factory, Writer writer) {
    return new TextTreePrinter(datasetKey, null, startID, ranks, factory, writer);
  }

  /**
   * Prints a sector from the given catalogue.
   */
  public static TextTreePrinter sector(int catalogueKey, final int sectorKey, SqlSessionFactory factory, Writer writer) {
    try (SqlSession session = factory.openSession(true)) {
      Sector s = session.getMapper(SectorMapper.class).get(sectorKey);
      return new TextTreePrinter(catalogueKey, sectorKey, s.getTarget().getId(), null, factory, writer);
    }
  }
  
  /**
   * @return number of written lines, i.e. name usages
   * @throws IOException
   */
  public int print() throws IOException {
    counter = 0;
    try {
      session = factory.openSession(true);
      NameUsageMapper num = session.getMapper(NameUsageMapper.class);
      num.processTreeSimple(datasetKey, sectorKey, startID, null, lowestRank, true, this);

    } finally {
      writer.flush();
      session.close();
    }
    return counter;
  }
  
  public int getCounter() {
    return counter;
  }
  
  @Override
  public void handleResult(ResultContext<? extends SimpleName> resultContext) {
    try {
      SimpleName u = resultContext.getResultObject();
      // send end signals
      while (!parents.isEmpty() && !parents.peekLast().getId().equals(u.getParent())) {
        end(parents.removeLast());
      }
      start(u);
      parents.add(u);
      
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void start(SimpleName u) throws IOException {
    if (ranks.isEmpty() || ranks.contains(u.getRank())) {
      counter++;
      writer.write(StringUtils.repeat(' ', level * indentation));
      if (u.getStatus() != null && u.getStatus().isSynonym()) {
        writer.write(SYNONYM_SYMBOL);
      }
      //TODO: flag basionyms
      writer.write(u.getName());
      if (u.getRank() != null) {
        writer.write(" [");
        writer.write(u.getRank().name().toLowerCase());
        writer.write("]");
      }
      
      writer.write('\n');
      level++;
    }
  }
  
  private void end(SimpleName u) {
    if (ranks.isEmpty() || ranks.contains(u.getRank())) {
      level--;
    }
  }
  
}
