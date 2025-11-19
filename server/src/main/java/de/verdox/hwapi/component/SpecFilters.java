package de.verdox.hwapi.component;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SpecFilters {
    public <T> Specification<T> build(Class<T> entityClass, Map<String, String> params, List<String> qFields) {
        String q = trim(params.get("q"));
        var equalsParams = params.entrySet().stream()
                .filter(e -> !Set.of("page","size","sort","q","fields").contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();

            // equals-Filter
            for (var e : equalsParams.entrySet()) {
                if (!hasAttribute(root, e.getKey())) continue;
                var path = root.get(e.getKey());
                preds.add(cb.equal(path, castValue(cb, path, e.getValue())));
            }

            // q-Suche (contains, OR über qFields)
            if (q != null && !q.isBlank() && qFields != null && !qFields.isEmpty()) {
                List<Predicate> likePreds = new ArrayList<>();
                for (String f : qFields) {
                    if (!hasAttribute(root, f)) continue;
                    likePreds.add(cb.like(cb.lower(root.get(f)), "%" + q.toLowerCase() + "%"));
                }
                if (!likePreds.isEmpty()) {
                    preds.add(cb.or(likePreds.toArray(Predicate[]::new)));
                }
            }

            return preds.isEmpty() ? cb.conjunction() : cb.and(preds.toArray(Predicate[]::new));
        };
    }

    private boolean hasAttribute(From<?,?> root, String name) {
        try { root.get(name); return true; } catch (IllegalArgumentException ex) { return false; }
    }
    private Object castValue(CriteriaBuilder cb, Path<?> path, String raw) {
        Class<?> t = path.getJavaType();
        if (t == Integer.class) return Integer.valueOf(raw);
        if (t == Long.class) return Long.valueOf(raw);
        if (t == Boolean.class) return Boolean.valueOf(raw);
        if (t == Instant.class) return Instant.parse(raw);
        return raw; // String, etc.
    }
    private static String trim(String s){ return s==null?null:s.trim(); }
}
