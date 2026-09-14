package de.verdox.hwapi.identity.dto;

import lombok.Builder;

import java.util.Collection;

@Builder
public record ProductSearchResultDTO(
        Collection<String> eans,
        Collection<String> upcs,
        Collection<String> gtins,
        Collection<String> mpns,
        String title
) {}