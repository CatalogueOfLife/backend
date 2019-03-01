package org.col.db.tree;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiFunction;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.algorithm.DiffException;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.DeltaType;
import com.github.difflib.patch.Patch;
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

  private static String diffFileName(SectorImport imp) {
    return "sector"+imp.getSectorKey()+"-attempt"+imp.getAttempt()+".txt";
  }
  
  static DiffReport.TreeDiff treeDiff(SectorImport imp1, SectorImport imp2) {
    final DiffReport.TreeDiff diff = new DiffReport.TreeDiff(imp1.getSectorKey(), imp1.getAttempt(), imp2.getAttempt());
    try {
      List<String> orig = LINE_SPLITTER.splitToList(imp1.getTextTree());
      List<String> revised = LINE_SPLITTER.splitToList(imp2.getTextTree());
      
      Patch<String> patch = DiffUtils.diff(orig, revised);
      for (AbstractDelta d : patch.getDeltas()) {
        diff.incSummary(d.getType());
      }
      List<String> udiff = UnifiedDiffUtils.generateUnifiedDiff(diffFileName(imp1), diffFileName(imp2), orig, patch, 3);
      StringBuilder sb = new StringBuilder();
      for (String l : udiff) {
        sb.append(l);
        sb.append('\n');
      }
      diff.setDiff(sb.toString());
    } catch (DiffException e) {
      throw new RuntimeException(e);
    }
    return diff;
  }
  
  static DiffReport.NamesDiff namesDiff(SectorImport imp1, SectorImport imp2) {
    final DiffReport.NamesDiff diff = new DiffReport.NamesDiff(imp1.getSectorKey(), imp1.getAttempt(), imp2.getAttempt());
    diff.setDeleted(new HashSet<>(imp1.getNames()));
    diff.getDeleted().removeAll(imp2.getNames());
  
    diff.setSummary(DeltaType.EQUAL, imp1.getNames().size() - diff.getDeleted().size());
  
    diff.setInserted(new HashSet<>(imp2.getNames()));
    diff.getInserted().removeAll(imp1.getNames());

    return diff;
  }
}
