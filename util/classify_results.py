import sys, yaml, os.path, parse, tag_manager, argparse, itertools
from report_parser import LegatoReport
from clustering import *
from termcolor import colored

review = False

def print_path(p, start, end = 0):
    it = start
    while it >= end:
        if p[it][0] == 'P' or p[it][0] == 'L':
            print " +++ Symbolic input"
        else:
            try:
                tags.print_tag(p[it][1])
            except:
                print p[it]
                raise
        it -= 1

def print_whole_path(p):
    print_path(p, len(p) - 1, 0)

def enum_witnesses_loop(tr, curr, accum):
    rec = False
    for l in tr:
        if type(l) == list:
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

def elaborate_flow(s, all_flows = False):
    paths = enumerate_witnesses(s)
    if not all_flows:
        print "Representative flow:"
        path = paths[0]
        print_whole_path(path)
    else:
        for i in range(0, len(paths)):
            print "Flow %d of %d" % (0, len(paths) - 1)
            print_whole_path(path)
    print "Done"

def drill_down_value_prop(cluster, prop):
    print "Incoming values for",prop
    by_unit = {}
    for (k,v) in cluster.failing.iteritems():
        if prop not in v:
            continue
        by_unit[k] = v[prop]
    to_fail = by_unit[cluster.units[0]]
    for other in cluster.units[1:]:
        if to_fail != by_unit[other]:
            print ":("
    fail = to_fail.items()
    while True:
        for it in range(0, len(fail)):
            print " - (%d) %s ==> %s" % ((it + 1,) + fail[it])
        a = raw_input("Elaborate flow [1 - %d/r]: " % (len(fail)))
        if a == "r":
            return
        try:
            sel = int(a) - 1
        except ValueError:
            continue
        if sel not in range(0, len(fail)):
            continue
        print "Flow for %s" % fail[sel][0]
        elaborate_flow(fail[sel][1])

def drill_down_value(cluster):
    properties = list(cluster.failing_props)
    if len(cluster.failing_props) == 1:
        drill_down_value_prop(cluster, properties[0])
        return
    while True:
        for i in range(0, len(properties)):
            print "- (%d) %s" % (i + 1, properties[i])
        a = raw_input("Select a property to drill down [1-%d/r]: " % len(properties))

        if a == "r":
            return
        try:
            sel = int(a) - 1
        except ValueError:
            continue
        if sel not in range(0,len(properties)):
            continue
        drill_down_value_prop(cluster, properties[sel])

class PierceTheHeavens(Exception):
    pass

def drill_down_function(fn):
    while True:
        if type(fn[0]) == list:
            print "Join point"
            # get the tag of the head of the first path
            tags.print_tag(fn[0][0][1])
            if len(fn) < 10:
                for i in range(0, len(fn)):
                    print " - (%d) Branch %d" % (i, fn[i][0][2])
                sel = raw_input("Select branch [0-%d/r/q]: " % (len(fn) - 1))
                if sel == 'r':
                    return
                elif sel == 'q':
                    return PierceTheHeavens()
                try:
                    b_num = int(sel)
                except ValueError:
                    continue
                if b_num not in range(0, len(fn)):
                    continue
                drill_down_function(fn[b_num])
            else:
                print "Megamorphic branching:",len(fn)
        elif type(fn[-1]) == list:
            print "Path to join"
            print_path(fn, len(fn) - 2, 0)
            print "Next join point"
            tags.print_tag(fn[-1][0][1])
            sel = raw_input("Continue [y/n/q]? ")
            if sel == "q":
                raise PierceTheHeavens()
            elif sel == "y":
                drill_down_function(fn[-1])
            elif sel == "n":
                return
        else:
            print "Single path"
            print_whole_path(fn)
            return

