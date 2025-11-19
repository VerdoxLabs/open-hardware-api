package de.verdox.hwapi.util;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QueryUtil {

    public static <ENTITY> Optional<ENTITY> search(
            String queryName,
            Set<ENTITY> candidates,
            Function<ENTITY, String> extractString
    ) {
        String normalizedQuery = normalizeModelName(queryName);
        List<String> queryTokens = tokenize(normalizedQuery);
        List<String> queryNumberBlocks = extractNumberBlocks(normalizedQuery);

        return candidates.stream()
                .max(Comparator.comparingDouble(b -> {
                    String model = normalize(extractString.apply(b));
                    List<String> modelTokens = tokenize(model);
                    List<String> modelNumberBlocks = extractNumberBlocks(model);
                    return scoreCandidate(
                            model,
                            modelTokens,
                            modelNumberBlocks,
                            normalizedQuery,
                            queryTokens,
                            queryNumberBlocks
                    );
                }));
    }

    /**
     * Entfernt Klein-/Großschreibung, doppelte Spaces etc.
     */
    private static String normalizeModelName(String name) {
        if (name == null) return "";
        return name
                .toLowerCase()
                .replaceAll("@", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static List<String> extractNumberBlocks(String s) {
        if (s == null) return List.of();
        List<String> result = new ArrayList<>();
        Matcher m = Pattern.compile("\\d{3,}").matcher(s);
        while (m.find()) {
            result.add(m.group());
        }
        return result;
    }


    private static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase().trim();
    }

    /**
     * Ganz einfache Tokenisierung: nach Leerzeichen und ein paar Trennern splitten,
     * leere Tokens rausfiltern.
     */
    private static List<String> tokenize(String s) {
        if (s == null || s.isBlank()) return List.of();
        return Arrays.stream(s.split("[\\s/()@]+"))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(t -> !t.isBlank())
                .toList();
    }

    // ---------------- Scoring-Logik komplett in Java ----------------

    private static double scoreCandidate(
            String modelString,
            List<String> modelTokens,
            List<String> modelNumberBlocks,
            String queryString,
            List<String> queryTokens,
            List<String> queryNumberBlocks
    ) {
        if (modelTokens.isEmpty() || queryTokens.isEmpty()) {
            return 0.0;
        }

        double score = 0.0;

        // 1) Token-Overlap (z.B. i9, 9900k)
        long tokenOverlap = queryTokens.stream()
                .filter(modelTokens::contains)
                .count();
        score += tokenOverlap * 80.0;

        // 2) Alle Query-Tokens drin? (z.B. ["i9", "9900k"])
        if (modelTokens.containsAll(queryTokens)) {
            score += 200.0;
        }

        // 3) Modellnummern (Zahlenblöcke) Matchen, z.B. 9900
        long numberOverlap = queryNumberBlocks.stream()
                .filter(modelNumberBlocks::contains)
                .count();
        score += numberOverlap * 150.0;

        // 4) Reihenfolge / Position: je weiter vorne, desto besser
        int indexSum = 0;
        for (String q : queryTokens) {
            int idx = modelTokens.indexOf(q);
            indexSum += (idx >= 0 ? idx : 5); // Strafwert, wenn Token fehlt
        }
        score -= indexSum * 5.0;

        // 5) Leichte String-Similarity als Feintuning
        double sim = similarity(queryString, modelString); // 0..1
        score += sim * 20.0;

        // 6) Länge: sehr lange Namen leicht bestrafen, aber nicht zu stark
        int lenDiff = modelString.length() - queryString.length();
        if (lenDiff > 10) {
            score -= (lenDiff - 10) * 0.5;
        }

        return score;
    }

    public static double similarity(String a, String b) {
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        int maxLen = Math.max(a.length(), b.length());
        if (maxLen == 0) return 1.0;
        int dist = levenshtein(a, b);
        return 1.0 - (double) dist / maxLen;
    }

    public static  int levenshtein(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();

        int[][] dp = new int[len1 + 1][len2 + 1];

        for (int i = 0; i <= len1; i++) dp[i][0] = i;
        for (int j = 0; j <= len2; j++) dp[0][j] = j;

        for (int i = 1; i <= len1; i++) {
            char c1 = s1.charAt(i - 1);
            for (int j = 1; j <= len2; j++) {
                char c2 = s2.charAt(j - 1);
                int cost = (c1 == c2) ? 0 : 1;

                dp[i][j] = Math.min(
                        Math.min(
                                dp[i - 1][j] + 1,      // Löschung
                                dp[i][j - 1] + 1       // Einfügung
                        ),
                        dp[i - 1][j - 1] + cost      // Ersetzen
                );
            }
        }
        return dp[len1][len2];
    }
}
