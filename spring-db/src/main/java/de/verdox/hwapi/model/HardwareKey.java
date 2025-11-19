package de.verdox.hwapi.model;

import java.util.Objects;
import java.util.Set;

public record HardwareKey(Set<String> MPNs, Set<String> EANs) {

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        HardwareKey that = (HardwareKey) o;
        return Objects.equals(MPNs, that.MPNs) && Objects.equals(EANs, that.EANs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(MPNs, EANs);
    }
}
