package top.cnuo.idlepool.update;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class VersionComparator {
    private VersionComparator() {
    }

    public static int compare(String left, String right) {
        ParsedVersion first = parse(left);
        ParsedVersion second = parse(right);

        int coreSize = Math.max(first.core().size(), second.core().size());
        for (int index = 0; index < coreSize; index++) {
            int comparison = Integer.compare(numberAt(first.core(), index), numberAt(second.core(), index));
            if (comparison != 0) {
                return comparison;
            }
        }

        if (first.preRelease().isEmpty() && second.preRelease().isEmpty()) {
            return 0;
        }
        if (first.preRelease().isEmpty()) {
            return 1;
        }
        if (second.preRelease().isEmpty()) {
            return -1;
        }

        int preReleaseSize = Math.max(first.preRelease().size(), second.preRelease().size());
        for (int index = 0; index < preReleaseSize; index++) {
            if (index >= first.preRelease().size()) {
                return -1;
            }
            if (index >= second.preRelease().size()) {
                return 1;
            }
            int comparison = compareIdentifier(first.preRelease().get(index), second.preRelease().get(index));
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    private static ParsedVersion parse(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("版本号不能为空");
        }
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("v")) {
            normalized = normalized.substring(1);
        }
        normalized = normalized.split("\\+", 2)[0];
        String[] mainAndPreRelease = normalized.split("-", 2);
        List<Integer> core = new ArrayList<>();
        for (String part : mainAndPreRelease[0].split("\\.")) {
            if (!part.matches("\\d+")) {
                throw new IllegalArgumentException("无法识别版本号：" + input);
            }
            core.add(Integer.parseInt(part));
        }
        List<String> preRelease = mainAndPreRelease.length == 1
                ? List.of()
                : List.of(mainAndPreRelease[1].split("[.-]"));
        return new ParsedVersion(List.copyOf(core), preRelease);
    }

    private static int compareIdentifier(String left, String right) {
        boolean leftNumeric = left.matches("\\d+");
        boolean rightNumeric = right.matches("\\d+");
        if (leftNumeric && rightNumeric) {
            return Integer.compare(Integer.parseInt(left), Integer.parseInt(right));
        }
        if (leftNumeric != rightNumeric) {
            return leftNumeric ? -1 : 1;
        }
        return left.compareTo(right);
    }

    private static int numberAt(List<Integer> numbers, int index) {
        return index < numbers.size() ? numbers.get(index) : 0;
    }

    private record ParsedVersion(List<Integer> core, List<String> preRelease) {
    }
}
