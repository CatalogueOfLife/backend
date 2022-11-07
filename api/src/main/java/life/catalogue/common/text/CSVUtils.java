package life.catalogue.common.text;

import life.catalogue.common.io.UTF8IoUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

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
   * As always make sure to close the stream!
   */
  public static Stream<List<String>> parse(InputStream in) {
    return parse(in, 0);
  }

  /**
   * Reads an UTF8 CSV stream separated by commas and generates a stream or rows, each being a list of columns.
   * As always make sure to close the stream!
   * @param skip number of lines to skip at the beginning
   */
  public static Stream<List<String>> parse(InputStream in, long skip) {
    BufferedReader br = UTF8IoUtils.readerFromStream(in);
    return br.lines().skip(skip).map(CSVUtils::parseLine);
  }

  /**
   * Reads an UTF8 column delimited stream separated by a given delimiter and generates a stream or rows, each being a list of columns.
   * As always make sure to close the stream!
   * @param skip number of lines to skip at the beginning
   * @param delimiter separating columns
   */
  public static Stream<List<String>> parse(InputStream in, long skip, char delimiter) {
    BufferedReader br = UTF8IoUtils.readerFromStream(in);
    return parse(br, skip, delimiter);
  }

  public static Stream<List<String>> parse(BufferedReader br, long skip, char delimiter) {
    return br.lines().skip(skip).map(line -> CSVUtils.parseLine(line, delimiter));
  }
  
  private static void add(List<String> row, StringBuffer col){
    row.add(col.length()==0 ? null : col.toString());
  }
}
