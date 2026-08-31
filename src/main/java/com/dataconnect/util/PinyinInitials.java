package com.dataconnect.util;

import java.nio.charset.Charset;

/**
 * 中文转拼音首字母。汉字按 GBK 区间取首字母，字母数字原样保留并转大写。
 * 多音字按常用读音近似，不做精确注音。
 */
public final class PinyinInitials {

    private static final Charset GBK = charsetOrNull("GBK");

    private PinyinInitials() {}

    public static String from(Object value) {
        if (value == null) {
            return "";
        }
        return from(value.toString());
    }

    public static String from(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                sb.append(c);
            } else if (c >= 'a' && c <= 'z') {
                sb.append((char) (c - 32));
            } else if (c >= '0' && c <= '9') {
                sb.append(c);
            } else if (c >= 0x4E00 && c <= 0x9FFF) {
                char initial = chineseInitial(c);
                if (initial != 0) {
                    sb.append(initial);
                }
            }
        }
        return sb.toString();
    }

    private static char chineseInitial(char ch) {
        if (GBK == null) {
            return 0;
        }
        byte[] bytes = String.valueOf(ch).getBytes(GBK);
        if (bytes.length < 2) {
            return 0;
        }
        int code = ((bytes[0] & 0xFF) << 8) | (bytes[1] & 0xFF);
        if (code >= 0xB0A1 && code <= 0xB0C4) return 'A';
        if (code >= 0xB0C5 && code <= 0xB2C0) return 'B';
        if (code >= 0xB2C1 && code <= 0xB4ED) return 'C';
        if (code >= 0xB4EE && code <= 0xB6E9) return 'D';
        if (code >= 0xB6EA && code <= 0xB7A1) return 'E';
        if (code >= 0xB7A2 && code <= 0xB8C0) return 'F';
        if (code >= 0xB8C1 && code <= 0xB9FD) return 'G';
        if (code >= 0xB9FE && code <= 0xBBF6) return 'H';
        if (code >= 0xBBF7 && code <= 0xBFA5) return 'J';
        if (code >= 0xBFA6 && code <= 0xC0AB) return 'K';
        if (code >= 0xC0AC && code <= 0xC2E7) return 'L';
        if (code >= 0xC2E8 && code <= 0xC4C2) return 'M';
        if (code >= 0xC4C3 && code <= 0xC5B5) return 'N';
        if (code >= 0xC5B6 && code <= 0xC5BD) return 'O';
        if (code >= 0xC5BE && code <= 0xC6D9) return 'P';
        if (code >= 0xC6DA && code <= 0xC8BA) return 'Q';
        if (code >= 0xC8BB && code <= 0xC8F5) return 'R';
        if (code >= 0xC8F6 && code <= 0xCBF9) return 'S';
        if (code >= 0xCBFA && code <= 0xCDD9) return 'T';
        if (code >= 0xCDDA && code <= 0xCEF3) return 'W';
        if (code >= 0xCEF4 && code <= 0xD188) return 'X';
        if (code >= 0xD1B9 && code <= 0xD4D0) return 'Y';
        if (code >= 0xD4D1 && code <= 0xD7F9) return 'Z';
        return 0;
    }

    private static Charset charsetOrNull(String name) {
        try {
            return Charset.forName(name);
        } catch (Exception e) {
            return null;
        }
    }
}