def drill_down_flow_function(cluster, prop):
    while True:
        str_table = [ cluster.report.blob["fst-string"],cluster.report.blob["snd-string"]]
        print "- (1) First function:",cluster.report.blob["fst-string"]
        print "- (2) Second function:",cluster.report.blob["snd-string"]
        raw_sel = raw_input("Elaborate which function for property %s [1/2/f/s/r]? " % prop)
        if raw_sel == "f":
            if prop in cluster.report.inputs[0]:
                elaborate_flow(cluster.report.inputs[0][prop])
            continue
        elif raw_sel == "s":
            if prop in cluster.report.inputs[1]:
                elaborate_flow(cluster.report.inputs[1][prop])
            continue
        elif raw_sel == "r":
            return

        try:
            sel = int(raw_sel)
        except ValueError:
            continue
        if sel != 1 and sel != 2:
            continue

        multi_tree = cluster.report.inputs[sel - 1]
        if prop not in multi_tree:
            print "No %s in %s" % (prop, str_table[sel-1])
            continue

        try:
            drill_down_function(multi_tree[prop])
        except PierceTheHeavens:
            continue    

def drill_down_flow(cluster):
    l = list(cluster.collect_props())
    if len(l) == 1:
        drill_down_flow_function(cluster, l[0])
        return

    while True:
        for i in range(0, len(l)):
            print " - (%d) %s" % (i+1, l[i])
        a = raw_input("Select a property [1-%d/r]: " % len(l))
        if a == "r":
            return
        try:
            sel = int(a) - 1
        except ValueError:
            continue
        if sel not in range(0, len(l)):
            continue
        prop = l[sel]
        drill_down_flow_function(cluster, prop)

def drill_down(cluster):
    if type(cluster) == InconsistentValue:
        drill_down_value(cluster)
    elif type(cluster) == FlowReport:
        drill_down_flow(cluster)
    else:
        print "Drilling down on",str(type(cluster)),"not supported"
        return None

def dump_blobs(report):
    for b in report.blobs:
        print yaml.dump(b)

def do_classification(cluster, classified_clusters):
    c = classified_clusters.get_classification(cluster)
    if c is not None and not do_review:
        return c
    if (by_classification is not None or by_tag is not None) and c is None:
        return (None, None)
    if by_classification is not None:
        assert c is not None
        if c[0] != by_classification:
            return c
    if by_tag is not None:
        assert c is not None
        if c[1] != by_tag:
            return c
    if type(cluster) == InconsistentValue and "context-sens" in cluster.blobs[0] and cluster.blobs[0]["context-sens"]:
        return ("fp", "cs")
    feat = generate_cluster_feature(cluster)
    if feat in auto_classification:
        return auto_classification[feat]
    if args.auto_run:
        return c or (None, None)
    cluster.print_cluster(tags)
    while True:
        resp = raw_input("In this a true report? [y/n/d/b/s/r/v/t/m/?] ")
        if resp == '':
            continue
        if resp == 'y':
            return ("tp", None)
        elif resp == 'n':
            tag = raw_input("Tag? (enter for none): ")
            return ("fp", tag or None)
        elif resp == 'd':
            drill_down(cluster)
        elif resp == 'r':
            dump_blobs(cluster)
        elif resp == "t":
            tag = raw_input("Site number? ")
            try:
                sel = int(tag)
                tags.dump_raw_tag(sel)
            except ValueError:
                pass
        elif resp == "b":
            return ("b", None)
        elif resp == "s":
            return c or (None, None)
        elif resp == "v":
            cluster.print_cluster(tags)
        elif resp == "m":
            tag = raw_input("Tag? ")
            return ("tp", tag)
        elif resp == '?':
            print "Help:"
            print "y - yes, definitely"
            print "n - no"
            print "d - drill down"
            print "b - bug in analysis"
            print "s - skip"
            print "r - view raw report blobs"
            print "v - view cluster again"
            print "t - display raw unit info"
            print "m - mark as tagged report"
            print "? - this help"
        else:
            continue

parser = argparse.ArgumentParser()


