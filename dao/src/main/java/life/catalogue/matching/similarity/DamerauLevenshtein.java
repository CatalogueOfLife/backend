package life.catalogue.matching.similarity;

public class DamerauLevenshtein implements StringSimilarity {

  public static double similarity(String x1, String x2) {
    return DistanceUtils.convertEditDistanceToSimilarity(new DamerauLevenshteinDistance(x1, x2).getEditDistance(), x1, x2);
  }

  @Override
  public double getSimilarity(String x1, String x2) {
    return similarity(x1, x2);
  }
  
  static class DamerauLevenshteinDistance {
    
    private String compOne;
    private String compTwo;
    private int[][] matrix;
    
    DamerauLevenshteinDistance(String a, String b) {
      if ((a.length() > 0 || !a.isEmpty()) || (b.length() > 0 || !b.isEmpty())) {
        compOne = a;
        compTwo = b;
      }
      setupMatrix();
    }
    
    public int getEditDistance() {
      return matrix[compOne.length()][compTwo.length()];
    }
    
    private void setupMatrix() {
      int cost = -1;
      int del, sub, ins;
      
      matrix = new int[compOne.length() + 1][compTwo.length() + 1];
      
      for (int i = 0; i <= compOne.length(); i++) {
        matrix[i][0] = i;
      }
      
      for (int i = 0; i <= compTwo.length(); i++) {
        matrix[0][i] = i;
      }
      
      for (int i = 1; i <= compOne.length(); i++) {
        for (int j = 1; j <= compTwo.length(); j++) {
          if (compOne.charAt(i - 1) == compTwo.charAt(j - 1)) {
            cost = 0;
          } else {
            cost = 2;
          }
          
          del = matrix[i - 1][j] + 1;
          ins = matrix[i][j - 1] + 1;
          sub = matrix[i - 1][j - 1] + cost;
          
          matrix[i][j] = minimum(del, ins, sub);
          
          if ((i > 1) && (j > 1) && (compOne.charAt(i - 1) == compTwo.charAt(j - 2)) && (compOne.charAt(i - 2) == compTwo
              .charAt(j - 1))) {
            matrix[i][j] = minimum(matrix[i][j], matrix[i - 2][j - 2] + cost);
          }
        }
      }
    }
    
    private int minimum(int d, int i, int s) {
      int m = Integer.MAX_VALUE;
      
      if (d < m) m = d;
      if (i < m) m = i;
      if (s < m) m = s;
      
      return m;
    }
    
    private int minimum(int d, int t) {
      int m = Integer.MAX_VALUE;
      
      if (d < m) m = d;
      if (t < m) m = t;
      
      return m;
    }
  }
}