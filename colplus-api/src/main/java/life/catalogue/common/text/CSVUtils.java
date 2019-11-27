package life.catalogue.common.text;

import java.io.BufferedReader;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import life.catalogue.common.io.Utf8IOUtils;

public class CSVUtils {
  
  private static final char DEFAULT_SEPARATOR = ',';
  private static final char DEFAULT_QUOTE = '"';
  
  public static List<String> parseLine(String line) {
    return parseLine(line, DEFAULT_SEPARATOR, DEFAULT_QUOTE);
  }
  
  public static List<String> parseLine(String line, char delimiter) {
    return parseLine(line, delimiter, DEFAULT_QUOTE);
  }
  
  public static List<String> parseLine(String line, char delimiter, char quote) {
    
    List<String> result = new ArrayList<>();
    
    //if empty, return!
    if (line == null || line.isEmpty()) {
      return result;
    }
    
    StringBuffer curVal = new StringBuffer();
    boolean inQuotes = false;
    boolean startCollectChar = false;
    
    char[] chars = line.toCharArray();
    
    for (char ch : chars) {
      
      if (inQuotes) {
        startCollectChar = true;
        if (ch == quote) {
          inQuotes = false;
        } else {
          curVal.append(ch);
        }
      } else {
        if (ch == quote) {
          
          inQuotes = true;
          
          //double quotes in column will hit this!
          if (startCollectChar) {
            curVal.append(quote);
          }
          
        } else if (ch == delimiter) {
  
          add(result, curVal);
          
          curVal = new StringBuffer();
          startCollectChar = false;
          
        } else if (ch == '\r') {
          //ignore LF characters
          continue;
        } else if (ch == '\n') {
          //the end, break!
          break;
        } else {
          curVal.append(ch);
        }
      }
      
    }
    
    add(result, curVal);
    
    return result;
  }
  
  /**
   * Reads an UTF8 CSV stream separated by commas and generates a stream or rows, each being a list of columns.
   */
  public static Stream<List<String>> parse(InputStream in) {
    BufferedReader br = Utf8IOUtils.readerFromStream(in);
    return br.lines().map(CSVUtils::parseLine);
  }
  
  private static void add(List<String> row, StringBuffer col){
    row.add(col.length()==0 ? null : col.toString());
  }
}
