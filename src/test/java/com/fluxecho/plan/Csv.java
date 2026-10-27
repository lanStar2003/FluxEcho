package com.fluxecho.plan;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the plan's CSV files ({@code docs/dev/ids.csv}, {@code plan/batches.csv}): comma separated, fields with a
 * comma, a quote or a line break in double quotes, a quote inside doubled; one record per line, the first is the
 * header.
 */
public final class Csv {

    private Csv() {}

    public static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false, any = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c != '"') field.append(c);
                else if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else quoted = false;
                continue;
            }
            any = true;
            if (c == '"') quoted = true;
            else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (c == '\n') {
                row.add(field.toString());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
                any = false;
            } else if (c != '\r') field.append(c);
        }
        if (quoted) throw new IllegalArgumentException("a quoted field is not closed");
        if (any) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }
}
