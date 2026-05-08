package com.examsolver.util;

import com.examsolver.dto.request.SolveRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility để normalize text câu hỏi và tính hash để lookup ngân hàng.
 *
 * Normalize: lowercase → strip dấu câu → collapse whitespace → trim
 * Hash: MD5 của (normalized question text + sorted options text)
 *
 * Kết quả: cùng câu hỏi dù gõ khác nhau hoặc ở môn khác → cùng hash.
 */
public class QuestionHashUtil {

    private QuestionHashUtil() {}

    public static String normalize(String text) {
        if (text == null) return "";
        return text.toLowerCase()
                .replaceAll("[^\\p{L}\\p{N}\\s]", " ")  // giữ chữ, số, space
                .replaceAll("\\s+", " ")
                .trim();
    }

    public static String computeHash(String questionText, List<SolveRequest.OptionData> options) {
        String normalizedQ = normalize(questionText);
        String normalizedOpts = "";
        if (options != null && !options.isEmpty()) {
            normalizedOpts = options.stream()
                    .sorted(Comparator.comparing(o -> o.getLabel().toUpperCase()))
                    .map(o -> o.getLabel().toUpperCase() + ":" + normalize(o.getText()))
                    .collect(Collectors.joining("|"));
        }
        return md5(normalizedQ + "##" + normalizedOpts);
    }

    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not available", e);
        }
    }
}