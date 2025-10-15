package de.verdox.openhardwareapi.model.values.ean;

import java.util.*;
import java.util.stream.Stream;

/**
 * GS1 prefix ranges per Wikipedia "List of GS1 country codes" (retrieved 2025-09-21).
 * Note: Prefix zeigt die GS1-Mitgliedsorganisation, nicht das Herstellungsland.
 * Source: https://en.wikipedia.org/wiki/List_of_GS1_country_codes
 */
public enum EANCountryCode {

    // --- UPC/US & Special circulation ---
    UPC_US_001_019("United States / UPC-A compatible", "GS1 US", note("UPC-A compatible"), r(1,19)),
    RESTRICTED_GEOGRAPHIC_020_029("Restricted circulation (geographic)", "GS1 (various)", note("UPC-A compatible; regional use"), r(20,29)),
    UPC_US_DRUGS_030_039("United States (drugs / NDC)", "GS1 US", note("UPC-A compatible; drugs (NDC)"), r(30,39)),
    RESTRICTED_COMPANY_040_049("Restricted circulation (company)", "GS1 (various)", note("UPC-A compatible; company internal"), r(40,49)),
    UPC_US_RESERVED_050_059("United States (reserved)", "GS1 US", note("UPC-A compatible; reserved for future use"), r(50,59)),
    UPC_US_060_099("United States / UPC-A compatible", "GS1 US", null, r(60,99)),
    UNITED_STATES_100_139("United States", "GS1 US", null, r(100,139)),
    RESTRICTED_GEOGRAPHIC_200_299("Restricted circulation (geographic)", "GS1 (various)", null, r(200,299)),

    // --- Europe (300s) ---
    FRANCE_MONACO_300_379("France & Monaco", "GS1 France / GS1 Monaco", null, r(300,379)),
    BULGARIA_380("Bulgaria", "GS1 Bulgaria", null, r(380)),
    SLOVENIA_383("Slovenia", "GS1 Slovenia", null, r(383)),
    CROATIA_385("Croatia", "GS1 Croatia", null, r(385)),
    BOSNIA_HERZEGOVINA_387("Bosnia and Herzegovina", "GS1 Bosnia & Herzegovina", null, r(387)),
    MONTENEGRO_389("Montenegro", "GS1 Montenegro", null, r(389)),
    KOSOVO_390("Kosovo", "GS1 Kosovo", null, r(390)),

    // --- Germany & Japan & Russia blocks ---
    GERMANY_400_440("Germany", "GS1 Germany", note("440 inherited from former East Germany"), r(400,440)),
    JAPAN_NEW_450_459("Japan (new JAN range)", "GS1 Japan", null, r(450,459)),
    RUSSIA_460_469("Russia", "GS1 Russia", note("inherited from Soviet Union"), r(460,469)),
    KYRGYZSTAN_470("Kyrgyzstan", "GS1 Kyrgyzstan", null, r(470)),
    TAIWAN_471("Taiwan", "GS1 Taiwan", null, r(471)),
    ESTONIA_474("Estonia", "GS1 Estonia", null, r(474)),
    LATVIA_475("Latvia", "GS1 Latvia", null, r(475)),
    AZERBAIJAN_476("Azerbaijan", "GS1 Azerbaijan", null, r(476)),
    LITHUANIA_477("Lithuania", "GS1 Lithuania", null, r(477)),
    UZBEKISTAN_478("Uzbekistan", "GS1 Uzbekistan", null, r(478)),
    SRI_LANKA_479("Sri Lanka", "GS1 Sri Lanka", null, r(479)),
    PHILIPPINES_480("Philippines", "GS1 Philippines", null, r(480)),
    BELARUS_481("Belarus", "GS1 Belarus", null, r(481)),
    UKRAINE_482("Ukraine", "GS1 Ukraine", null, r(482)),
    TURKMENISTAN_483("Turkmenistan", "GS1 Turkmenistan", null, r(483)),
    MOLDOVA_484("Moldova", "GS1 Moldova", null, r(484)),
    ARMENIA_485("Armenia", "GS1 Armenia", null, r(485)),
    GEORGIA_486("Georgia", "GS1 Georgia", null, r(486)),
    KAZAKHSTAN_487("Kazakhstan", "GS1 Kazakhstan", null, r(487)),
    TAJIKISTAN_488("Tajikistan", "GS1 Tajikistan", null, r(488)),
    HONG_KONG_489("Hong Kong", "GS1 Hong Kong", null, r(489)),
    JAPAN_ORIG_490_499("Japan (original JAN range)", "GS1 Japan", null, r(490,499)),

