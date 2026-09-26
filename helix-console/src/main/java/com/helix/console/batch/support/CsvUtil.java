package com.helix.console.batch.support;

import java.util.ArrayList;
import java.util.List;

public final class CsvUtil {

    private static final char BOM = '\uFEFF';

    private CsvUtil() {
    }

    public static List<String[]> parse(String content) {
        List<String[]> rows = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return rows;
        }
        if (content.charAt(0) == BOM) {
            content = content.substring(1);
        }
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;
        boolean rowStarted = false;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < content.length() && content.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cell.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
                rowStarted = true;
            } else if (c == ',') {
                row.add(cell.toString());
                cell.setLength(0);
                rowStarted = true;
            } else if (c == '\r') {
                if (i + 1 < content.length() && content.charAt(i + 1) == '\n') {
                    i++;
                }
                endRow(rows, row, cell, rowStarted);
                row = new ArrayList<>();
                rowStarted = false;
            } else if (c == '\n') {
                endRow(rows, row, cell, rowStarted);
                row = new ArrayList<>();
                rowStarted = false;
            } else {
                cell.append(c);
                rowStarted = true;
            }
        }
        endRow(rows, row, cell, rowStarted);
        return rows;
    }

    private static void endRow(List<String[]> rows, List<String> row, StringBuilder cell, boolean rowStarted) {
        if (rowStarted || cell.length() > 0) {
            row.add(cell.toString());
        }
        if (!row.isEmpty()) {
            rows.add(row.toArray(new String[0]));
        }
        cell.setLength(0);
    }

    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean needQuote = value.indexOf(',') >= 0 || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
        if (!needQuote) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    public static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
