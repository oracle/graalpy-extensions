package com.example;

import java.util.List;
import java.util.Map;

/**
 * Methods demonstrating wildcard and raw type handling at use sites.
 */
public class UseSite {
    public List<?> anyList() { return null; }

    public Map<?, String> mapWild() { return null; }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public List<String> listRaw(List raw) { return null; }
}