    // --- UK & Europe (500s) ---
    UNITED_KINGDOM_500_509("United Kingdom", "GS1 UK", null, r(500,509)),
    GREECE_520_521("Greece", "GS1 Greece", null, r(520,521)),
    LEBANON_528("Lebanon", "GS1 Lebanon", null, r(528)),
    CYPRUS_529("Cyprus", "GS1 Cyprus", null, r(529)),
    ALBANIA_530("Albania", "GS1 Albania", null, r(530)),
    NORTH_MACEDONIA_531("North Macedonia", "GS1 North Macedonia", null, r(531)),
    MALTA_535("Malta", "GS1 Malta", null, r(535)),
    IRELAND_539("Ireland", "GS1 Ireland", null, r(539)),
    BELGIUM_LUXEMBOURG_540_549("Belgium & Luxembourg", "GS1 Belgium & Luxembourg", null, r(540,549)),
    PORTUGAL_560("Portugal", "GS1 Portugal", null, r(560)),
    ICELAND_569("Iceland", "GS1 Iceland", null, r(569)),
    DENMARK_FAROE_GREENLAND_570_579("Denmark / Faroe / Greenland", "GS1 Denmark", null, r(570,579)),
    POLAND_590("Poland", "GS1 Poland", null, r(590)),
    ROMANIA_594("Romania", "GS1 Romania", null, r(594)),
    HUNGARY_599("Hungary", "GS1 Hungary", null, r(599)),

    // --- Africa & Middle East (600s) ---
    SOUTH_AFRICA_600_601("South Africa", "GS1 South Africa", null, r(600,601)),
    GHANA_603("Ghana", "GS1 Ghana", null, r(603)),
    SENEGAL_604("Senegal", "GS1 Senegal", null, r(604)),
    UGANDA_605("Uganda", "GS1 Uganda", null, r(605)),
    ANGOLA_606("Angola", "GS1 Angola", null, r(606)),
    OMAN_607("Oman", "GS1 Oman", null, r(607)),
    BAHRAIN_608("Bahrain", "GS1 Bahrain", null, r(608)),
    MAURITIUS_609("Mauritius", "GS1 Mauritius", null, r(609)),
    MOROCCO_611("Morocco", "GS1 Morocco", null, r(611)),
    SOMALIA_612("Somalia", "GS1 Somalia", null, r(612)),
    ALGERIA_613("Algeria", "GS1 Algeria", null, r(613)),
    NIGERIA_615("Nigeria", "GS1 Nigeria", null, r(615)),
    KENYA_616("Kenya", "GS1 Kenya", null, r(616)),
    CAMEROON_617("Cameroon", "GS1 Cameroon", null, r(617)),
    COTE_DIVOIRE_618("Ivory Coast", "GS1 Côte d’Ivoire", null, r(618)),
    TUNISIA_619("Tunisia", "GS1 Tunisia", null, r(619)),
    TANZANIA_620("Tanzania", "GS1 Tanzania", null, r(620)),
    SYRIA_621("Syria", "GS1 Syria", null, r(621)),
    EGYPT_622("Egypt", "GS1 Egypt", null, r(622)),
    GLOBAL_OFFICE_FUTURE_MO_623("Managed by GS1 Global Office (future MO)", "GS1 Global Office", note("was Brunei until May 2021"), r(623)),
    LIBYA_624("Libya", "GS1 Libya", null, r(624)),
    JORDAN_625("Jordan", "GS1 Jordan", null, r(625)),
    IRAN_626("Iran", "GS1 Iran", null, r(626)),
    KUWAIT_627("Kuwait", "GS1 Kuwait", null, r(627)),
    SAUDI_ARABIA_628("Saudi Arabia", "GS1 Saudi Arabia", null, r(628)),
    UAE_629("United Arab Emirates", "GS1 UAE", null, r(629)),
    QATAR_630("Qatar", "GS1 Qatar", null, r(630)),
    NAMIBIA_631("Namibia", "GS1 Namibia", null, r(631)),
    RWANDA_632("Rwanda", "GS1 Rwanda", null, r(632)),
    FINLAND_640_649("Finland", "GS1 Finland", null, r(640,649)),

