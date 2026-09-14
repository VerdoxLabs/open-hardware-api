package de.verdox.hwapi.catalog.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Table;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Generic catalog record for PCPartPicker categories that have no dedicated domain entity yet. */
@Entity
@Table(name = "pcpartpicker_product")
@DiscriminatorValue("PCPARTPICKER_PRODUCT")
public class PCPartPickerProduct extends HardwareSpec<PCPartPickerProduct> {
    @Column(nullable = false)
    private String category = "other";

    @ElementCollection
    @CollectionTable(name = "pcpartpicker_product_spec", joinColumns = @JoinColumn(name = "spec_id"))
    @MapKeyColumn(name = "spec_key", length = 255)
    @Column(name = "spec_value", length = 4000, nullable = false)
    private Map<String, String> specifications = new LinkedHashMap<>();

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category == null || category.isBlank() ? "other" : category; }
    public Map<String, String> getSpecifications() { return specifications; }
    public void setSpecifications(Map<String, List<String>> specs) {
        specifications.clear();
        specs.forEach((key, values) -> {
            if (!"imageUrl".equals(key) && values != null && !values.isEmpty()) {
                specifications.put(key, String.join(" | ", values));
            }
        });
    }
    @Override public void merge(PCPartPickerProduct other) { super.merge(other); if (specifications.isEmpty()) specifications.putAll(other.specifications); }
    @Override public void checkIfLegal() { }
}
