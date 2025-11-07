package de.verdox.openhardwareapi.model.clustering;

import de.verdox.openhardwareapi.model.HardwareSpec;

import java.util.function.Function;

public interface ClusterRule<HARDWARE extends HardwareSpec<HARDWARE>> {
    boolean isInSameCluster(HARDWARE hardware1, HARDWARE hardware2);

    default ClusterRule<HARDWARE> and(Function<RuleBuilder<HARDWARE>, ClusterRule<HARDWARE>> other) {
        RuleBuilder<HARDWARE> builder = new RuleBuilder<>();
        return (hw1, hw2) -> this.isInSameCluster(hw1, hw2) && other.apply(builder).isInSameCluster(hw1, hw2);
    }

    default ClusterRule<HARDWARE> or(Function<RuleBuilder<HARDWARE>, ClusterRule<HARDWARE>> other) {
        RuleBuilder<HARDWARE> builder = new RuleBuilder<>();
        return (hw1, hw2) -> this.isInSameCluster(hw1, hw2) || other.apply(builder).isInSameCluster(hw1, hw2);
    }

    default ClusterRule<HARDWARE> xor(Function<RuleBuilder<HARDWARE>, ClusterRule<HARDWARE>> other) {
        RuleBuilder<HARDWARE> builder = new RuleBuilder<>();
        return (hw1, hw2) -> this.isInSameCluster(hw1, hw2) ^ other.apply(builder).isInSameCluster(hw1, hw2);
    }

    default ClusterRule<HARDWARE> not() {
        return (hw1, hw2) -> !this.isInSameCluster(hw1, hw2);
    }

    static <HARDWARE extends HardwareSpec<HARDWARE>> RuleBuilder<HARDWARE> forType(Class<HARDWARE> clazz) {
        return new RuleBuilder<>();
    }


    class RuleBuilder<HARDWARE extends HardwareSpec<HARDWARE>> {
        ClusterRule<HARDWARE> intIsBetween(Function<HARDWARE, Integer> getter, int min, int max) {
            return (hw1, hw2) -> {
                int got1 = getter.apply(hw1);
                int got2 = getter.apply(hw2);
                return got1 >= min && got1 <= max && got2 >= min && got2 <= max;
            };
        }

        <OBJECT> ClusterRule<HARDWARE> byEquals(Function<HARDWARE, OBJECT> getter) {
            return (hw1, hw2) -> getter.apply(hw1).equals(getter.apply(hw2));
        }

        <ENUM extends Enum<ENUM>> ClusterRule<HARDWARE> byEnum(Function<HARDWARE, ENUM> getter) {
            return byEquals(getter);
        }

        ClusterRule<HARDWARE> byString(Function<HARDWARE, String> getter) {
            return byEquals(getter);
        }

        ClusterRule<HARDWARE> byInt(Function<HARDWARE, Integer> getter) {
            return byEquals(getter);
        }
    }
}
