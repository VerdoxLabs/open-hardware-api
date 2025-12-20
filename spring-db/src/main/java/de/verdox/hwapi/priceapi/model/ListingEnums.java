package de.verdox.hwapi.priceapi.model;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class ListingEnums {
    public enum Continent {
        EUROPE,
        ASIA,
        AFRICA,
        NORTH_AMERICA,
        SOUTH_AMERICA,
        OCEANIA
    }

    public enum Country {


        // -------------------------------------------------
        // EUROPE
        // -------------------------------------------------
        DE("DE", "Germany", "Deutschland", Continent.EUROPE),
        AT("AT", "Austria", "Österreich", Continent.EUROPE),
        CH("CH", "Switzerland", "Schweiz", Continent.EUROPE),
        FR("FR", "France", "Frankreich", Continent.EUROPE),
        IT("IT", "Italy", "Italien", Continent.EUROPE),
        ES("ES", "Spain", "Spanien", Continent.EUROPE),
        NL("NL", "Netherlands", "Niederlande", Continent.EUROPE),
        BE("BE", "Belgium", "Belgien", Continent.EUROPE),
        PL("PL", "Poland", "Polen", Continent.EUROPE),
        SE("SE", "Sweden", "Schweden", Continent.EUROPE),
        NO("NO", "Norway", "Norwegen", Continent.EUROPE),
        DK("DK", "Denmark", "Dänemark", Continent.EUROPE),
        FI("FI", "Finland", "Finnland", Continent.EUROPE),
        IE("IE", "Ireland", "Irland", Continent.EUROPE),
        PT("PT", "Portugal", "Portugal", Continent.EUROPE),
        CZ("CZ", "Czech Republic", "Tschechien", Continent.EUROPE),
        SK("SK", "Slovakia", "Slowakei", Continent.EUROPE),
        HU("HU", "Hungary", "Ungarn", Continent.EUROPE),
        RO("RO", "Romania", "Rumänien", Continent.EUROPE),
        BG("BG", "Bulgaria", "Bulgarien", Continent.EUROPE),
        HR("HR", "Croatia", "Kroatien", Continent.EUROPE),
        SI("SI", "Slovenia", "Slowenien", Continent.EUROPE),
        LT("LT", "Lithuania", "Litauen", Continent.EUROPE),
        LV("LV", "Latvia", "Lettland", Continent.EUROPE),
        EE("EE", "Estonia", "Estland", Continent.EUROPE),
        UA("UA", "Ukraine", "Ukraine", Continent.EUROPE),
        GB("GB", "United Kingdom", "Großbritannien", Continent.EUROPE),

        // -------------------------------------------------
        // NORTH AMERICA
        // -------------------------------------------------
        US("US", "United States", "Vereinigte Staaten", Continent.NORTH_AMERICA),
        CA("CA", "Canada", "Kanada", Continent.NORTH_AMERICA),
        MX("MX", "Mexico", "Mexiko", Continent.NORTH_AMERICA),

        // -------------------------------------------------
        // SOUTH AMERICA
        // -------------------------------------------------
        BR("BR", "Brazil", "Brasilien", Continent.SOUTH_AMERICA),
        CL("CL", "Chile", "Chile", Continent.SOUTH_AMERICA),
        CO("CO", "Colombia", "Kolumbien", Continent.SOUTH_AMERICA),
        PE("PE", "Peru", "Peru", Continent.SOUTH_AMERICA),

        // -------------------------------------------------
        // ASIA
        // -------------------------------------------------
        CN("CN", "China", "China", Continent.ASIA),
        JP("JP", "Japan", "Japan", Continent.ASIA),
        IN("IN", "India", "Indien", Continent.ASIA),
        SG("SG", "Singapore", "Singapur", Continent.ASIA),
        TR("TR", "Turkey", "Türkei", Continent.ASIA),
        AE("AE", "United Arab Emirates", "Vereinigte Arabische Emirate", Continent.ASIA),

        // -------------------------------------------------
        // OCEANIA
        // -------------------------------------------------
        AU("AU", "Australia", "Australien", Continent.OCEANIA),
        NZ("NZ", "New Zealand", "Neuseeland", Continent.OCEANIA),

        // -------------------------------------------------
        // AFRICA
        // -------------------------------------------------
        ZA("ZA", "South Africa", "Südafrika", Continent.AFRICA);

        // -------------------------------------------------
        // Fields
        // -------------------------------------------------

        private static final Map<String, Country> BY_ISO = Arrays.stream(values()).collect(Collectors.toMap(Country::getIsoCode, c -> c));

        public static Optional<Country> fromIso(String iso) {
            if (iso == null) return Optional.empty();
            return Optional.ofNullable(BY_ISO.get(iso.toUpperCase()));
        }

        private final String isoCode;
        private final String nameEn;
        private final String nameDe;
        private final Continent continent;

        Country(String isoCode, String nameEn, String nameDe, Continent continent) {
            this.isoCode = isoCode;
            this.nameEn = nameEn;
            this.nameDe = nameDe;
            this.continent = continent;
        }

        public String getIsoCode() {
            return isoCode;
        }

        public String getNameEn() {
            return nameEn;
        }

        public String getNameDe() {
            return nameDe;
        }

        public Continent getContinent() {
            return continent;
        }
    }
}
