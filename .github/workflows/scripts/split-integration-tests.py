#!/usr/bin/env python3
"""
Split the integration test classes into balanced groups for parallel CI jobs.

Reads the comma-separated class list (tests/integration-tests.txt) and the measured
time per class (tests/integration-test-timings.csv), assigns each class to the group
with the least total time so far (largest classes first), and prints the classes of
the requested group as a comma-separated list, ready for `mvn test -Dtest=...`.

Classes without a recorded time get DEFAULT_SECONDS, so new tests are still run.

Usage: split-integration-tests.py <group> <groups> [tests-file] [timings-file]
       (group is 1-based)
"""
import sys

DEFAULT_SECONDS = 30


def read_classes(path):
    with open(path) as f:
        return [c.strip() for c in f.read().split(",") if c.strip()]


def read_timings(path):
    timings = {}
    with open(path) as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#") or line == "class,seconds":
                continue
            name, seconds = line.split(",")
            timings[name.strip()] = float(seconds)
    return timings


def split(classes, timings, groups):
    # Sort by time (largest first), then by name so the split is the same on every runner.
    ordered = sorted(classes, key=lambda c: (-timings.get(c, DEFAULT_SECONDS), c))
    buckets = [{"seconds": 0.0, "classes": []} for _ in range(groups)]
    for c in ordered:
        lightest = min(buckets, key=lambda b: b["seconds"])
        lightest["seconds"] += timings.get(c, DEFAULT_SECONDS)
        lightest["classes"].append(c)
    return buckets


def main():
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    group, groups = int(sys.argv[1]), int(sys.argv[2])
    tests_file = sys.argv[3] if len(sys.argv) > 3 else "tests/integration-tests.txt"
    timings_file = sys.argv[4] if len(sys.argv) > 4 else "tests/integration-test-timings.csv"
    if not 1 <= group <= groups:
        sys.exit(f"group must be between 1 and {groups}, got {group}")

    buckets = split(read_classes(tests_file), read_timings(timings_file), groups)
    for i, b in enumerate(buckets, start=1):
        marker = "  <- this job" if i == group else ""
        print(f"group {i}/{groups}: {len(b['classes'])} classes, ~{b['seconds'] / 60:.1f} min{marker}", file=sys.stderr)
    print(",".join(sorted(buckets[group - 1]["classes"])))


if __name__ == "__main__":
    main()
