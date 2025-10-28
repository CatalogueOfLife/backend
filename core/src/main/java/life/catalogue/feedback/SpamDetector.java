package life.catalogue.feedback;

import life.catalogue.common.io.Resources;

import java.util.Arrays;
import java.util.Scanner;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

/**
 * Class that keeps a AST like token tree to match an array of adjacent tokens.
 * THis array can just be a single one too.
 *
 * We maintain a spam.txt resource file to spot keywords that trigger a positive spam detection.
 */
public class SpamDetector {
  private final String delimiter = "\\s*\\b\\s*";
  private final TokenTree spam = new TokenTree();

  public SpamDetector() {
    Pattern delim = Pattern.compile(delimiter);
    Resources.lines("feedback/spam.txt")
      .filter(line -> !StringUtils.isBlank(line))
      .map(x -> Arrays.stream(delim.split(x.trim().toLowerCase()))
          .filter(y -> !StringUtils.isBlank(y))
          .toArray(String[]::new)
      )
      .forEach(spam::add);
  }

  public TokenTree spamTokens() {
    return spam;
  }

  public boolean isSpam(String text) {
    Scanner scanner = new Scanner(text).useDelimiter(delimiter);;
    TokenTree.TNode match = null;
    while(scanner.hasNext()) {
      String word = scanner.next().trim().toLowerCase();
      if (match != null) {
        if (match.contains(word)) {
          match = match.get(word);
          if (match.isTerminal()) {
            return true;
          }
        } else {
          match = null;
        }
      }

      if (spam.contains(word)) {
        match = spam.get(word);
        if (match.isTerminal()) {
          return true;
        }
      }
    }
    return false;
  }
}
