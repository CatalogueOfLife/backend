package org.col.dao;

import java.io.*;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.Sector;
import org.col.common.io.Utf8IOUtils;
import org.col.db.mapper.NameMapper;
import org.col.db.mapper.SectorMapper;
import org.col.db.tree.TextTreePrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DAO giving read and write access to potentially large text trees and name lists
 * stored on the filesystem. We use compression to keep storage small.
 */
public class NamesTreeDao {
  private static final Logger LOG = LoggerFactory.getLogger(NamesTreeDao.class);
  
  private final SqlSessionFactory factory;
  private final File repo;
  
  public NamesTreeDao(SqlSessionFactory factory, File repo) {
    this.factory = factory;
    this.repo = repo;
  }
  
  public static Set<String> readNames(File nf) throws IOException{
    try (BufferedReader br = Utf8IOUtils.readerFromFile(nf)) {
      return br.lines().collect(Collectors.toSet());
    }
  }
  
  public int updateDatasetNames(int datasetKey, int attempt) {
    int count;
    try (SqlSession session = factory.openSession(true);
        NamesWriter handler = new NamesWriter(namesFile(datasetKey, attempt))
    ){
      NameMapper nm = session.getMapper(NameMapper.class);
      nm.processIndexIds(datasetKey, null, handler);
      count = handler.counter;
      LOG.info("Written {} index names for dataset {}-{}", count, datasetKey, attempt);
    }
    return count;
  }
  
  public int updateSectorNames(int sectorKey, int attempt) {
    int count;
    try (SqlSession session = factory.openSession(true);
         NamesWriter handler = new NamesWriter(sectorNamesFile(sectorKey, attempt))
    ){
      SectorMapper sm = session.getMapper(SectorMapper.class);
      NameMapper nm = session.getMapper(NameMapper.class);
      Sector s = sm.get(sectorKey);
      nm.processIndexIds(s.getSubjectDatasetKey(), sectorKey, handler);
      count = handler.counter;
      LOG.info("Written {} index names for sector {}-{}", count, sectorKey, attempt);
    }
    return count;
  }

  static class NamesWriter implements ResultHandler<String>, AutoCloseable {
    public int counter = 0;
    private final File f;
    private final BufferedWriter w;
    
    NamesWriter(File f) {
      this.f=f;
      try {
        w = Utf8IOUtils.writerFromFile(f);
        
      } catch (IOException e) {
        LOG.error("Failed to write to {}", f.getAbsolutePath());
        throw new RuntimeException(e);
      }
    }
  
    @Override
    public void close() {
      try {
        w.close();
      } catch (IOException e) {
        LOG.error("Failed to close {}", f.getAbsolutePath());
      }
    }
  
    @Override
    public void handleResult(ResultContext<? extends String> resultContext) {
      try {
        String id = resultContext.getResultObject();
        if (id != null) {
          counter++;
          w.append(id);
          w.append('\n');
        }
      } catch (IOException e) {
        LOG.error("Failed to write to {}", f.getAbsolutePath());
        throw new RuntimeException(e);
      }
    }
  }

  public int updateDatasetTree(int datasetKey, int attempt) throws IOException {
    Writer writer = Utf8IOUtils.writerFromFile(treeFile(datasetKey, attempt));
    int count = TextTreePrinter.dataset(datasetKey, factory, writer).print();
    LOG.info("Written text tree with {} lines for dataset {}-{}", count, datasetKey, attempt);
    return count;
  }
  
  public int updateSectorTree(int sectorKey, int attempt) throws IOException {
    Writer writer = Utf8IOUtils.writerFromFile(sectorTreeFile(sectorKey, attempt));
    int count = 0;
    try (SqlSession session = factory.openSession(true)) {
      Sector s = session.getMapper(SectorMapper.class).get(sectorKey);
      count = TextTreePrinter.sector(s.getSubjectDatasetKey(), sectorKey, factory, writer).print();
    }
    LOG.info("Written text tree with {} lines for sector {}-{}", count, sectorKey, attempt);
    return count;
  }

  public Stream<String> getDatasetNames(int datasetKey, int attempt) {
    return streamFile(namesFile(datasetKey, attempt));
  }
  
  public Stream<String> getDatasetTree(int datasetKey, int attempt) {
    return streamFile(treeFile(datasetKey, attempt));
  }
  
  public Stream<String> getSectorNames(int sectorKey, int attempt) {
    return streamFile(sectorNamesFile(sectorKey, attempt));
  }
  
  public Stream<String> getSectorTree(int sectorKey, int attempt) {
    return streamFile(sectorTreeFile(sectorKey, attempt));
  }

  private static Stream<String> streamFile(File f) {
    try {
      BufferedReader br = Utf8IOUtils.readerFromFile(f);
      return br.lines();
    } catch (IOException e) {
      LOG.warn("Failed to stream file {}", f.getAbsolutePath());
      return Stream.empty();
    }
  }
  
  public File sectorTreeFile(int sectorKey, int attempt) {
    return new File(sectorDir(sectorKey), "tree/"+attempt+".txt");
  }
  
  public File sectorNamesFile(int sectorKey, int attempt) {
    return new File(sectorDir(sectorKey), "names/"+attempt+".txt");
  }
  
  public File treeFile(int datasetKey, int attempt) {
    return new File(datasetDir(datasetKey), "tree/"+attempt+".txt");
  }
  
  public File namesFile(int datasetKey, int attempt) {
    return new File(datasetDir(datasetKey), "names/"+attempt+".txt");
  }
  
  private File datasetDir(int datasetKey) {
    return new File(repo, "dataset/" + datasetKey);
  }
  
  private File sectorDir(int sectorKey) {
    return new File(repo, "sector" + sectorKey);
  }
}
