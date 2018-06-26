import sys, os, os.path

if len(sys.argv) < 2 or sys.argv[1] == '-':
    in_stream = sys.stdin
else:
    in_stream = open(sys.argv[1], 'r')

lines = []
for l in in_stream:
    if len(l.strip()) == 0:
        continue
    lines.append(l.rstrip())
in_stream.close()

warned_methods = []

import re
def parse_sig(l):
    return re.match("^>>> Call to (<.+>) in contexts:$",l).group(1)

i = 0
while i < len(lines):
    l = lines[i]
    i+=1
    if not l.startswith(">>>"):
        continue
    method_sig = parse_sig(l)
    cs_count = 0
    graph_counts = []
    while i < len(lines) and lines[i].startswith("  + "):
        i+=1
        cs_count += 1
        graph_count = 0
        while i < len(lines) and lines[i].startswith("    - "):
            graph_count+=1
            i+=1
        graph_counts.append(graph_count)
    warned_methods.append({"sig": method_sig, "call_count": cs_count, "arg_counts": graph_counts})

import numpy
std_dev = numpy.std([ float(w["call_count"]) for w in warned_methods ])
mean = numpy.mean([ float(w["call_count"]) for w in warned_methods ])

print "The following methods are frequently called (> 1 stddev from average unsumamrized call-count)."
print "Consider providing summaries:"

for m in warned_methods:
    if m["call_count"] > mean + std_dev:
        print m["sig"]
