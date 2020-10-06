package life.catalogue.release;

import it.unimi.dsi.fastutil.ints.*;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.SimpleNameWithNidx;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

public class ScoreMatrix {
  private static final int DELETED = -1;
  private final int lenN;
  private final int lenR;
  // first dimension=names idx, second=candidates idx
  private final int[][] scores;
  private final IntList distinctScores;
  private final List<SimpleNameWithNidx> names;
  private final ReleasedIds.ReleasedId[] releasedIds;

  /**
   * Produces a match score matrix of all names against all candidates.
   * @param scorer function to generate a score with 0=nomatch, the higher the better the match
   */
  public ScoreMatrix(List<SimpleNameWithNidx> names, ReleasedIds.ReleasedId[] releasedIds,
                     BiFunction<SimpleName, ReleasedIds.ReleasedId,Integer> scorer) {
    this.names = names;
    this.releasedIds = releasedIds;
    lenN = names.size();
    lenR = releasedIds.length;
    scores = new int[lenN][lenR];
    int ni=0;
    IntSet distinctScores = new IntOpenHashSet();
    for (SimpleName n : names){
      int ri=0;
      for (ReleasedIds.ReleasedId r : releasedIds){
        int score = Math.max(scorer.apply(n, r), 0);
        scores[ni][ri++] = score;
        distinctScores.add(score);
      }
      ni++;
    }
    // zero is no match, dont return
    distinctScores.remove(0);
    this.distinctScores = new IntArrayList(distinctScores);
    this.distinctScores.sort(IntComparators.OPPOSITE_COMPARATOR);
  }

  public static class ReleaseMatch {
    private final int namesIdx;
    private final int relIdx;
    final int score;
    final SimpleNameWithNidx name;
    final ReleasedIds.ReleasedId rid;

    public ReleaseMatch(int namesIdx, int relIdx, int score, SimpleNameWithNidx name, ReleasedIds.ReleasedId rid) {
      this.namesIdx = namesIdx;
      this.relIdx = relIdx;
      this.score = score;
      this.name = name;
      this.rid = rid;
    }
  }

  private List<ReleaseMatch> match(int highscore) {
    final List<ReleaseMatch> next = new ArrayList<>();
    for (int ni=0; ni<lenN; ni++) {
      // we only mark the first row or column as deleted - so just check those
      if (scores[ni][0] != DELETED) {
        for (int ri=0; ri<lenR; ri++) {
          if (scores[0][ri] != DELETED && scores[ni][ri] == highscore) {
            next.add(new ReleaseMatch(ni, ri, highscore, names.get(ni), releasedIds[ri]));
          }
        }
      }
    }
    return next;
  }

  public List<ReleaseMatch> highest() {
    List<ReleaseMatch> next = new ArrayList<>();
    while (next.isEmpty() && !distinctScores.isEmpty()) {
      next = match(distinctScores.removeInt(0));
    }
    if (!next.isEmpty()) {
      System.out.println("found "+next.size()+" matches with score " + next.get(0).score);
    }
    return next;
  }

  public void remove(ReleaseMatch rm){
    scores[rm.namesIdx][0] = DELETED;
    scores[0][rm.relIdx] = DELETED;
  }

  public void printMatrix(){
    System.out.println(StringUtils.repeat("---", lenR));
    for (int ni=0; ni<lenN; ni++) {
      StringBuilder sb = new StringBuilder();
      for (int ri=0; ri<lenR; ri++) {
        sb.append(String.format("% 3d", scores[ni][ri]));
      }
      System.out.println(sb);
    }
  }
}
