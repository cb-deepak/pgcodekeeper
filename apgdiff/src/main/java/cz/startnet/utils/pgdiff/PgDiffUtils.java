/**
 * Copyright 2006 StartNet s.r.o.
 *
 * Distributed under MIT license
 */
package cz.startnet.utils.pgdiff;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.eclipse.core.runtime.IProgressMonitor;

import ru.taximaxim.codekeeper.apgdiff.log.Log;
import ru.taximaxim.codekeeper.apgdiff.sql.Keyword;
import ru.taximaxim.codekeeper.apgdiff.sql.Keyword.KeywordCategory;

/**
 * Utilities for creation of diffs.
 *
 * @author fordfrog
 */
public final class PgDiffUtils {

    public static final int ERROR_SUBSTRING_LENGTH = 20;
    private static final Pattern PATTERN_SQ = Pattern.compile("'", Pattern.LITERAL);
    private static final Pattern PATTERN_DQ = Pattern.compile("\"", Pattern.LITERAL);
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    public static boolean isValidId(String id, boolean allowKeywords, boolean allowCaps) {
        if (id.isEmpty()) {
            return true;
        }

        for (int i = 0; i < id.length(); i++) {
            if (!isValidIdChar(id.charAt(i), allowCaps, i != 0)) {
                return false;
            }
        }

        if (!allowKeywords) {
            Keyword keyword = Keyword.KEYWORDS.get(allowCaps ? id.toLowerCase(Locale.ROOT) : id);
            if (keyword != null && keyword.getCategory() != KeywordCategory.UNRESERVED_KEYWORD) {
                return false;
            }
        }

        return true;
    }

    public static boolean isValidIdChar(char c) {
        return isValidIdChar(c, true, true);
    }

    public static boolean isValidIdChar(char c, boolean allowCaps, boolean allowDigits) {
        if (c >= 'a' && c <= 'z') {
            return true;
        }

        if (allowCaps && c >= 'A' && c <= 'Z') {
            return true;
        }

        if (allowDigits && c >= '0' && c <= '9') {
            return true;
        }

        return c == '_';
    }

    /**
     * If name contains only lower case characters and digits and is not
     * keyword, it is returned not quoted, otherwise the string is returned
     * quoted.
     *
     * @param name name
     *
     * @return quoted string if needed, otherwise not quoted string
     */
    public static String getQuotedName(final String name) {
        return isValidId(name, false, false) ? name : quoteName(name);
    }

    public static String quoteName(String name) {
        return new StringBuilder(name.length() + 2)
                .append('"')
                .append(name.indexOf('"') != -1 ? PATTERN_DQ.matcher(name).replaceAll("\"\"") : name)
                .append('"')
                .toString();
    }

    public static String quoteString(String s) {
        return appendQuotedString(new StringBuilder(s.length() + 2), s).toString();
    }

    public static StringBuilder appendQuotedString(StringBuilder sb, String s) {
        return sb.append('\'')
                .append(s.indexOf('\'') != -1 ? PATTERN_SQ.matcher(s).replaceAll("''") : s)
                .append('\'');
    }

    /**
     * Function equivalent to appendStringLiteralDQ in pgdump's dumputils.c
     */
    public static String quoteStringDollar(String contents) {
        final String suffixes = "_XXXXXXX";
        StringBuilder quote = new StringBuilder("$");
        int counter = 0;
        while (contents.contains(quote)) {
            quote.append(suffixes.charAt(counter++));
            counter %= suffixes.length();
        }

        quote.append('$');
        String dollar = quote.toString();
        return dollar + contents + dollar;
    }

    public static String unquoteQuotedName(String name) {
        return name.substring(1, name.length() - 1).replace("\"\"", "\"");
    }

    public static String unquoteQuotedString(String s, int start) {
        return s.substring(start, s.length() - 1).replace("''", "'");
    }

    public static byte[] getHash(String s, String instance) throws NoSuchAlgorithmException {
        return MessageDigest.getInstance(instance)
                .digest(s.getBytes(StandardCharsets.UTF_8));
    }

