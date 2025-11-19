package de.verdox.hwapi.io.parser;

import de.verdox.hwapi.hardwareapi.component.service.ScrapingService;
import de.verdox.hwapi.model.HardwareSpec;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.*;
import java.util.logging.Level;

public class ScrapeParser<HARDWARE extends HardwareSpec> {
    private final Map<String, List<String>> specs;
    private final Set<Consumer<HARDWARE>> consumers = ConcurrentHashMap.newKeySet();

    public ScrapeParser(Map<String, List<String>> specs) {
        this.specs = specs;
    }

    public void parse(HARDWARE hardware) {
        consumers.forEach(consumer -> consumer.accept(hardware));
    }

    public ScrapeParser<HARDWARE> parseString(String key, Function<HARDWARE, String> getter, BiConsumer<HARDWARE, String> setter) {
        consumers.add(hardware -> parse(hardware, key, String::trim, getter, setter, ""));
        return this;
    }

    public <INPUT extends Enum<INPUT>> ScrapeParser<HARDWARE> parseEnum(String key, Function<HARDWARE, INPUT> getter, BiConsumer<HARDWARE, INPUT> setter, BiPredicate<String, INPUT> isEqual, INPUT defaultValue) {
        consumers.add(hardware -> parse(hardware, key, (value) -> {
            INPUT[] enumConstants = (INPUT[]) defaultValue.getClass().getEnumConstants();
            return Arrays.stream(enumConstants).filter(e -> isEqual.test(value, e)).findFirst().orElse(defaultValue);
        }, getter, setter, defaultValue));
        return this;
    }

    public <INPUT extends Number> ScrapeParser<HARDWARE> parseNumber(String key, Function<String, INPUT> dataParser, Function<HARDWARE, INPUT> getter, BiConsumer<HARDWARE, INPUT> setter, INPUT defaultValue) {
        consumers.add(hardware -> parse(hardware, key, raw -> dataParser.apply(raw.replaceAll("[^0-9-]", "")), getter, setter, defaultValue));
        return this;
    }


    public <INPUT> ScrapeParser<HARDWARE> parse(
            HARDWARE hardwareSpec,
            String key,
            Function<String, INPUT> dataParser,
            Function<HARDWARE, INPUT> getter, BiConsumer<HARDWARE, INPUT> setter,
            INPUT defaultValue
    ) {
        if (!specs.containsKey(key)) {
            ScrapingService.LOGGER.log(Level.FINER, "Scraper could not find data associated to the key " + key + " in model " + specs.get("model") + " | " + specs.keySet());
            return this;
        }
        List<String> data = specs.get(key);
        if (data.isEmpty()) {
            ScrapingService.LOGGER.log(Level.FINER, "Scraper could not find data in the entry set for key " + key + " in model " + specs.get("model") + " | " + specs.keySet());
            return this;
        }
        String dataAsString = data.getFirst();
        set(hardwareSpec, () -> {
            try {
                return dataParser.apply(dataAsString);
            } catch (Throwable throwable) {
                ScrapingService.LOGGER.log(Level.FINER, "An error occured while parsing the string data for key " + key + ": " + dataAsString, throwable);
                return defaultValue;
            }
        }, getter, setter, input -> input == null || Objects.equals(input, defaultValue));
        return this;
    }

    private <INPUT> void set(
            HARDWARE hardwareSpec,
            Supplier<INPUT> value,
            Function<HARDWARE, INPUT> getter,
            BiConsumer<HARDWARE, INPUT> setter,
            Predicate<INPUT> isDefaultValue
    ) {
        checkInput(hardwareSpec, getter, setter);
        INPUT current = getter.apply(hardwareSpec);
        if (!isDefaultValue.test(current)) {
            return;
        }
        INPUT newValue = value.get();
        if (isDefaultValue.test(newValue)) {
            return;
        }
        setter.accept(hardwareSpec, newValue);
    }

    private static <HARDWARE extends HardwareSpec, INPUT> void checkInput(HARDWARE hardwareSpec, Function<HARDWARE, INPUT> getter, BiConsumer<HARDWARE, INPUT> setter) {
        if (hardwareSpec == null) {
            throw new IllegalArgumentException("The hardware cannot be null!");
        }
        if (getter == null) {
            throw new IllegalArgumentException("The getter cannot be null!");
        }
        if (setter == null) {
            throw new IllegalArgumentException("The getter cannot be null!");
        }
    }
}
