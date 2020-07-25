package io.vertx.ext.web.handler.impl;

class StringUtils {

  /**
     * Check if a string is null or empty (including containing only spaces)
     *
     * @param s Source string
     * @return TRUE if source string is null or empty (including containing only spaces)
     */
    public static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
