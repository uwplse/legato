[![Build Status](https://travis-ci.com/uwplse/legato.svg?branch=master)](https://travis-ci.com/uwplse/legato)

# Legato User Guide

## Requirements

To build and run the main Legato tool, will need at least Gradle
version 4.0 and Java 7. To use the python scripts described in this
README you will additionally need the following python packages:
* PyYaml
* jinja
* pygments
* pyparsing
* numpy
* termcolor

To use the servlet generation and stubs you will also need Tomcat 7.

## Setup

If you plan to use the wrapper scripts described below you will have
to provide a Legato setup configuration. Create the file `legato_env.py` in the
`util/` directory. This module should contain the following top-level constants:

* `TOMCAT_BIN`: A string representing the absolute path to the binary folder of the Tomcat 7 install, e.g., `/usr/share/tomcat7/bin/`
* `TOMCAT_LIB`:  The path to the library folder of the Tomcat 7 installation, e.g. `/user/share/comcat7/lib/`
* `SOURCE_DIR`: A path to a common source folder containing programs to be analyzed. This value is only used to provide the value of the `${source_dir}` variable when expanding servlet definitions, and may be assigned a dummy value if it is unused.

## Building Legato

Run `gradle assemble copyDep` in the top level project directory.

## How to Run Legato

The basic template to run Legato is as follows (assuming the command is run from within the project directory):

`java -ea -classpath 'build/build-deps/*:build/classes/eclipse' edu.washington.cse.instrumentation.analysis.Legato $LIBRARY_CLASSPATH $APPLICATION_CLASSPATH $SUMMARY_FILES $RESOURCE_FILE $MAIN_CLASS <options...> $SPEC_STRING`

| Placeholder | Explanation |
| --- | --- |
| `$LIBRARY_CLASSPATH` | The classpath of libraries used by the application. The distinction between libraries and applications is described below. |
| `$APPLICATION_CLASSPATH` | The application classpath. |
| `$SUMMARY_FILES` | A colon separated list of paths to summary specification files. |
| `$RESOURCE_FILE` | A resource resolver and configuration specification. The precise format is described below. |
| `$MAIN_CLASS` | The application entry point, i.e., the fully qualified name of the class containing `main()` |
| `$SPEC_STRING` | **Optional** A key-value string (described below) further configuring the Legato analysis. |

**Application vs. Library Classes**: By default, when Soot cannot precisely resolve a `newInstance()` call, it assumes any class in the
set of _dynamic packages_ may be instantiated. For Legato, only packages appearing in the application classpath are considered dynamic packages.

The options supported by Legato are:

| Option | Meaning |
| --- | --- |
| `-m` | Sample the JVM memory every second, keeping a running maximum. If provided an optional argument, writes the largest observed heap (in bytes) to the specified file, or to standard out |
| `-t <file>` | Write timing information collected while running Legato to `<file>` |
| `-s <file>` | Perform call-graph construction and load bytecode, dump statistics to `<file>` and quit. |
| `-h` | Use CHA instead of the more precise SPARK to build call-graphs. |
| `-r <file>` | Use the routing information present in `<file>` to resolve indirect flows, as described in Section 5.0. |
| `-c` | Do not use the built-in static initializer model, rely on Soot's instead. |
| `-e <file>` | Read include/exclude information from `<file>`. Excluded classes are loaded into the class hierarchy, but not analyzed. |

The key-value string has the format `key:value,...,key:value`. Legato automatically supplies some of these values, we therefore only
describe those not set by Legato:

| Key            | Value                                                                                                                                                                |
| ---            | ---                                                                                                                                                                  |
| `sync-havoc`   | **Boolean** (default: true) Pessimistically assume that reading from the heap in a synchronized block havocs configuration information, as described in Section 5.4. |
| `summary-mode` | One of `IGNORE`, `WARN`, `BOTTOM`, `FAIL`: sets how to handle unsummarized functions. `IGNORE` (the default) applies the default summary described in Section 5.0, `WARN` prints a warning for every unsummarized function, `BOTTOM` assumes the result is inconsistent, `FAIL` immediately halts analysis. |
| `output` | One of `console` (default), `quiet` or `yaml`. `console`: print all reports to console, `quiet`: don't print any reports, `yaml`: write reports to the file specified by `output-opt` |
| `output-opt`   | **String** See above.                                                                                                                                                                 |
| `hofe`         | **Boolean** (default: false). Halt on first error found by Legato.                                                                                                                                                                   |
| `warn-log`     | **String** If `summary-mode` is `WARN`, the file into which to write warnings. |
| `ignore-file`  | **String** A file containing a list of Soot method signature that should be treated as nops for the purposes of information flow. |
| `track-sites`  | **Boolean** (default: false) If true, record the units involved in any report. |
| `site-log`     | **String**: If track-sites is enabled, the output yaml file for the recorded sites |

### Resource Models

Out-of-the-box, Legato supports a handful of resource models. To use a
resource model, you must specify a resolver and (optional) resolver
configuration. The syntax for specifying a resolver on the command line is `resolver-name:config-string`.

The resolvers support by Legato are:

* `simple-get`: Any static method with the name `get()` is considered a resource access of a singleton resource. Used only for testing, no configuration is necessary.
* `simple-string`: A key-value model. The exact resource accessed by a method _m_ is determined by a string argument passed to _m_.
* `yaml-file`: A key-value model like `simple-string` above, but configured with a YAML file.
* `static`: A resource model where different methods access a specific set of resources.
* `hybrid`: A combination of `yaml-file` and `static` above.

We now describe the configuration methods for the above resolvers.

#### Simple String
The simple string model only supports a single resource access method. The config string format is `sig;pos`, where `sig` is the Soot method
signature of the resource access method, and `pos` is the position of the string argument determining which resource is accessed.

#### Yaml File
The config string should be a path to a YAML file, the contents of which specify the resource access methods.
The YAML file is a list of yaml dictionaries. To specify that a resource
access method with Soot signature `"<Foo: int get(String)>"` whose 1st argument names the resource being accessed,
the following dictionary should be included in the list:

```
sig: "<Foo: int get(String)>"
pos: 1
```

There should be one such dictionary for every resource access method. In addition, the resource names may be filtered
using a whitelist of blacklist. In you include the dictionary:
```
whitelist: [ "foo", "bar", ...]
```
then the resolver will ignore resource accesses for resources _not_ included in the whitelist.

If you include the dictionary:
```
blacklist: [ "foo", "bar", ... ]
```
then the resolver will ignore resources included in the blacklist.

#### Static
The configuration string is a yaml file, the contents of which configure the resolver. As with the Yaml File resolver, the
yaml file is a list of dictionaries. The first dictionary must be a singleton dictionary mapping the key `access-sigs` to a list
of method signatures for all resource accessing methods.

The remaining dictionaries in the list have the form:

```
sig: $SIG
resources: [ $RESOURCE1, $RESOURCE2, ... ]
```

Which indicates that the method with soot signature `$SIG` accesses the resources `$RESOURCE1`, `$RESOURCE2`, ...

#### Hybrid

The hybrid model composes the yaml file and static models. The configuration string is a YAML file, the
contents of which are a list of dictionaries. These dictionaries either for configuring the static
component, or the yaml-file key value component: the hybrid resolver examines the structure of
the dictionary and dispatches as appropriate. In other words, a hybrid configuration is any
interleaving of a static configuration and yaml file configuration.

#### Custom Resource Resolvers

As mentioned in Section 5.2, it is possible to implement your own resource
resolver.  To do so, implement
`edu.washington.cse.instrumentation.analysis.resource.ResourceResolver`
and add a name for this resolver implementation in `parseResolver()`
in `edu.washington.cse.instrumentation.analysis.AnalysisConfiguration`.

The interface has four methods:
* `isResourceAccess(InvokeExpr ie, Unit u)`: returns true if the method call `ie` in
  `u` is a resource access.
* `getAccessedResources(InvokeExpr ie, Unit u)`: Return a set of strings for each
  method accessed by the resource. Return the empty set if no resources are accessed.
  If some unknown resource is accessed, return `null`.
* `isResourceMethod(SootMethod m)`: return true if the method `m` could access a resource.
* `getResourceAccessMethods()`: return a collection of every method for which `isResourceMethod` returns
  true.

### Summary Specifications
Summary specifications are given in YAML files. Each yaml file is a
list of dictionaries, each providing a summary for a method. The basic
format of summary dictionary is:

```
sig: $SIG
target $SIG
```

Where `$SIG` is the Soot method signature of the method to be summarized, and target
describes the method behavior. Possible values for `target` are:

* `RETURN`: Propagate information from all arguments (including the
  receiver, if applicable) to the returned value. This is more precise than
  the default summary, which propagates to the return value _and_ all subfields thereof.
* `IDENTITY`: Do not perform any propagation.
* `RECEIVER`: Propagate information from the arguments to the receiver value.
* `FLUENT`: Propagate information to both the receiver and return value (e.g., for methods that return `this`)
* `CONTAINER_PUT`: Propagate information from the arguments to a special "container contents" subfield of the receiver.
* `CONTAINER_GET`: Propagate information from the container contents subfield of the receiver to the return value.
* `CONTAINER_TRANSFER`: Propagate information from the container contents of the receiver to the container contents of the return value (useful for methods like `subList`, `entrySet`, etc.)
* `CONTAINER_ADDALL`: Dual of `CONTAINER_TRANSFER`, propagate information from the container contents of the argument to the container contents of the receiver.
* `CONTAINER_MOVE`: Propagate information from the receiver contents to the _arguments_ contents (for methods like `drainTo`)
* `GRAPH`: Propagate information from the arguments to the access graph configured in the dictionary (see below).
* `HAVOC`: Propagate information from all arguments (including receiver) to all other arguments (including receiver) and the return value. Potentially more sound, but likely useless.
* `DIE`: Halt the analysis if this method is ever called.

When propagating information from arguments, usually only values stored in the local argument are considered.
If all information from all subfields should also be propagated, include the `subfields` key in the summary
dictionary. The subfields key should map to a list of integers which contains argument positions (starting
from 0, the receiver) that should have field information also propagated.

To configure the graph propagation, specify a graph resolver by
including a `resolver` key in the summary dictionary. There are two resolvers:

* `ret-graph`: Propagate information from the argument to an access graph rooted
  in the return value. The fields of the access graph are specified by a list of
  field signatures included in the summary dictionary under the key `fields`. (A special signature,
  `"ARRAY_FIELD"` refers to the pseudo-field used for array contents).
* `out-arg`: Propagate information from the argument to the access graph rooted
  at an argument of the method. The argument is specified by position under the key `argnum`.
  The fields of the access graph are specified using the `fields` key as described above.
  
#### Extends Annotations
If you want the summaries specified for a super class to be applied to subclasses, include the dictionary:

```
extends: $CHILD
parents: [$PARENT1, $PARENT2, ...]
```

Legato will automatically copy the summary definitions for every method defined in `$PARENT1`, `$PARENT2`, etc.
into the class `$CHILD`.

#### Short Hands

As a convenience, Legato allows specifying summaries for multiple methods at once using
a subset of Staccato's propagation rules. To use a rule, include the singleton dictionary

```
rule: $RULE
```

or simply include the string `$RULE` in the list of summaries.

Propagation rules by default select sets of method to perform `RETURN` propagation.
However, the following rules may be prefixed by `^`, `~`, `=`, or `*`
to perform `RECEIVER`, `FLUENT`, `IDENTITY`, or `HAVOC` propagation respectively.

* `<t1,t2>`: Select all methods in reference type `t1` that returns values of type `t2`. `t2` may be
  a reference or a primitive type.
* `<t1:id>` Select all methods defined in reference type `t1` with exactly the name `id`.
* `<t1:id*>` Select all methods whose name is prefixed with `id` in reference type t1. If `id` is the
  empty string, select all methods in `t1`.
  
In addition, Legato supports package level selection. The rule `@package.name:id*` indicates that
all methods with names prefixed by `id` in classes defined in `package.name` should perform `RECEIVER` propagation.
The rule `==package.name:id*` indicates that all methods with names prefixed by `id` in `package.name` should
perform `IDENTITY` propagation.

### A (Brief) Guide to Servlet Definitions

Marshalling the correct Legato arguments for non-trivial applications
can be challenging. To help analyze web applications, Legato includes
several scripts for setting up the analysis of servlet based
applications.

By convention each application to be analyzed should be given its own directory.
This directory must at least contain:

* A servlet definition file named `servlet_def.yml`
* A resource definition file named `resources.yml`

The resource definition file must be a configuration file for the yaml
file, static, or hybrid resolvers.  The servlet definition file is an
extension of the [simple-servlet configuration
format](https://github.com/uwplse/simple-servlet). The global variables referenced
in the simple-servlet configuration may be specified in the `util/legato_env.py` module.

The folder may also contain:

* `ignore.list`: A file containing a list of methods to ignore during analysis
* `propagation.yml`: A summary file to use when analyzing the servlet.

Provided the above conditions are met, the the servlet may be analyzed by simply
running `util/analyze_servlet.py path/to/dir/servlet_def.yml`. To record
the analysis results, add the `--record` flag, which will instruct legato to write
the report and report site information to `path/to/dir/reports.yml` and `path/to/dir/site.yml`
respectively.

Finally, after analysis, the classifier may be run via the wrapper script:
`util/easy_classify.py path/to/dir/servlet_def.yml`. Any options to the classifier (see below)
may be passed to `easy_classify.py` _after_ the servlet definition path.

## Using the Classifier

The classifier is an interactive script for classifying reports produced by runs of Legato.
The classifier should be run as:

`python util/classify_results.py <options> path/to/reports.yml path/to/classify.yml path/to/site.yml path/to/sources*`

Where `reports.yml` and `site.yml` are the files produced by Legato, and `classify.yml` is the file in which
the classification results are stored. `path/to/sources` are zero or more paths to the directories containing
the source for the application. The source directory must have the layout expected by the Java compiler, i.e.,
if the source for the class `foo.bar.Baz` is in the source tree at `path/to/source/dir`, then the source file for `foo.bar.Baz` must
appear in `path/to/source/dir/foo/bar/Baz.java`.

The options supported by the classifier are as follows:


| Option                   | Usage                                                                                                                                                      |
| ---                      | ---                                                                                                                                                        |
| `--review`               | By default, the classifier does not show reports that already have a classification. This flag overrides that behavior and shows all reports.              |
| `--classification=<arg>` | One of **fp**, **tp**, and **b**, to focus reviews only on reports classified as false positives (fp), true positives (tp) or suspected analysis bugs (b). |
| `--tag=<arg>`            | Further restricts review only to those reports tagged with `<arg>`                                                                                         |

Each report is printed according to whether the report is for a lost static field, inconsistent value, or
inconsistent flow. The interface is driven by user input. At the top level, the prompt asks for
a classification. Input '?' to get help on the options. Before classification, you may "drill down"
on each report, by typing `d + ENTER` (drill down on static fields is not supported).

If multiple resources flow to an option, then you will first be
prompted to view which flow you wish to examine. Usually, this will be
the resource reported as inconsistent, which is included in the report
output. After making a selection, you will be presented with either two flow functions
(if an inconsistent flow) or the incoming inconsistent values (for an inconsistent value).
You may choose which flow you wish to examine by entering `1`, `2`, etc. To return to the top-level,
input `r`.

Some flows have branching, as described in Section 4.2. When examining a flow with branching, the tool will show
the flow up to the branching point, and then ask for you to choose a branch. You may return up one level
by entering `r`. To return to the top level, enter `q`.

Finally, when displaying a flow, the classifier outputs the source
code surrounding the line on which the resource access/method
call/synchronization point occurred. It is possible this source
is not available, in which case the tool will print `!!NO LINE!!`. Information
about the site may still be recovered by using the raw site information
option at the top level and entering the relevant site identifier.
