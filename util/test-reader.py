import sys, yaml, os, os.path
sys.path.append(os.path.realpath(os.path.dirname(sys.argv[0])))
import tag_manager
from report_parser import LegatoReport
import pyparsing

def collect_access(tree):
    if type(tree) == pyparsing.ParseResults:
        to_ret = set()
        for l in tree:
            to_ret |= collect_access(tree[-1])
        return to_ret
    elif type(tree) == tuple:
        assert tree[0] == 'C'
        return set([tree[1]])




def enum_witnesses_loop(tr, curr, accum):
    rec = False
    for l in tr:
        if type(l) == pyparsing.ParseResults:
            rec = True
            enum_witnesses_loop(l, list(curr), accum)
        elif type(l) == tuple:
            assert not rec
            curr.append(l)
    if not rec:
        accum.append(curr)

def enumerate_witnesses(tr):
    accum = []
    enum_witnesses_loop(tr, [], accum)
    return accum

def strip_branch(p):
    if p[0] == "TR":
        return (p[0], p[1], p[3])
    else:
        return p

def strip_tree(path):
    return [ strip_branch(n) for n in path ]

class InputTree(object):
    def __init__(self, tr):
        self.paths = enumerate_witnesses(tr)
    def get_head(self):
        return self.paths[0][0]
    def get_first_path(self):
        return self.paths[0]

def print_path(p, start, end = 0):
    it = start
    while it >= end:
        if p[it][0] == 'P':
            print " +++ Symbolic input"
        else:
            try:
                tags.print_tag(p[it][1])
            except:
                print p[it]
                raise
        it -= 1

def dump_path(p1, p2):
    i = len(p1) - 1
    j = len(p2) - 1
    has_suff = False
    while i >= 0 and j >= 0 and p1[i] == p2[j]:
        has_suff = True
        i-=1
        j-=1
    if has_suff:
        print "Common call suffix:"
        print_path(p1, len(p1) - 1, i + 1)
        print "Divergence as follows:"
    print ">>> FLOW 1"
    print_path(p1, i)
    print ">>> FLOW 2"
    print_path(p2, j)

def explain_different_paths(trees):
    tree_abs = [ InputTree(tree) for tree in trees ]
    curr_head = tree_abs[0].get_head()
    i = 1
    while i < len(tree_abs):
        if tree_abs[i].get_head() != curr_head:
            dump_path(tree_abs[0].get_first_path(), tree_abs[i].get_first_path())
            break
        i += 1
        

def explain_conflict(trees):
    explain_different_paths(trees)

def explain_report(rep):
    print ">> Report found at", tags.location_str(rep.target_tag)
    tags.print_tag(int(rep.target_tag), suppress_header = True)
    print "Target value: " + rep.target_fact
    print " - properties: " + str(rep.failing)
    for p in rep.failing:
        print ">>> Begin dump for:",p
        involved_trees = [ i[p] for i in rep.inputs if p in i ]
        explain_conflict(involved_trees)

    print " >>> --- End report --- <<<\n\n"


src_path = sys.argv[1]
sites = sys.argv[2]
report = sys.argv[3]

tags = tag_manager.load_tags(src_path, sites)

with open(report, 'r') as f:
    reports = yaml.load(f)

for raw_rep in reports:
    rep = LegatoReport(raw_rep)
    if rep.failing is not None and rep.report_type == "value":
        explain_report(rep)
        break
