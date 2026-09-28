package com.example.dynamicform.schema;

import com.example.dynamicform.common.ApiException;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class IdentifierPolicy {
    private static final Pattern VALID = Pattern.compile("^[a-z][a-z0-9_]{0,62}$");
    private static final Set<String> RESERVED = Set.of(
            "all", "analyse", "analyze", "and", "any", "array", "as", "asc", "authorization",
            "between", "binary", "both", "case", "cast", "check", "collate", "column", "constraint",
            "create", "current_date", "current_time", "current_timestamp", "default", "deferrable", "desc",
            "distinct", "do", "else", "end", "except", "false", "for", "foreign", "from", "grant",
            "group", "having", "in", "initially", "intersect", "into", "is", "leading", "limit", "localtime",
            "localtimestamp", "new", "not", "null", "off", "offset", "old", "on", "only", "or", "order",
            "placing", "primary", "references", "returning", "select", "session_user", "some", "symmetric",
            "table", "then", "to", "trailing", "true", "union", "unique", "user", "using", "variadic",
            "when", "where", "window", "with"
    );

    public String requireValid(String value, String label) {
        if (value == null || !VALID.matcher(value).matches() || RESERVED.contains(value)) {
            throw ApiException.badRequest(label + " không hợp lệ: " + value);
        }
        return value;
    }

    public String normalize(String logicalName) {
        String ascii = Normalizer.normalize(logicalName, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd').replace('Đ', 'D');
        String snake = ascii.replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (snake.isBlank() || !Character.isLetter(snake.charAt(0))) snake = "f_" + snake;
        if (snake.length() > 63) snake = snake.substring(0, 63).replaceAll("_+$", "");
        requireValid(snake, "Identifier");
        return snake;
    }
}
