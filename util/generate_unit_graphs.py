import sys

import os.path

this_dir = os.path.dirname(sys.argv[0])


output_dir = os.path.join(this_dir, "../src/edu/washington/cse/instrumentation/analysis/cfg")

sizes = {
    8: "byte",
    16: "short",
    32: "int",
    64: "long"
}

with open(os.path.join(os.path.dirname(sys.argv[0]), "unit_graph.tmpl"), 'r') as f:
    template_string = f.read()

import string
tmpl = string.Template(template_string)

def generate_graph(sz, prim_type):
    with open(os.path.join(output_dir, "CompactUnitGraph_%s.java" % prim_type), "w") as f:
        print >> f,tmpl.substitute(type = str(prim_type), type_size = str(sz))

for (sz, prim_type) in sizes.iteritems():
    generate_graph(sz, prim_type)
