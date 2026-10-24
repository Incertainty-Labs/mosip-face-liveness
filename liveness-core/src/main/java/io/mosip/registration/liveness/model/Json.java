package io.mosip.registration.liveness.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal JSON reader for the model manifest: objects become {@code Map<String, Object>} (in order), arrays
 * {@code List<Object>}, strings {@code String}, numbers {@code Double}, plus {@code Boolean} and {@code null}. The core
 * module may not depend on a JSON library (API.md §3, module rules).
 */
final class Json {

    private final String text;
    private int pos;

    private Json(String text) {
        this.text = text;
    }

    /**
     * @param text a JSON document
     * @return its value
     * @throws IllegalArgumentException if the text is not valid JSON
     */
    static Object parse(String text) {
        Json json = new Json(text);
        Object value = json.value();
        json.skipSpace();
        if (json.pos != text.length()) {
            throw json.error("unexpected text after the value");
        }
        return value;
    }

    private Object value() {
        skipSpace();
        if (pos >= text.length()) {
            throw error("unexpected end");
        }
        char c = text.charAt(pos);
        switch (c) {
            case '{':
                return object();
            case '[':
                return array();
            case '"':
                return string();
            case 't':
                return literal("true", Boolean.TRUE);
            case 'f':
                return literal("false", Boolean.FALSE);
            case 'n':
                return literal("null", null);
            default:
                return number();
        }
    }

    private Map<String, Object> object() {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        pos++;
        skipSpace();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipSpace();
            if (peek() != '"') {
                throw error("expected a key");
            }
            String key = string();
            skipSpace();
            expect(':');
            map.put(key, value());
            skipSpace();
            if (peek() == ',') {
                pos++;
            } else {
                expect('}');
                return map;
            }
        }
    }

    private List<Object> array() {
        List<Object> list = new ArrayList<Object>();
        pos++;
        skipSpace();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            list.add(value());
            skipSpace();
            if (peek() == ',') {
                pos++;
            } else {
                expect(']');
                return list;
            }
        }
    }

    private String string() {
        StringBuilder sb = new StringBuilder();
        pos++;
        while (true) {
            if (pos >= text.length()) {
                throw error("unterminated string");
            }
            char c = text.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if (pos >= text.length()) {
                throw error("unterminated escape");
            }
            char e = text.charAt(pos++);
            switch (e) {
                case 'n':
                    sb.append('\n');
                    break;
                case 't':
                    sb.append('\t');
                    break;
                case 'r':
                    sb.append('\r');
                    break;
                case 'b':
                    sb.append('\b');
                    break;
                case 'f':
                    sb.append('\f');
                    break;
                case 'u':
                    if (pos + 4 > text.length()) {
                        throw error("short unicode escape");
                    }
                    sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                    pos += 4;
                    break;
                default:
                    sb.append(e);
            }
        }
    }

    private Double number() {
        int start = pos;
        while (pos < text.length() && "+-0123456789.eE".indexOf(text.charAt(pos)) >= 0) {
            pos++;
        }
        if (start == pos) {
            throw error("unexpected character '" + text.charAt(pos) + "'");
        }
        try {
            return Double.valueOf(text.substring(start, pos));
        } catch (NumberFormatException e) {
            throw error("bad number");
        }
    }

    private Object literal(String word, Object value) {
        if (!text.startsWith(word, pos)) {
            throw error("unexpected character '" + text.charAt(pos) + "'");
        }
        pos += word.length();
        return value;
    }

    private void skipSpace() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
    }

    private char peek() {
        if (pos >= text.length()) {
            throw error("unexpected end");
        }
        return text.charAt(pos);
    }

    private void expect(char c) {
        if (peek() != c) {
            throw error("expected '" + c + "'");
        }
        pos++;
    }

    private IllegalArgumentException error(String what) {
        return new IllegalArgumentException("Invalid JSON at " + pos + ": " + what);
    }
}
