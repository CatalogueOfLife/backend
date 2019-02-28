package org.col.db.tree;

import java.io.IOException;
import java.io.Writer;
import java.util.LinkedList;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.*;
import org.col.db.mapper.SectorMapper;
import org.col.db.mapper.SynonymMapper;
import org.col.db.mapper.TaxonMapper;

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
public class TextTreePrinter implements ResultHandler<Taxon> {
  public static final String SYNONYM_SYMBOL = "*";
  public static final String BASIONYM_SYMBOL = "$";
  
  private static final int indentation = 2;
  private int level = 0;
  private final Writer writer;
  private final int datasetKey;
  private final Integer sectorKey;
  private final String startID;
  private final SqlSessionFactory factory;
  private SqlSession session;
  private SynonymMapper sm;
  private final LinkedList<Taxon> parents = new LinkedList<>();
  
  /**
   * @param sectorKey optional sectorKey to restrict printed tree to
   */
  private TextTreePrinter(int datasetKey, Integer sectorKey, String startID, SqlSessionFactory factory, Writer writer) {
    this.datasetKey = datasetKey;
    this.startID = startID;
    this.sectorKey = sectorKey;
    this.factory = factory;
    this.writer = writer;
  }
  
  public static TextTreePrinter dataset(int datasetKey, SqlSessionFactory factory, Writer writer) {
    return new TextTreePrinter(datasetKey, null, null, factory, writer);
  }
  
  /**
   * Prints a sector from the given catalogue.
   */
  public static TextTreePrinter sector(int catalogueKey, final int sectorKey, SqlSessionFactory factory, Writer writer) {
    try (SqlSession session = factory.openSession(true)) {
      Sector s = session.getMapper(SectorMapper.class).get(sectorKey);
      return new TextTreePrinter(catalogueKey, sectorKey, s.getTarget().getId(), factory, writer);
    }
  }

  public void print() throws IOException {
    try {
      session = factory.openSession(true);
      sm = session.getMapper(SynonymMapper.class);
      TaxonMapper tm = session.getMapper(TaxonMapper.class);
      tm.processTree(datasetKey, sectorKey, startID, null, true, this);

    } finally {
      writer.flush();
      session.close();
    }
  }
  
  @Override
  public void handleResult(ResultContext<? extends Taxon> resultContext) {
    try {
      Taxon t = resultContext.getResultObject();
      // send end signals
      while (!parents.isEmpty() && !parents.peekLast().getId().equals(t.getParentId())) {
        end(parents.removeLast());
      }
      start(t);
      
      // synonyms
      for (Synonym s : sm.listByTaxon(datasetKey, t.getId())) {
        start(s);
        end(s);
      }
      parents.add(t);
    
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  private void start(Taxon t) throws IOException {
    print(t);
    level++;
  }
  
  private void start(Synonym s) throws IOException {
    print(s);
    level++;
  }

  private void end(NameUsage u) {
    level--;
  }
  
  private void print(Taxon t) throws IOException {
    printCore(t);
    if (t.getSectorKey() != null) {
      writer.write(" (S"+t.getSectorKey()+")");
    }
    writer.write("\n");
  }
  
  private void print(Synonym s) throws IOException {
    printCore(s);
    writer.write("\n");
  }
  
  private void printCore(NameUsage u) throws IOException {
    Name n = u.getName();
    writer.write(StringUtils.repeat(' ', level * indentation));
    if (u.isSynonym()) {
      writer.write(SYNONYM_SYMBOL);
    }
    //TODO: flag basionyms
    writer.write(n.canonicalName());
    if (n.getRank() != null) {
      writer.write(" [");
      writer.write(n.getRank().name().toLowerCase());
      writer.write("]");
    }
  }
  
}
