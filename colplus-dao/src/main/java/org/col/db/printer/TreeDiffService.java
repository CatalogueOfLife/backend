package org.col.db.printer;

import java.util.Iterator;
import java.util.List;

import com.github.difflib.DiffUtils;
import com.github.difflib.algorithm.DiffException;
import com.github.difflib.patch.Patch;
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
import org.col.common.collection.LimitedQueue;
import org.col.db.mapper.SectorImportMapper;

public class TreeDiffService {
  private final SqlSessionFactory factory;
  private final static Splitter ATTEMPTS = Splitter.on("..").trimResults();
  private final static Splitter LINE_SPLITTER = Splitter.on('\n');
  
  public TreeDiffService(SqlSessionFactory factory) {
    this.factory = factory;
  }
  
  public String diff(int sectorKey, String attempts) throws DiffException {
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
  
  public String diff(int sectorKey, int attempt1, int attempt2) throws DiffException, NotFoundException {
    try (SqlSession session = factory.openSession(true)) {
      SectorImportMapper sim = session.getMapper(SectorImportMapper.class);
      SectorImport s1 = sim.get(sectorKey, attempt1);
      SectorImport s2 = sim.get(sectorKey, attempt2);
      if (s1 == null || s2 == null) {
        throw new NotFoundException("Sector "+sectorKey+" sync attempts "+attempt1+".."+attempt2+" not existing");
      }
      return diff(0, attempt1, s1.getTextTree(), attempt2, s2.getTextTree());
    }
  }
  
  static String diff(int context, int attempt1, String t1, int attempt2, String t2) throws DiffException {
    DiffRowGenerator generator = DiffRowGenerator.create()
        .showInlineDiffs(true)
        .inlineDiffByWord(true)
        .reportLinesUnchanged(false)
        .ignoreWhiteSpaces(false)
        .mergeOriginalRevised(false)
        .oldTag(f -> "~")      //introduce markdown style for strikethrough
        .newTag(f -> "**")     //introduce markdown style for bold
        .build();
  
    List<DiffRow> rows = generator.generateDiffRows(LINE_SPLITTER.splitToList(t1), LINE_SPLITTER.splitToList(t2));
  
    StringBuilder sb = new StringBuilder();
    sb.append("|attempt "+attempt1+"|attempt "+attempt2+"|\n");
    sb.append("|--------|---|\n");
    
    if (context > 0) {
      LimitedQueue<DiffRow> queue = new LimitedQueue(context);
      int postCtxt = 0;
      for (DiffRow row : rows) {
        if (DiffRow.Tag.EQUAL != row.getTag()) {
          for (DiffRow qr : queue) {
            print(sb, qr);
          }
          print(sb, row);
          postCtxt = context;
        } else {
          if (postCtxt > 0) {
            postCtxt--;
            print(sb, row);
          } else {
            queue.add(row);
          }
        }
      }
      
    } else {
      for (DiffRow qr : rows) {
        print(sb, qr);
      }
    }
    
    return sb.toString();
  }
  
  private static void print(StringBuilder sb, DiffRow row) {
    sb.append("|" + row.getOldLine() + "|" + row.getNewLine() + "|\n");
  }
  
  static Patch<String> unifiedDiff(String t1, String t2) throws DiffException {
    Patch<String> patch = DiffUtils.diff(LINE_SPLITTER.splitToList(t1), LINE_SPLITTER.splitToList(t2));
    return patch;
  }
}
