package org.col.db.tree;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiFunction;

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

public class DiffService {
  private final SqlSessionFactory factory;
  private final static Splitter ATTEMPTS = Splitter.on("..").trimResults();
  private final static Splitter LINE_SPLITTER = Splitter.on('\n');
  
  public DiffService(SqlSessionFactory factory) {
    this.factory = factory;
  }
  
  public DiffReport.TreeDiff treeDiff(int sectorKey, String attempts) {
    return diff(sectorKey, attempts, DiffService::treeDiff);
  }
  
  public DiffReport.NamesDiff namesDiff(int sectorKey, String attempts) {
    return diff(sectorKey, attempts, DiffService::namesDiff);
  }

  
  private <T extends DiffReport> T diff(int sectorKey, String attempts, BiFunction<SectorImport,SectorImport,T> diffFunc) {
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
      return diff(sectorKey, a1, a2, diffFunc);
      
    } catch (IllegalArgumentException | NotFoundException e) {
      throw e;
    }
  }
  
  private <T extends DiffReport> T diff(int sectorKey, int attempt1, int attempt2, BiFunction<SectorImport,SectorImport,T> diffFunc) {
    try (SqlSession session = factory.openSession(true)) {
      SectorImportMapper sim = session.getMapper(SectorImportMapper.class);
      SectorImport s1 = sim.get(sectorKey, attempt1);
      SectorImport s2 = sim.get(sectorKey, attempt2);
      if (s1 == null || s2 == null) {
        throw new NotFoundException("Sector "+sectorKey+" sync attempts "+attempt1+".."+attempt2+" not existing");
      }
      return diffFunc.apply(s1, s2);
    }
  }
  
  static DiffReport.TreeDiff treeDiff(SectorImport imp1, SectorImport imp2) {
    final DiffReport.TreeDiff diff = new DiffReport.TreeDiff(imp1.getSectorKey(), imp1.getAttempt(), imp2.getAttempt());
    DiffRowGenerator generator = DiffRowGenerator.create()
        .reportLinesUnchanged(true)
        .build();
    // remove new line in case of equals
    try {
      for (DiffRow row : generator.generateDiffRows(LINE_SPLITTER.splitToList(imp1.getTextTree()), LINE_SPLITTER.splitToList(imp2.getTextTree()))) {
        diff.add(row);
      }
    } catch (DiffException e) {
      throw new RuntimeException(e);
    }
    return diff;
  }
  
  static DiffReport.NamesDiff namesDiff(SectorImport imp1, SectorImport imp2) {
    final DiffReport.NamesDiff diff = new DiffReport.NamesDiff(imp1.getSectorKey(), imp1.getAttempt(), imp2.getAttempt());
    diff.setDeleted(new HashSet<>(imp1.getNames()));
    diff.getDeleted().removeAll(imp2.getNames());
  
    diff.setInserted(new HashSet<>(imp2.getNames()));
    diff.getInserted().removeAll(imp1.getNames());

    return diff;
  }
}