    public static String hash (String s, String instance) {
        try {
            byte[] hash = getHash(s, instance);
            StringBuilder sb = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                sb.append(HEX_CHARS[(b & 0xff) >> 4]);
                sb.append(HEX_CHARS[(b & 0x0f)]);
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            Log.log(e);
            return instance +"_ERROR_" + new Random().nextInt();
        }
    }

    /**
     * @return lowercase hex MD5 for UTF-8 representation of given string.
     */
    public static String md5(String s) {
        return hash(s, "MD5");
    }

    /**
     * @return lowercase hex SHA-256 for UTF-8 representation of given string.
     */
    public static String sha(String s) {
        return hash(s, "SHA-256");
    }

    public static String checkedEncodeUtf8(String string) {
        try {
            return URLEncoder.encode(string, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return string;
        }
    }

    public static String getErrorSubstr(String s, int pos) {
        return PgDiffUtils.getErrorSubstr(s, pos, ERROR_SUBSTRING_LENGTH);
    }

    public static String getErrorSubstr(String s, int pos, int len) {
        if (pos >= s.length()) {
            return "";
        }
        return pos + len < s.length() ? s.substring(pos, pos + len) : s.substring(pos);
    }

    public static void checkCancelled(IProgressMonitor monitor)
            throws InterruptedException {
        if (monitor != null && monitor.isCanceled()) {
            throw new InterruptedException();
        }
    }

    /**
     * Compares 2 collections for equality unorderedly as if they were {@link Set}s.<br>
     * Does not eliminate duplicate elements as sets do and counts them instead.
     * Thus it achieves complementarity with {@link #setlikeHashcode}
     * while not requiring it to eliminate duplicates, nor does it require
     * a <code>List.containsAll()</code> O(N^2) call here.
     * In general, duplicate elimination is an undesired side-effect of comparison using {@link Set}s,
     * so this solution is overall better and only *slightly* slower.<br><br>
     *
     * Performance: best case O(1), worst case O(N) + new {@link HashMap} (in case N1 == N2),
     * assuming size() takes constant time.
     */
    public static boolean setlikeEquals(Collection<?> c1, Collection<?> c2) {
        final int s1 = c1.size();
        if (s1 != c2.size()) {
            return false;
        }
        if (0 == s1) {
            // both are empty
            return true;
        }
        // mimic HashSet(Collection) constructor
        final float loadFactor = 0.75f;
        final Map<Object, Integer> map =
                new HashMap<>(Math.max((int) (s1/loadFactor) + 1, 16), loadFactor);
        for (Object el1 : c1) {
            Integer i = map.get(el1);
            map.put(el1, i == null ? 1 : (i + 1));
        }
        for (Object el2 : c2) {
            Integer i = map.get(el2);
            if (i == null) {
                // c1.count(el2) < c2.count(el2)
                return false;
            }
            if (i == 1) {
                // the last or the only instance of el2 in c1
                map.remove(el2);
            } else {
                // counted one duplicate
                map.put(el2, i - 1);
            }
        }
        // if the map is not empty at the end it means that
        // not all duplicates in c1 were matched by those in c2
        return map.isEmpty();
    }

    public static boolean isStringNotEmpty(String input) {
        return input != null && !input.isEmpty();
    }

    public static boolean startsWithId(String text, String id, int offset) {
        if (offset != 0 && isValidIdChar(text.charAt(offset - 1))) {
            return false;
        }
        int rightChar = offset + id.length();
        if (rightChar < text.length() && isValidIdChar(text.charAt(rightChar))) {
            return false;
        }

        return text.startsWith(id, offset);
    }

    public static boolean endsWithIgnoreCase(String str, String suffix) {
        int suffixLength = suffix.length();
        return str.regionMatches(true, str.length() - suffixLength, suffix, 0, suffixLength);
    }

    public static <T> Iterable<T> sIter(Stream<T> stream) {
        return stream::iterator;
    }

    private PgDiffUtils() {
    }
}
