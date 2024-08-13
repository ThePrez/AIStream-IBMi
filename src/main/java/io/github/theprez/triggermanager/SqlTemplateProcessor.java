package io.github.theprez.triggermanager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.theprez.jcmdutils.StringUtils;

/** Utility class consisting of only static methods */
final class SqlTemplateProcessor {
    private static final Pattern REPLACEMENT_PATTERN = Pattern.compile("%%([^%\\n]+)%%");

    private SqlTemplateProcessor() {
        // Utility class consisting of only static methods, no instances
    }

    public static String getProcessed(String _templateName, Properties _values) throws IOException {
        final String fileContents = readFile(_templateName);
        String processed = fileContents;
        final Matcher m = REPLACEMENT_PATTERN.matcher(fileContents);
        while (m.find()) {
            String key = m.group(1).trim();
            String group = m.group();
            String value = _values.getProperty(key);
            if (StringUtils.isNonEmpty(value)) {
                processed = processed.replace(group, value);
            }
        }
        return processed;
    }

    private static String readFile(String _templateName) throws IOException {
        try (InputStream rawData = ClassLoader.getSystemResourceAsStream("sqltemplates/" + _templateName)) {
            if (Objects.isNull(rawData)) {
                throw new IOException("SQL template not found");
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int bytesRead;
            byte[] buf = new byte[12];
            while ((bytesRead = rawData.read(buf, 0, buf.length)) != -1) {
                baos.write(buf, 0, bytesRead);
            }
            baos.flush();
            return new String(baos.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
