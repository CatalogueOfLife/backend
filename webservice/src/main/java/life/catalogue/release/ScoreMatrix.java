package life.catalogue.release;

import life.catalogue.api.model.SimpleNameWithNidx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiFunction;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import it.unimi.dsi.fastutil.ints.*;

public class ScoreMatrix {
  private static final int DELETED_N = -1;
  private static final int DELETED_R = -2;
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
                     BiFunction<SimpleNameWithNidx, ReleasedIds.ReleasedId,Integer> scorer) {
    this.names = names;
    this.releasedIds = releasedIds;
    lenN = names.size();
    lenR = releasedIds.length;
    scores = new int[lenN][lenR];
    int ni=0;
    IntSet distinctScores = new IntOpenHashSet();
    for (SimpleNameWithNidx n : names){
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

  public static class ReleaseMatch implements Comparable<ReleaseMatch>{
    private static final Comparator<ReleaseMatch> NATURAL_ORDER =
      Comparator.<ReleaseMatch, Integer>comparing(m -> m.rid.id, Integer::compare)
        .thenComparing(m -> m.rid.attempt, Integer::compare)
        .thenComparing(m -> m.name, Comparator.naturalOrder());

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

    @Override
    public int compareTo(@NotNull ReleaseMatch o) {
      return NATURAL_ORDER.compare(this, o);
    }
  }

  private List<ReleaseMatch> match(int highscore) {
    final List<ReleaseMatch> next = new ArrayList<>();
    for (int ni=0; ni<lenN; ni++) {
      // we only mark the first row or column as deleted - so just check those
      if (scores[ni][0] != -1 && scores[ni][0] != -3) {
        for (int ri=0; ri<lenR; ri++) {
          if (scores[0][ri] != -2 && scores[0][ri] != -3 && scores[ni][ri] == highscore) {
            next.add(new ReleaseMatch(ni, ri, highscore, names.get(ni), releasedIds[ri]));
          }
        }
      }
    }
    return next;
  }

  /**
   * return the batch of matches with the highest, equal score.
   * In case there have been multiple previous ids, sort lowest ID first to keep stability.
   * As IDs are issued incrementally the smallest ID is always the oldest too.
   */
  public List<ReleaseMatch> highest() {
    List<ReleaseMatch> next = new ArrayList<>();
    while (next.isEmpty() && !distinctScores.isEmpty()) {
      next = match(distinctScores.removeInt(0));
    }
    // natural sort order defines the relevance
    Collections.sort(next);
    return next;
  }

  public void remove(ReleaseMatch rm){
    // we mark only the first rows or columns
    // the first row & column, 0/0, represents both so we must be able to distinguish between row or column markup
    // for this we add up both
    if (rm.namesIdx==0) {
      scores[rm.namesIdx][0] = Math.min(scores[rm.namesIdx][0], 0) + DELETED_N;
    } else {
      scores[rm.namesIdx][0] = DELETED_N;
    }

    if (rm.relIdx == 0) {
      scores[0][rm.relIdx] = Math.min(scores[0][rm.relIdx], 0) + DELETED_R;
    } else {
      scores[0][rm.relIdx] = DELETED_R;
    }
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
