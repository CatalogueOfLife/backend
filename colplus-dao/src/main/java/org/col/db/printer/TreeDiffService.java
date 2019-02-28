package org.col.db.printer;

import java.util.Iterator;
import java.util.List;

import com.github.difflib.algorithm.DiffException;
import com.github.difflib.text.DiffRow;
import com.github.difflib.text.DiffRowGenerator;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.exception.NotFoundException;
import org.col.api.model.Page;
import org.col.api.model.SectorImport;
import org.col.db.mapper.SectorImportMapper;

public class TreeDiffService {
  private final SqlSessionFactory factory;
  private final static Splitter ATTEMPTS = Splitter.on("..").trimResults();
  private final static Splitter LINE_SPLITTER = Splitter.on('\n');
  
  public TreeDiffService(SqlSessionFactory factory) {
    this.factory = factory;
  }
  
  public TreeDiff diff(int sectorKey, String attempts) throws DiffException {
    int a1;
    int a2;
    try (SqlSession session = factory.openSession(true)) {
      if (StringUtils.isBlank(attempts)) {
        SectorImportMapper sim = session.getMapper(SectorImportMapper.class);
        List<SectorImport> imports = sim.list(sectorKey, Lists.newArrayList(SectorImport.State.FINISHED), new Page(0, 2));
        if (imports.size()<2) {
          throw new NotFoundException("At least 2 successful imports must exist to provide a diff for sector " + sectorKey);
        }
        a1=imports.get(1).getAttempt();
        a2=imports.get(0).getAttempt();
      } else {
        Iterator<String> attIter = ATTEMPTS.split(attempts).iterator();
        a1 = Integer.parseInt(attIter.next());
        a2 = Integer.parseInt(attIter.next());
      }
      return diff(sectorKey, a1, a2);
    
    } catch (DiffException | NotFoundException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("Range of attempts to be separated by ..", e);
    }
  }
  
  public TreeDiff diff(int sectorKey, int attempt1, int attempt2) throws DiffException, NotFoundException {
    try (SqlSession session = factory.openSession(true)) {
      SectorImportMapper sim = session.getMapper(SectorImportMapper.class);
      SectorImport s1 = sim.get(sectorKey, attempt1);
      SectorImport s2 = sim.get(sectorKey, attempt2);
      if (s1 == null || s2 == null) {
        throw new NotFoundException("Sector "+sectorKey+" sync attempts "+attempt1+".."+attempt2+" not existing");
      }
      return diff(sectorKey, attempt1, s1.getTextTree(), attempt2, s2.getTextTree());
    }
  }
  
  static TreeDiff diff(int sectorKey, int attempt1, String t1, int attempt2, String t2) throws DiffException {
    final TreeDiff diff = new TreeDiff(sectorKey, attempt1, attempt2);
    DiffRowGenerator generator = DiffRowGenerator.create()
        .reportLinesUnchanged(true)
        .build();
    // remove new line in case of equals
    for (DiffRow row : generator.generateDiffRows(LINE_SPLITTER.splitToList(t1), LINE_SPLITTER.splitToList(t2))) {
      diff.add(row);
    }
    return diff;
  }
  
}