    // --- China blocks ---
    CHINA_680_681("China", "GS1 China", null, r(680,681)),
    CHINA_690_699("China", "GS1 China", null, r(690,699)),

    // --- Nordics / Israel / Central America (700s) ---
    NORWAY_700_709("Norway", "GS1 Norway", null, r(700,709)),
    ISRAEL_729("Israel", "GS1 Israel", null, r(729)),
    SWEDEN_730_739("Sweden", "GS1 Sweden", null, r(730,739)),
    GUATEMALA_740("Guatemala", "GS1 Guatemala", null, r(740)),
    EL_SALVADOR_741("El Salvador", "GS1 El Salvador", null, r(741)),
    HONDURAS_742("Honduras", "GS1 Honduras", null, r(742)),
    NICARAGUA_743("Nicaragua", "GS1 Nicaragua", null, r(743)),
    COSTA_RICA_744("Costa Rica", "GS1 Costa Rica", null, r(744)),
    PANAMA_745("Panama", "GS1 Panama", null, r(745)),
    DOMINICAN_REPUBLIC_746("Dominican Republic", "GS1 Dominican Republic", null, r(746)),
    MEXICO_750("Mexico", "GS1 Mexico", null, r(750)),
    CANADA_754_755("Canada", "GS1 Canada", null, r(754,755)),
    VENEZUELA_759("Venezuela", "GS1 Venezuela", null, r(759)),
    SWITZERLAND_LIECHTENSTEIN_760_769("Switzerland & Liechtenstein", "GS1 Switzerland", null, r(760,769)),
    COLOMBIA_770_771("Colombia", "GS1 Colombia", null, r(770,771)),
    URUGUAY_773("Uruguay", "GS1 Uruguay", null, r(773)),
    PERU_775("Peru", "GS1 Peru", null, r(775)),
    BOLIVIA_777("Bolivia", "GS1 Bolivia", null, r(777)),
    ARGENTINA_778_779("Argentina", "GS1 Argentina", null, r(778,779)),
    CHILE_780("Chile", "GS1 Chile", null, r(780)),
    PARAGUAY_784("Paraguay", "GS1 Paraguay", null, r(784)),
    ECUADOR_786("Ecuador", "GS1 Ecuador", null, r(786)),
    BRAZIL_789_790("Brazil", "GS1 Brazil", null, r(789,790)),

    // --- Southern Europe (800s) ---
    ITALY_SANMARINO_VATICAN_800_839("Italy / San Marino / Vatican City", "GS1 Italy", null, r(800,839)),
    SPAIN_ANDORRA_840_849("Spain & Andorra", "GS1 Spain", null, r(840,849)),
    CUBA_850("Cuba", "GS1 Cuba", null, r(850)),
    SLOVAKIA_858("Slovakia", "GS1 Slovakia", null, r(858)),
    CZECH_REPUBLIC_859("Czech Republic", "GS1 Czech Republic", note("inherited from Czechoslovakia"), r(859)),
    SERBIA_860("Serbia", "GS1 Serbia", note("inherited via Serbia and Montenegro / Yugoslavia"), r(860)),
    MONGOLIA_865("Mongolia", "GS1 Mongolia", null, r(865)),
    NORTH_KOREA_867("North Korea", "GS1 DPR Korea", null, r(867)),
    TURKEY_868_869("Turkey", "GS1 Turkey", null, r(868,869)),
    NETHERLANDS_870_879("Netherlands", "GS1 Netherlands", null, r(870,879)),
    SOUTH_KOREA_880_881("South Korea", "GS1 Korea", null, r(880,881)),
    MYANMAR_883("Myanmar", "GS1 Myanmar", null, r(883)),
    CAMBODIA_884("Cambodia", "GS1 Cambodia", null, r(884)),
    THAILAND_885("Thailand", "GS1 Thailand", null, r(885)),
    SINGAPORE_888("Singapore", "GS1 Singapore", null, r(888)),
    INDIA_890("India", "GS1 India", null, r(890)),
    VIETNAM_893("Vietnam", "GS1 Vietnam", null, r(893)),
    BANGLADESH_894("Bangladesh", "GS1 Bangladesh", null, r(894)),
    PAKISTAN_896("Pakistan", "GS1 Pakistan", null, r(896)),
    INDONESIA_899("Indonesia", "GS1 Indonesia", null, r(899)),

