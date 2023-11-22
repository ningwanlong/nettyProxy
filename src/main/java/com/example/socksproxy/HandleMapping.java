package com.example.socksproxy;

import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.*;
import java.net.URL;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author ysn
 * @date 2023/11/21
 */
public class HandleMapping {
    public static final Map<String, String> mappingMap = new HashMap<>();
    private static boolean hasInit = false;
    public static void init() throws IOException{
        if (hasInit){
            return;
        }
        hasInit = true;
        ResourceLoader resourceLoader = new DefaultResourceLoader();
        Resource resource = resourceLoader.getResource("classpath:ip-port-mapping.txt");
        try {
            final InputStream inputStream = resource.getInputStream();
            final List<String> mappingList = readAllLines(inputStream);
            System.out.println("mappingList: " + mappingList);
            for (String s : mappingList) {
                s = s.trim();
                if (s.startsWith("#") || s.isEmpty()){
                    continue;
                }
                final String[] s1 = s.split(" ");
                if (s1.length == 2) {
                    mappingMap.put(s1[0], s1[1]);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<String> readAllLines(InputStream inputStream) throws IOException{
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
        Reader reader = new InputStreamReader(inputStream, decoder);
        try (BufferedReader bufferedReader = new BufferedReader(reader)) {
            List<String> result = new ArrayList<>();
            for (;;) {
                String line = bufferedReader.readLine();
                if (line == null)
                    break;
                result.add(line);
            }
            return result;
        }
    }

}