parser.add_argument("--lint", action="store_true")
parser.add_argument("--review", action="store_true")
parser.add_argument("--classification")
parser.add_argument("--tag")
parser.add_argument("--dry-run", action="store_true")
parser.add_argument("--gc", action="store_true")
parser.add_argument("--gen-features")
parser.add_argument("--auto-features")
parser.add_argument("--auto-run", action="store_true")

parser.add_argument("report_file")
parser.add_argument("classify")
parser.add_argument("site_file")
parser.add_argument("source_sites", nargs="*")

args = parser.parse_args()

do_review = args.review
by_tag = args.tag
by_classification = args.classification

if not do_review and (by_tag is not None or by_classification is not None):
    print "Classification and tag selectors only make sense in review mode"
    sys.exit(1)

with open(args.report_file, 'r') as f:
    reports = build_clusters(yaml.load(f))

tags = tag_manager.load_tags(args.site_file, args.source_sites)

methods = {}

auto_classification = {}
if args.auto_features is not None:
    with open(args.auto_features, "r") as r:
        auto_classification = yaml.load(r)

if os.path.exists(args.classify):
    with open(args.classify, 'r') as f:
        classified_clusters = parse_classify_blob(yaml.load(f))
else:
    classified_clusters = parse_classify_blob([])

def classify_loop():
    for cluster in reports.iter_non_heap():
        (cls, tag) = do_classification(cluster, classified_clusters)
        if cls is not None:
            classified_clusters.add_classification(cluster, cls, tag)

    for h_cluster in reports.heap_reports.itervalues():
        print "!!! WARNING FOUND HEAP CLUSTER"
        classified_clusters.add_classification(h_cluster, "fp", "heap")



def generate_feature_classification(out_file):
    if os.path.exists(out_file):
        with open(out_file, 'r') as curr_out:
            generated_features = yaml.load(curr_out)
    else:
        generated_features = {}
    ambiguous_features = set()
    for cluster in reports.iter_non_heap():
        (cls, tag) = do_classification(cluster, classified_clusters)
        assert cls is not None
        feature = generate_cluster_feature(cluster)
        if feature not in generated_features:
            generated_features[feature] = (cls, tag)
        elif generated_features[feature] != (cls, tag):
            print feature, "is ambiguous", (cls,tag), generated_features[feature]
            ambiguous_features.add(feature)
    with open(out_file, "w") as f:
        yaml.dump(generated_features, f)

def generate_cluster_feature(cluster):
    if type(cluster) == FlowReport:
        if cluster.report.blob["failing"] is not None:
            props = cluster.report.blob["failing"]
        else:
            props = cluster.collect_props()
        prop_vec = tuple(sorted(props))
        raw_target = tags.store[cluster.blobs[0]["target-num"]]["blob"]
        import re
        unit_context = re.sub("\d", "", raw_target["unit-string"])
        return (cluster.key[1], cluster.key[0], prop_vec, unit_context)
    elif type(cluster) == LostStaticReport:
        return cluster.key
    else:
        assert type(cluster) == InconsistentValue
        return (cluster.method, len(cluster.units), tuple(sorted(cluster.failing_props)))

def lint_classification():
    pass

def garbage_collect():
    to_return = Classification([])
    for cluster in reports:
        cls = classified_clusters.get_classification(cluster)
        if cls is None:
            continue
        to_return.add_classification(cluster, cls[0], cls[1])
    return to_return

if args.lint:
    lint_classification()
    sys.exit(0)
elif args.gc:
    classified_clusters = garbage_collect()
elif args.gen_features is not None:
    generate_feature_classification(args.gen_features)
    sys.exit(0)
else:
    try:
        classify_loop()
    except (EOFError, KeyboardInterrupt) as e:
        print ""
        pass
    except:
        if not args.dry_run:
            classified_clusters.write(args.classify)
        raise

if not args.dry_run:
    classified_clusters.write(args.classify)
