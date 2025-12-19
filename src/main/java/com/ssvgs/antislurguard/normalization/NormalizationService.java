package com.biggiko.antislurguard.normalization;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;

import com.biggiko.antislurguard.config.Config;
import com.ibm.icu.text.Transliterator;

public final class NormalizationService {

    private static final Map<Character, Character> LEET_MAP = Map.ofEntries(
            Map.entry('0', 'o'),
            Map.entry('1', 'i'),
            Map.entry('3', 'e'),
            Map.entry('4', 'a'),
            Map.entry('5', 's'),
            Map.entry('7', 't'),
            Map.entry('@', 'a'),
            Map.entry('$', 's'),
            Map.entry('!', 'i'),
            Map.entry('|', 'i')
    );

    private final Config.NormalizeSettings settings;
    private final Transliterator transliterator;

    public NormalizationService(Config.NormalizeSettings settings) {
        this.settings = settings;
        this.transliterator = settings.transliterateCyrillic()
                ? Transliterator.getInstance("Russian-Latin/BGN")
                : null;
    }

    public String normalize(String input) {
        if (input == null) {
            return "";
        }
        if (!settings.enabled()) {
            return input;
        }
        String result = input;
        if (settings.caseFold()) {
            result = result.toLowerCase(Locale.ROOT);
        }
        if (settings.nfdStripDiacritics()) {
            result = Normalizer.normalize(result, Normalizer.Form.NFD)
                    .replaceAll("\\p{M}+", "");
        }
        if (settings.leetMappings()) {
            result = applyCharacterMap(result, LEET_MAP);
        }
        if (transliterator != null) {
            result = transliterator.transliterate(result);
        }
        if (settings.stripNonAlnum()) {
            result = stripNonAlphaNumeric(result);
        }
        if (settings.collapseRepeats()) {
            result = collapseRepeats(result);
        }
        return result;
    }

    private String applyCharacterMap(String input, Map<Character, Character> map) {
        StringBuilder builder = new StringBuilder(input.length());
        for (char c : input.toCharArray()) {
            char lower = Character.toLowerCase(c);
            Character mapped = map.get(lower);
            if (mapped != null) {
                builder.append(mapped);
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    private String stripNonAlphaNumeric(String input) {
        StringBuilder builder = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    private String collapseRepeats(String input) {
        if (input.isEmpty()) {
            return input;
        }
        StringBuilder builder = new StringBuilder(input.length());
        char last = input.charAt(0);
        builder.append(last);
        for (int i = 1; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c != last) {
                builder.append(c);
                last = c;
            }
        }
        return builder.toString();
    }
}