    // --- DACH/ANZ (900s) ---
    AUSTRIA_900_919("Austria", "GS1 Austria", null, r(900,919)),
    AUSTRALIA_930_939("Australia", "GS1 Australia", null, r(930,939)),
    NEW_ZEALAND_940_949("New Zealand", "GS1 New Zealand", null, r(940,949)),

    // --- Special GS1 office allocations / identifiers ---
    GLOBAL_OFFICE_950("GS1 Global Office (territories without MO)", "GS1 Global Office", null, r(950)),
    EPC_GID_951("EPC GID General Manager Numbers", "GS1 (EPC Tag Data Standard)", null, r(951)),
    EXAMPLES_952("Examples / demonstrations of GS1 system", "GS1", null, r(952)),
    MALAYSIA_955("Malaysia", "GS1 Malaysia", null, r(955)),
    MACAU_958("Macau", "GS1 Macau", null, r(958)),

    // GTIN-8 allocation blocks
    GTIN8_UK_960_9624("GTIN-8 allocations (UK Office)", "GS1 UK", null, r(960,9624)),
    GTIN8_POLAND_9625_9626("GTIN-8 allocations (Poland Office)", "GS1 Poland", null, r(9625,9626)),
    GTIN8_GLOBAL_9627_969("GTIN-8 allocations (Global Office)", "GS1 Global Office", null, r(9627,969)),

    // Serial publications / ISBN / Coupons
    ISSN_977("Serial publications (ISSN)", "ISSN", null, r(977)),
    ISBN_978_979("Bookland (ISBN-13)", "ISBN", note("979-0 ISMN-13 for sheet music"), r(978,979)),
    REFUND_RECEIPTS_980("Refund receipts", "GS1", null, r(980)),
    COUPON_981_983("GS1 coupon identification (common currency areas)", "GS1", null, r(981,983)),
    COUPON_990_999("GS1 coupon identification", "GS1", null, r(990,999)),

    // Fallback
    UNKNOWN("Unknown / Not listed", null, null);

    public final String countryOrRegion;
    public final String memberOrg;
    public final String note;
    private final List<Range> ranges;

    EANCountryCode(String countryOrRegion, String memberOrg, String note, Range... ranges) {
        this.countryOrRegion = countryOrRegion;
        this.memberOrg = memberOrg;
        this.note = note;
        this.ranges = ranges == null ? List.of() : List.of(ranges);
    }
    EANCountryCode(String countryOrRegion, String memberOrg, String note) {
        this(countryOrRegion, memberOrg, note, new Range[0]);
    }

    /** Resolve by 3–5 digit prefix (handles GTIN-8 allocations like 9624). */
    public static EANCountryCode fromPrefix(int numericPrefix) {
        return Stream.of(values())
                .filter(c -> c != UNKNOWN)
                .filter(c -> c.ranges.stream().anyMatch(r -> r.contains(numericPrefix)))
                .findFirst()
                .orElse(UNKNOWN);
    }

    /** Resolve from full EAN/GTIN string (uses first 3 or more digits when needed). */
    public static EANCountryCode fromEAN(String ean) {
        if (ean == null) return UNKNOWN;
        String d = ean.replaceAll("\\D+", "");
        if (d.length() < 3) return UNKNOWN;

        // Check 5, 4, then 3 digits to cover special blocks like 9624
        int max = Math.min(d.length(), 5);
        for (int len = max; len >= 3; len--) {
            int pfx = Integer.parseInt(d.substring(0, len));
            EANCountryCode c = fromPrefix(pfx);
            if (c != UNKNOWN) return c;
        }
        return UNKNOWN;
    }

    public List<Range> ranges() { return ranges; }

    private static String note(String s) { return s; }
    private static Range r(int single) { return new Range(single, single); }
    private static Range r(int start, int endInclusive) { return new Range(start, endInclusive); }

    /** Inclusive numeric range (e.g., 400–440). */
    public static final class Range {
        public final int start, end;
        public Range(int start, int end) { this.start = start; this.end = end; }
        public boolean contains(int v) { return v >= start && v <= end; }
        @Override public String toString() { return start + "–" + end; }
    }
}
