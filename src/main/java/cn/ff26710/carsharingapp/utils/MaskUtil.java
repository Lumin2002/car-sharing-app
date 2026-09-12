package cn.ff26710.carsharingapp.utils;

public final class MaskUtil {

    private MaskUtil() {
    }
    public static String mask(String value, int prefix, int suffix) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String v = value.trim();
        int len = v.length();
        if (len <= prefix + suffix) {
            return "*".repeat(len);
        }
        return v.substring(0, prefix) + "*".repeat(len - prefix - suffix) + v.substring(len - suffix);
    }
}
