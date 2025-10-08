package de.verdox.openhardwareapi.io.parser;

import de.verdox.openhardwareapi.model.HardwareSpec;

import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class ScrapeUtil {
    public static <HARDWARE extends HardwareSpec, NUMBER extends Number> void setNumber(HARDWARE hardwareSpec, NUMBER input, Function<HARDWARE, NUMBER> getter, BiConsumer<HARDWARE, NUMBER> setter) {
        set(hardwareSpec, input, getter, setter, value -> value.doubleValue() == 0);
    }

    public static <HARDWARE extends HardwareSpec> void setString(HARDWARE hardwareSpec, String input, Function<HARDWARE, String> getter, BiConsumer<HARDWARE, String> setter) {
        set(hardwareSpec, input, getter, setter, value -> value == null || value.isBlank());
    }

    public static <HARDWARE extends HardwareSpec, ENUM extends Enum<ENUM>> void setEnum(HARDWARE hardwareSpec, ENUM input, Function<HARDWARE, ENUM> getter, BiConsumer<HARDWARE, ENUM> setter, ENUM defaultValue) {
        set(hardwareSpec, input, getter, setter, value -> value == null || value.equals(defaultValue));
    }

    public static <HARDWARE extends HardwareSpec, COLLECTION extends Collection<?>> void setCollection(HARDWARE hardwareSpec, COLLECTION input, Function<HARDWARE, COLLECTION> getter, BiConsumer<HARDWARE, COLLECTION> setter) {
        set(hardwareSpec, input, getter, setter, value -> value == null || value.isEmpty());
    }

    private static <HARDWARE extends HardwareSpec, INPUT> void set(
            HARDWARE hardwareSpec,
            INPUT value,
            Function<HARDWARE, INPUT> getter,
            BiConsumer<HARDWARE, INPUT> setter,
            Predicate<INPUT> isDefaultValue
    ) {
        checkInput(hardwareSpec, getter, setter);
        if (isDefaultValue.test(getter.apply(hardwareSpec))) {
            return;
        }
        setter.accept(hardwareSpec, value);
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
