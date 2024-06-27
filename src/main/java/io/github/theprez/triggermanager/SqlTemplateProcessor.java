package io.github.theprez.triggermanager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tools.ant.taskdefs.Classloader;

import com.github.theprez.jcmdutils.StringUtils;

public class SqlTemplateProcessor {
    private static final Pattern s_replacementPattern = Pattern.compile("%%([^%\\n]+)%%");

    public SqlTemplateProcessor() {

    }

    public String getProcessed(String _templateName, Properties _values) throws IOException {
        final String fileContents = readFile(_templateName);

        Matcher m = s_replacementPattern.matcher(fileContents);
        String processed = fileContents;
        while (m.find()) {
            String key = m.group(1).trim();
            String group = m.group();
            String value = _values.getProperty(key);
            if(StringUtils.isNonEmpty(value)){
                processed = processed.replace(group, value);
            }
        }
        return processed;
    }

    private String readFile(String _templateName) throws IOException {

        try (InputStream rawData = ClassLoader.getSystemResourceAsStream("sqltemplates/" + _templateName)) {
            if(null == rawData) {
                throw new IOException("SQL template not found");
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int bytesRead;
            byte[] buf = new byte[12];
            while ((bytesRead = rawData.read(buf, 0, buf.length)) != -1) {
                baos.write(buf, 0, bytesRead);
            }
            baos.flush();
            String ret = new String(baos.toByteArray(), "UTF-8");

            return ret;
        }
    }
}
