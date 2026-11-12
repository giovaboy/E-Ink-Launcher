package cn.modificator.launcher.reading;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses the Lua tables KOReader writes to disk (settings.reader.lua, history.lua,
 * metadata.*.lua): a {@code return { ... }} with string/number keys and string, number,
 * boolean or nested table values. It is not a general Lua parser.
 *
 * Tables become {@code Map<String, Object>} (numeric keys as their decimal string), strings
 * {@code String}, numbers {@code Double}, booleans {@code Boolean}, nil {@code null}.
 */
public final class LuaTableParser {
  private final String src;
  private int pos;

  private LuaTableParser(String src) {
    this.src = src;
  }

  /** Returns the table after {@code return}, or throws IllegalArgumentException. */
  public static Map<String, Object> parse(String src) {
    LuaTableParser parser = new LuaTableParser(src);
    parser.skipSpaceAndComments();
    if (src.startsWith("return", parser.pos)) {
      parser.pos += "return".length();
    }
    Object value = parser.readValue();
    if (!(value instanceof Map)) {
      throw new IllegalArgumentException("Not a table");
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> table = (Map<String, Object>) value;
    return table;
  }

  private Object readValue() {
    skipSpaceAndComments();
    if (pos >= src.length()) {
      throw error("Unexpected end");
    }
    char c = src.charAt(pos);
    if (c == '{') {
      return readTable();
    } else if (c == '"' || c == '\'') {
      return readString();
    } else if (c == '-' || c == '.' || Character.isDigit(c)) {
      return readNumber();
    } else if (src.startsWith("true", pos)) {
      pos += 4;
      return Boolean.TRUE;
    } else if (src.startsWith("false", pos)) {
      pos += 5;
      return Boolean.FALSE;
    } else if (src.startsWith("nil", pos)) {
      pos += 3;
      return null;
    }
    throw error("Unexpected character '" + c + "'");
  }

  private Map<String, Object> readTable() {
    Map<String, Object> table = new LinkedHashMap<>();
    pos++; // {
    int arrayIndex = 1;
    while (true) {
      skipSpaceAndComments();
      if (pos >= src.length()) {
        throw error("Unterminated table");
      }
      char c = src.charAt(pos);
      if (c == '}') {
        pos++;
        return table;
      }
      String key;
      if (c == '[') {
        pos++;
        Object keyValue = readValue();
        skipSpaceAndComments();
        expect(']');
        skipSpaceAndComments();
        expect('=');
        key = keyToString(keyValue);
      } else if (Character.isLetter(c) || c == '_') {
        int start = pos;
        while (pos < src.length() && (Character.isLetterOrDigit(src.charAt(pos)) || src.charAt(pos) == '_')) {
          pos++;
        }
        String word = src.substring(start, pos);
        skipSpaceAndComments();
        if (pos < src.length() && src.charAt(pos) == '=') {
          pos++;
          key = word;
        } else {
          // A bare true/false/nil list element.
          pos = start;
          key = String.valueOf(arrayIndex++);
          table.put(key, readValue());
          skipSeparator();
          continue;
        }
      } else {
        key = String.valueOf(arrayIndex++);
        table.put(key, readValue());
        skipSeparator();
        continue;
      }
      table.put(key, readValue());
      skipSeparator();
    }
  }

  private static String keyToString(Object key) {
    if (key instanceof Double) {
      double d = (Double) key;
      if (d == Math.rint(d)) {
        return String.valueOf((long) d);
      }
    }
    return String.valueOf(key);
  }

  private String readString() {
    char quote = src.charAt(pos++);
    StringBuilder sb = new StringBuilder();
    while (pos < src.length()) {
      char c = src.charAt(pos++);
      if (c == quote) {
        return sb.toString();
      }
      if (c != '\\') {
        sb.append(c);
        continue;
      }
      if (pos >= src.length()) {
        break;
      }
      char e = src.charAt(pos++);
      switch (e) {
        case 'n': sb.append('\n'); break;
        case 't': sb.append('\t'); break;
        case 'r': sb.append('\r'); break;
        case 'a': sb.append('\u0007'); break;
        case 'b': sb.append('\b'); break;
        case 'f': sb.append('\f'); break;
        case 'v': sb.append('\u000B'); break;
        case '\n': sb.append('\n'); break; // %q writes newlines as backslash + newline
        case '\r':
          sb.append('\n');
          if (pos < src.length() && src.charAt(pos) == '\n') pos++;
          break;
        default:
          if (Character.isDigit(e)) {
            // \ddd: decimal byte value, up to 3 digits
            int value = e - '0';
            for (int i = 0; i < 2 && pos < src.length() && Character.isDigit(src.charAt(pos)); i++) {
              value = value * 10 + (src.charAt(pos++) - '0');
            }
            sb.append((char) value);
          } else {
            sb.append(e); // \\ \" \'
          }
      }
    }
    throw error("Unterminated string");
  }

  private Double readNumber() {
    int start = pos;
    if (src.charAt(pos) == '-') pos++;
    while (pos < src.length()) {
      char c = src.charAt(pos);
      if (Character.isDigit(c) || c == '.' || c == 'e' || c == 'E'
              || ((c == '+' || c == '-') && (src.charAt(pos - 1) == 'e' || src.charAt(pos - 1) == 'E'))) {
        pos++;
      } else {
        break;
      }
    }
    try {
      return Double.parseDouble(src.substring(start, pos));
    } catch (NumberFormatException e) {
      throw error("Bad number");
    }
  }

  private void skipSeparator() {
    skipSpaceAndComments();
    if (pos < src.length() && (src.charAt(pos) == ',' || src.charAt(pos) == ';')) {
      pos++;
    }
  }

  private void skipSpaceAndComments() {
    while (pos < src.length()) {
      char c = src.charAt(pos);
      if (Character.isWhitespace(c)) {
        pos++;
      } else if (c == '-' && src.startsWith("--", pos)) {
        while (pos < src.length() && src.charAt(pos) != '\n') pos++;
      } else {
        return;
      }
    }
  }

  private void expect(char c) {
    if (pos >= src.length() || src.charAt(pos) != c) {
      throw error("Expected '" + c + "'");
    }
    pos++;
  }

  private IllegalArgumentException error(String message) {
    return new IllegalArgumentException(message + " at offset " + pos);
  }
}
