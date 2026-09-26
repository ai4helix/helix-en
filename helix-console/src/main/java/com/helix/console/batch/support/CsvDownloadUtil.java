package com.helix.console.batch.support;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class CsvDownloadUtil {

    private CsvDownloadUtil() {
    }

    public static void write(HttpServletResponse response, String fileName, List<String> lines)
            throws IOException {
        response.setContentType("text/csv;charset=UTF-8");
        String encoded = URLEncoder.encode(fileName + ".csv", "UTF-8").replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
        Writer writer = new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8);
        writer.write('\uFEFF');
        for (String line : lines) {
            writer.write(line);
            writer.write('\n');
        }
        writer.flush();
    }
}
