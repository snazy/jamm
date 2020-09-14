Overview
========

Jamm provides `MemoryMeter`, a Java agent for all Java versions to
measure actual object memory use including JVM overhead.


THIS IS A POC BRANCH FOR JDK/JEP-8249196 !
==========================================

Requirements:
* Special JDK build from the `JDK-8249196-low-level-object` branch in https://hg.openjdk.java.net/jdk/sandbox/branches
* Understand the goals of https://openjdk.java.net/jeps/8249196
* (See also https://bugs.openjdk.java.net/browse/JDK-8249196)

OpenJDK jdk-dev review thread: https://mail.openjdk.java.net/pipermail/jdk-dev/2020-August/004574.html

A build of `JDK-8249196-low-level-object` is required to _build_ this branch.

During development of this POC branch, a bunch of refactorings had to be applied, see the `0.4.0 notes` below.

It became clear, that all interpretation of the object layout via jamm's "specification" and "unsafe" modes need
to be considered as best effort (read: can be wrong) as it is hard to consider all potential object layouts of
all JVMs and GCs and machine/configuration options.

Jamm's "instrumentation" mode is better, because it relies on the object-size reported by the JVM. But this mode
still needs reflection for `measureDeep()`, which is expensive and insecure and future Java versions will prevent
these "illegal accesses" (as reported by Java > 8 with messages like `WARNING: An illegal reflective access
operation has occurred`).

The new "runtime" mode prevents all these downsides:
* No "illegal accesses" (as there's no reflection)
* correct object tree sizes (no guess-timates)


Use
===

To use `MemoryMeter`, start the JVM with `-javaagent:<path to>/jamm.jar`.

You can then use MemoryMeter in your code like this:

    MemoryMeter meter = MemoryMeter.builder().build();
    meter.measure(object);
    meter.measureDeep(object);

See [`MemoryMeter.Builder`](./src/org/github/jamm/MemoryMeter.java) for more
options.

If you would like to use `MemoryMeter` in a web application, make sure
that you do NOT put this jar in `WEB-INF/lib`, as that may cause problems
since your code is accessing a MemoryMeter from a different class loader
than the one loaded by the `-javaagent` and won't see it as initialized.

If you want `MemoryMeter` not to measure some specific classes, you can
mark the classes (or interfaces) using the
[`@Unmetered`](./src/org/github/jamm/Unmetered.java) annotation.

It is good to reuse existing `MemoryMeter` instances. Creating new `MemoryMeter`
instances can and will cause significant performance penalties and also
unnecessary side effects, since all guess-modes rely on `java.lang.ClassValue`.


Dependency coordinates for the latest version of Jamm
=====================================================

Gradle:

    dependencies {
        implementation("com.github.jbellis:jamm:0.4.0")
    }

Maven:

    <dependency>
        <groupId>com.github.jbellis</groupId>
        <artifactId>jamm</artifactId>
        <version>0.4.0</version>
    </dependency>


Building
========

Build + test:

    ./gradlew jar test

To run the microbenchmark:

    ./gradlew microbench
    build/microbench <JMH options...>

Public to local Maven repo (in case you need that)

    ./gradlew publishToMavenLocal

IDEs
====

You can just open the project in IntelliJ. If you've opened the Maven-ish branch(es) in IntelliJ before,
you'll have to remove the `.idea` folder.

Benchmarks
==========

See [`Microbench.java`](./microbench/org/github/jamm/jmh/Microbench.java)

To run the microbenchmarks, execute `./gradlew microbench` and run `build/microbench` from the command line.

Some microbenchmark results
(JVM options: `-Xms16g -Xmx16g -XX:+UseG1GC -XX:+AlwaysPreTouch`):

Java 16 (from the `JDK-8249196-low-level-object` jdk-sandbox branch @ `16625b2b71f7`)
-------------------------------------------------------------------------------------

    Benchmark                                      (guess)  (nested)  (refs)  Mode  Cnt   Score    Error  Units
    Microbench.arrayByteArray                  ALWAYS_SPEC       100       4  avgt    3   0.012 ±  0.021  us/op
    Microbench.arrayByteArray                ALWAYS_UNSAFE       100       4  avgt    3   0.012 ±  0.020  us/op
    Microbench.arrayByteArray       ALWAYS_INSTRUMENTATION       100       4  avgt    3   0.012 ±  0.021  us/op
    Microbench.arrayByteArray               ALWAYS_RUNTIME       100       4  avgt    3   0.006 ±  0.006  us/op
    Microbench.cls1                            ALWAYS_SPEC       100       4  avgt    3   0.025 ±  0.002  us/op
    Microbench.cls1                          ALWAYS_UNSAFE       100       4  avgt    3   0.025 ±  0.004  us/op
    Microbench.cls1                 ALWAYS_INSTRUMENTATION       100       4  avgt    3   0.025 ±  0.004  us/op
    Microbench.cls1                         ALWAYS_RUNTIME       100       4  avgt    3   0.032 ±  0.013  us/op
    Microbench.cls2                            ALWAYS_SPEC       100       4  avgt    3   0.345 ±  0.037  us/op
    Microbench.cls2                          ALWAYS_UNSAFE       100       4  avgt    3   0.351 ±  0.030  us/op
    Microbench.cls2                 ALWAYS_INSTRUMENTATION       100       4  avgt    3   0.379 ±  0.055  us/op
    Microbench.cls2                         ALWAYS_RUNTIME       100       4  avgt    3   0.335 ±  0.077  us/op
    Microbench.cls3                            ALWAYS_SPEC       100       4  avgt    3   0.725 ±  0.026  us/op
    Microbench.cls3                          ALWAYS_UNSAFE       100       4  avgt    3   0.738 ±  0.073  us/op
    Microbench.cls3                 ALWAYS_INSTRUMENTATION       100       4  avgt    3   0.754 ±  0.047  us/op
    Microbench.cls3                         ALWAYS_RUNTIME       100       4  avgt    3   0.747 ±  0.077  us/op
    Microbench.deeplyNested                    ALWAYS_SPEC       100       4  avgt    3  48.558 ±  4.990  us/op
    Microbench.deeplyNested                  ALWAYS_UNSAFE       100       4  avgt    3  48.676 ±  5.895  us/op
    Microbench.deeplyNested         ALWAYS_INSTRUMENTATION       100       4  avgt    3  48.782 ±  3.770  us/op
    Microbench.deeplyNested                 ALWAYS_RUNTIME       100       4  avgt    3  53.761 ±  8.016  us/op
    Microbench.justByteArray                   ALWAYS_SPEC       100       4  avgt    3   0.012 ±  0.021  us/op
    Microbench.justByteArray                 ALWAYS_UNSAFE       100       4  avgt    3   0.012 ±  0.019  us/op
    Microbench.justByteArray        ALWAYS_INSTRUMENTATION       100       4  avgt    3   0.012 ±  0.020  us/op
    Microbench.justByteArray                ALWAYS_RUNTIME       100       4  avgt    3   0.006 ±  0.006  us/op
    Microbench.justByteBuffer                  ALWAYS_SPEC       100       4  avgt    3   0.059 ±  0.010  us/op
    Microbench.justByteBuffer                ALWAYS_UNSAFE       100       4  avgt    3   0.059 ±  0.025  us/op
    Microbench.justByteBuffer       ALWAYS_INSTRUMENTATION       100       4  avgt    3   0.066 ±  0.021  us/op
    Microbench.justByteBuffer               ALWAYS_RUNTIME       100       4  avgt    3   0.085 ±  0.027  us/op
    Microbench.justString                      ALWAYS_SPEC       100       4  avgt    3   0.059 ±  0.015  us/op
    Microbench.justString                    ALWAYS_UNSAFE       100       4  avgt    3   0.058 ±  0.039  us/op
    Microbench.justString           ALWAYS_INSTRUMENTATION       100       4  avgt    3   0.062 ±  0.036  us/op
    Microbench.justString                   ALWAYS_RUNTIME       100       4  avgt    3   0.068 ±  0.019  us/op
    Microbench.runtimeBB                    ALWAYS_RUNTIME       100       4  avgt    3   0.073 ±  0.031  us/op
    Microbench.runtimeByteArray             ALWAYS_RUNTIME       100       4  avgt    3   0.005 ±  0.005  us/op
    Microbench.runtimeCls1                  ALWAYS_RUNTIME       100       4  avgt    3   0.028 ±  0.007  us/op
    Microbench.runtimeCls2                  ALWAYS_RUNTIME       100       4  avgt    3   0.318 ±  0.069  us/op
    Microbench.runtimeCls3                  ALWAYS_RUNTIME       100       4  avgt    3   0.651 ±  0.061  us/op
    Microbench.runtimeDeeplyNested          ALWAYS_RUNTIME       100       4  avgt    3  50.026 ± 19.874  us/op
    Microbench.runtimeString                ALWAYS_RUNTIME       100       4  avgt    3   0.057 ±  0.033  us/op

Java 11.0.8
-----------

    Benchmark                                 (guess)  (nested)  (refs)  Mode  Cnt   Score   Error  Units
    Microbench.arrayByteArray             ALWAYS_SPEC       100       4  avgt    3   0.012 ± 0.019  us/op
    Microbench.arrayByteArray           ALWAYS_UNSAFE       100       4  avgt    3   0.012 ± 0.019  us/op
    Microbench.arrayByteArray  ALWAYS_INSTRUMENTATION       100       4  avgt    3   0.012 ± 0.017  us/op
    Microbench.cls1                       ALWAYS_SPEC       100       4  avgt    3   0.026 ± 0.004  us/op
    Microbench.cls1                     ALWAYS_UNSAFE       100       4  avgt    3   0.026 ± 0.005  us/op
    Microbench.cls1            ALWAYS_INSTRUMENTATION       100       4  avgt    3   0.026 ± 0.004  us/op
    Microbench.cls2                       ALWAYS_SPEC       100       4  avgt    3   0.350 ± 0.155  us/op
    Microbench.cls2                     ALWAYS_UNSAFE       100       4  avgt    3   0.387 ± 0.030  us/op
    Microbench.cls2            ALWAYS_INSTRUMENTATION       100       4  avgt    3   0.355 ± 0.040  us/op
    Microbench.cls3                       ALWAYS_SPEC       100       4  avgt    3   0.761 ± 0.087  us/op
    Microbench.cls3                     ALWAYS_UNSAFE       100       4  avgt    3   0.733 ± 0.130  us/op
    Microbench.cls3            ALWAYS_INSTRUMENTATION       100       4  avgt    3   0.738 ± 0.099  us/op
    Microbench.deeplyNested               ALWAYS_SPEC       100       4  avgt    3  49.825 ± 1.482  us/op
    Microbench.deeplyNested             ALWAYS_UNSAFE       100       4  avgt    3  46.260 ± 4.466  us/op
    Microbench.deeplyNested    ALWAYS_INSTRUMENTATION       100       4  avgt    3  52.301 ± 8.285  us/op
    Microbench.justByteArray              ALWAYS_SPEC       100       4  avgt    3   0.014 ± 0.005  us/op
    Microbench.justByteArray            ALWAYS_UNSAFE       100       4  avgt    3   0.014 ± 0.004  us/op
    Microbench.justByteArray   ALWAYS_INSTRUMENTATION       100       4  avgt    3   0.014 ± 0.006  us/op
    Microbench.justByteBuffer             ALWAYS_SPEC       100       4  avgt    3   0.056 ± 0.011  us/op
    Microbench.justByteBuffer           ALWAYS_UNSAFE       100       4  avgt    3   0.058 ± 0.018  us/op
    Microbench.justByteBuffer  ALWAYS_INSTRUMENTATION       100       4  avgt    3   0.058 ± 0.029  us/op
    Microbench.justString                 ALWAYS_SPEC       100       4  avgt    3   0.059 ± 0.061  us/op
    Microbench.justString               ALWAYS_UNSAFE       100       4  avgt    3   0.057 ± 0.019  us/op
    Microbench.justString      ALWAYS_INSTRUMENTATION       100       4  avgt    3   0.056 ± 0.001  us/op

Java 8u265
----------

    Benchmark                                 (guess)  (nested)  (refs)  Mode  Cnt   Score    Error  Units
    Microbench.arrayByteArray             ALWAYS_SPEC       100       4  avgt    3   0.011 ±  0.007  us/op
    Microbench.arrayByteArray           ALWAYS_UNSAFE       100       4  avgt    3   0.011 ±  0.006  us/op
    Microbench.arrayByteArray  ALWAYS_INSTRUMENTATION       100       4  avgt    3   0.011 ±  0.007  us/op
    Microbench.cls1                       ALWAYS_SPEC       100       4  avgt    3   0.023 ±  0.009  us/op
    Microbench.cls1                     ALWAYS_UNSAFE       100       4  avgt    3   0.025 ±  0.039  us/op
    Microbench.cls1            ALWAYS_INSTRUMENTATION       100       4  avgt    3   0.024 ±  0.012  us/op
    Microbench.cls2                       ALWAYS_SPEC       100       4  avgt    3   0.357 ±  0.034  us/op
    Microbench.cls2                     ALWAYS_UNSAFE       100       4  avgt    3   0.365 ±  0.008  us/op
    Microbench.cls2            ALWAYS_INSTRUMENTATION       100       4  avgt    3   0.366 ±  0.100  us/op
    Microbench.cls3                       ALWAYS_SPEC       100       4  avgt    3   0.773 ±  0.178  us/op
    Microbench.cls3                     ALWAYS_UNSAFE       100       4  avgt    3   0.754 ±  0.328  us/op
    Microbench.cls3            ALWAYS_INSTRUMENTATION       100       4  avgt    3   0.755 ±  0.396  us/op
    Microbench.deeplyNested               ALWAYS_SPEC       100       4  avgt    3  46.889 ± 18.817  us/op
    Microbench.deeplyNested             ALWAYS_UNSAFE       100       4  avgt    3  45.166 ±  9.059  us/op
    Microbench.deeplyNested    ALWAYS_INSTRUMENTATION       100       4  avgt    3  48.041 ± 14.777  us/op
    Microbench.justByteArray              ALWAYS_SPEC       100       4  avgt    3   0.011 ±  0.008  us/op
    Microbench.justByteArray            ALWAYS_UNSAFE       100       4  avgt    3   0.011 ±  0.009  us/op
    Microbench.justByteArray   ALWAYS_INSTRUMENTATION       100       4  avgt    3   0.011 ±  0.007  us/op
    Microbench.justByteBuffer             ALWAYS_SPEC       100       4  avgt    3   0.053 ±  0.013  us/op
    Microbench.justByteBuffer           ALWAYS_UNSAFE       100       4  avgt    3   0.053 ±  0.011  us/op
    Microbench.justByteBuffer  ALWAYS_INSTRUMENTATION       100       4  avgt    3   0.053 ±  0.014  us/op
    Microbench.justString                 ALWAYS_SPEC       100       4  avgt    3   0.055 ±  0.010  us/op
    Microbench.justString               ALWAYS_UNSAFE       100       4  avgt    3   0.054 ±  0.007  us/op
    Microbench.justString      ALWAYS_INSTRUMENTATION       100       4  avgt    3   0.054 ±  0.019  us/op


_Previous_ (0.3.x) versions of jamm (w/ 8u265)
----------------------------------------------

*Mentioned for comparison reasons!* Performance numbers for the recent version above.

    Benchmark                        (guess)  (nested)  (refs)  Mode  Cnt    Score    Error  Units
    Microbench.cls1              ALWAYS_SPEC       100       4  avgt    3    0.243 ±  0.170  us/op
    Microbench.cls1            ALWAYS_UNSAFE       100       4  avgt    3    0.304 ±  0.078  us/op
    Microbench.cls1                    NEVER       100       4  avgt    3    0.210 ±  0.126  us/op
    Microbench.cls2              ALWAYS_SPEC       100       4  avgt    3    2.924 ±  0.272  us/op
    Microbench.cls2            ALWAYS_UNSAFE       100       4  avgt    3    3.086 ±  0.419  us/op
    Microbench.cls2                    NEVER       100       4  avgt    3    2.624 ±  0.131  us/op
    Microbench.cls3              ALWAYS_SPEC       100       4  avgt    3    5.919 ±  1.433  us/op
    Microbench.cls3            ALWAYS_UNSAFE       100       4  avgt    3    6.031 ±  1.632  us/op
    Microbench.cls3                    NEVER       100       4  avgt    3    5.329 ±  0.513  us/op
    Microbench.deeplyNested      ALWAYS_SPEC       100       4  avgt    3  500.569 ± 27.362  us/op
    Microbench.deeplyNested    ALWAYS_UNSAFE       100       4  avgt    3  495.529 ± 66.984  us/op
    Microbench.deeplyNested            NEVER       100       4  avgt    3  388.130 ± 83.949  us/op
    Microbench.justByteArray     ALWAYS_SPEC       100       4  avgt    3    0.012 ±  0.011  us/op
    Microbench.justByteArray   ALWAYS_UNSAFE       100       4  avgt    3    0.013 ±  0.004  us/op
    Microbench.justByteArray           NEVER       100       4  avgt    3    0.055 ±  0.010  us/op
    Microbench.justByteBuffer    ALWAYS_SPEC       100       4  avgt    3    0.942 ±  0.230  us/op
    Microbench.justByteBuffer  ALWAYS_UNSAFE       100       4  avgt    3    0.935 ±  0.154  us/op
    Microbench.justByteBuffer          NEVER       100       4  avgt    3    0.689 ±  0.247  us/op
    Microbench.justString        ALWAYS_SPEC       100       4  avgt    3    0.616 ±  0.165  us/op
    Microbench.justString      ALWAYS_UNSAFE       100       4  avgt    3    0.684 ±  0.636  us/op
    Microbench.justString              NEVER       100       4  avgt    3    0.574 ±  0.098  us/op


The fine print
==============

MemoryMeter can use the `Runtime.deepSizeOf()` functionality added in
Java 16, which is by far the most accurate way to measure the heap
size occupied by an object or object-tree.

Alternatively, `MemoryMeter` can use
`java.lang.instrument.Instrumentation.getObjectSize()`, which only claims
to provide "approximate" results, but in practice seems to work as
expected.

Two more implementations are available as well. One is using
`sun.misc.Unsafe` w/ reflection and one just uses reflection. Both are
inaccurate and rely on guesses. It is strongly recommended to *not
use* "unsafe" or "specification" and jamm will emit a warning on
stderr:
* The "Unsafe" implementation performs arithmetics on the "cookies"
  returned by `Unsafe.objectFieldOffset()`, althought the Javadoc says:
  `Do not expect to perform any sort of arithmetic on this offset;
  it is just a cookie which is passed to the unsafe heap memory accessors.`
  The implementation does not always consider Java object layouts in under
  all circumstances for all JVMs.
* The "specification" is just a best-effort guess-timate that can
  easily produce results that are wrong w/ newer JVMs and GCs and/or
  newer Java features.

0.4.0 notes
===========

* The method `MemoryMeter.countChildren()` has been removed, because
  there is a) no corresponding implementation from JDK-8249196 and
  b) there was no test coverage at all.
* The construction of the `MemoryMeter` instance has been refactored
  to a proper builder pattern to allow distinct classes for each
  implementation (JDK-8249196/Runtime.deepSizeOf(), Instrumentation,
  Unsafe, Specification).
* A bunch of internal optimizations went into the existing guess-modes
  and performance for these modes has also been _significantly_
  improved (about 10x, see microbenchmark results above).
* Support for `@Unmetered` for fields has been removed for the future
  implementation of `java.lang.Runtime.deepSizeOf()` with Java 16.
