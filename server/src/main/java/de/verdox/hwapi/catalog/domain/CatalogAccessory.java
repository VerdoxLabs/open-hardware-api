package de.verdox.hwapi.catalog.domain;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.FetchType;
import jakarta.persistence.MappedSuperclass;
import java.util.LinkedHashMap;
import java.util.Map;

/** Common lossless spec storage for catalog accessories. */
@MappedSuperclass
public abstract class CatalogAccessory<SELF extends CatalogAccessory<SELF>> extends HardwareSpec<SELF> {
    @ElementCollection(fetch = FetchType.EAGER)
    protected Map<String, String> specifications = new LinkedHashMap<>();
    public Map<String, String> getSpecifications() { return specifications; }
    public void setSpecifications(Map<String, ? extends java.util.List<String>> values) {
        specifications.clear();
        if (values != null) values.forEach((key, list) -> specifications.put(key, list == null || list.isEmpty() ? "" : list.getFirst()));
    }
    @Override public void merge(SELF other) {
        super.merge(other);
        if (specifications.isEmpty()) specifications.putAll(other.specifications);
    }
}
