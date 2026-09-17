package com.transit.simulation2.util;

/**
 * Minimal RFC 4180 CSV writing used by the export endpoints. Numbers are rendered with
 * {@link Double#toString} rather than a formatter, so the output carries full precision and does
 * not depend on the default locale's decimal separator; a null value becomes an empty field.
 */
public final class Csv {

    private Csv() {
    }

    /** Quotes a field only when it contains a comma, a quote or a line break. */
    public static String escape(String field) {
        if (field == null) return "";
        if (field.indexOf(',') >= 0 || field.indexOf('"') >= 0 || field.indexOf('\n') >= 0 || field.indexOf('\r') >= 0) {
            return '"' + field.replace("\"", "\"\"") + '"';
        }
        return field;
    }

    /** Appends one record, terminated by a line feed. Null fields are written as empty. */
    public static void row(StringBuilder out, Object... fields) {
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) out.append(',');
            Object f = fields[i];
            if (f == null) continue;
            if (f instanceof Number || f instanceof Boolean) {
                out.append(f);
            } else {
                out.append(escape(f.toString()));
            }
        }
        out.append('\n');
    }
}
