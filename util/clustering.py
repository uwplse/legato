import sys, yaml, os.path
import parse, tag_manager
from report_parser import LegatoReport
import argparse, itertools
from termcolor import colored

class Clusters(object):
    def __init__(self):
        self.static_clusters = {}
        self.value_clusters = {}
        self.flow_reports = {}
        self.heap_reports = {}
    def __iter__(self):
        return itertools.chain(self.static_clusters.itervalues(),
                               self.value_clusters.itervalues(),
                               self.flow_reports.itervalues(),
                               self.heap_reports.itervalues())
    def iter_non_heap(self):
        return itertools.chain(self.flow_reports.itervalues(), \
                               self.value_clusters.itervalues(), \
                               self.static_clusters.itervalues())

def handle_report(rep, clus):
    if rep.report_type == "lost-static":
        import pyparsing
        static_field_gram = pyparsing.StringStart() + parse.field_with_type
        parsed = static_field_gram.parseString(rep.target_fact[1:])[0]
        if parsed not in clus.static_clusters:
            clus.static_clusters[parsed] = LostStaticReport(parsed)
        clus.static_clusters[parsed].add_lost_flow(rep.target_fact, rep.inputs, rep)
    elif rep.report_type == "lost-heap":
        key = (rep.container,rep.target_fact)
        if key not in clus.heap_reports:
            clus.heap_reports[key] = LostHeapReport(key)
        clus.heap_reports[key].add_report(rep)
    elif rep.report_type == "value":
        meth = rep.container
        key = (meth, tuple(rep.key[0]), rep.target_fact)
        if key not in clus.value_clusters:
            clus.value_clusters[key] = InconsistentValue(key, rep.context_tags)
        if rep.failing is None:
            rep.failing = []
        clus.value_clusters[key].add_inconsistent(rep.key[2], rep.vals, rep.target_tag, rep.failing, rep.blob, rep.dup_contexts)
    else:
        key = (rep.container,) + tuple(rep.key)
        flow = FlowReport(key, rep)
        clus.flow_reports[key] = flow

def parse_clusters(cluster_f):
    with open(cluster_f, 'r') as f:
        return build_clusters(yaml.load(f))

def build_clusters(reports):
    clusters = Clusters()
    for r in reports:
        try:
            rep = LegatoReport(r)
        except:
            print sys.exc_info()
            print "Failed on ", yaml.dump(r)
            sys.exit(10)
        handle_report(rep, clusters)
    return clusters


class LostStaticReport(object):
    def __init__(self, static_graph):
        self.key = (static_graph.name, static_graph.ty)
        self.graph = static_graph
        self.property_sites = {}
        self.properties = set()
        self.target_fields = {}
        self.blobs = []
    def add_lost_flow(self, target, flow_value, report):
        self.blobs.append(report.blob)
        for v in flow_value:
            for (k,tree) in v.iteritems():
                self.properties.add(k)
                if k not in self.target_fields:
                    self.target_fields[k] = set()
                self.target_fields[k].add(target)
                self.add_sites(k, tree)
    def add_sites(self, k, tree):
        assert type(tree) == list
        if type(tree[0]) == list:
            for t in tree:
                self.add_sites(k, t)
        else:
            assert type(tree[-1]) == tuple
            last = tree[-1]
            assert last[0] == "C"
            if k not in self.property_sites:
                self.property_sites[k] = set()
            self.property_sites[k].add(last[1])
    def print_cluster(self, tags):
        print "Warning!",", ".join(self.properties),"are lost into the static field",self.graph
        for p in self.properties:
            print "> Property",p
            print "Flows to the sub field(s):",",".join(self.target_fields[p])
            print "From sites"
            for s in self.property_sites[p]:
                tags.print_tag(s)

class LostHeapReport(object):
    def __init__(self, key):
        self.key = key
        self.reports = []
    def add_report(self, report):
        self.reports.append(report)

class FlowReport(object):
    def __init__(self, key, report):
        self.key = key
        self.report = report
        self.blobs = [report.blob]
    def print_cluster(self, tags):
        print "Inconsistent flow discovered for fact",self.key[3],"from source",self.key[1],"in method",self.key[0],"at"
        tags.print_tag(self.report.target_tag)
        if self.report.blob["failing"] is not None:
            props = self.report.blob["failing"]
        else:
            props = self.collect_props()
        print ">> For properties",", ".join(props)
        if "sensitivity" in self.blobs[0] and self.blobs[0]["sensitivity"]:
            print colored("NOTE:", "green", attrs=["bold"]), "this report is due to path-insensitivity"
    def collect_props(self):
        to_return = set()
        for t in self.report.inputs:
            to_return |= set(t.iterkeys())
        return to_return

class InconsistentValue(object):
    def __init__(self, key, context_tags):
        self.method = key[0]
        self.context = key[1]
        self.graph = key[2]
        self.context_tags = context_tags
        self.failing = {}
        self.tags = []
        self.units = []
        self.blobs = []
        self._saved_tag = key
        self.key = key
        self.failing_props = set()
        self.dup_contexts = set()
    def add_inconsistent(self, target, value_set, target_tag, failing, blob, dup_cont):
        if target not in self.failing:
            self.failing[target] = {}
        self._add_failing_at(value_set, failing, self.failing[target])
        self.failing_props |= set(failing)
        self.tags.append(target_tag)
        self.units.append(target)
        self.blobs.append(blob)
        self.dup_contexts |= set([ tuple(t) for t in dup_cont ])
    def _add_failing_at(self, value_set, failing, failing_at):
        for f in failing:
            if f not in failing_at:
                failing_at[f] = {}
            for (in_val,tree) in value_set.iteritems():
                if f not in tree:
                    continue
                failing_at[f][in_val] = tree[f]
                
    def print_cluster(self, tags):
        print "Report for value:",self.graph,"in",self.method,"from context:"
        for tag in self.context_tags:
            tags.print_tag(tag)
        if "predecessor" in self.blobs[0] and self.blobs[0]["predecessor"]:
            print colored("NOTE:", "green", attrs=["bold"]), "this report is due to path-insensitivity"
        print ">> For properties",", ".join(self.failing_props)
        print "At the following locations"
        for t in self.tags:
            tags.print_tag(t)
        if len(self.dup_contexts) > 0:
            print ">> This error also occurs under the following contexts:"
            for (cont, i) in zip(self.dup_contexts, range(0, len(self.dup_contexts))):
                print "Context %d" % (i+1)
                for c in cont:
                    tags.print_tag(c)


class Classification(object):
    def __init__(self, blob):
        self.blob = blob
        self._by_key = {}
        for b in blob:
            self._by_key[tuple(b["key"])] = b

    def get_classification(self, cluster):
        if cluster.key not in self._by_key:
            return None
        cluster_res = self._by_key[cluster.key]
        if type(cluster) == LostStaticReport:
            return self._classify_static(cluster_res, cluster)
        elif type(cluster) == LostHeapReport:
            return self._classify_heap(cluster_res, cluster)
        elif type(cluster) == FlowReport:
            return self._classify_flow(cluster_res, cluster)
        elif type(cluster) == InconsistentValue:
            return self._classify_value(cluster_res, cluster)
        else:
            raise RuntimeError("Unhandled report type")
    
    def _classify_value(self, cluster_res, cluster):
        if sorted(cluster.units) != cluster_res["units"]:
            return None
        for u in cluster_res["units"]:
            if not self._isomorphic_at(cluster.failing[u], cluster_res["value"][u]):
                return None
        return (cluster_res["classification"], cluster_res.get("tag", None))

    def _isomorphic_at(self, vm1, vm2):
        k1 = sorted(vm1.iterkeys())
        k2 = sorted(vm2.iterkeys())
        if k1 != k2:
            return False
        l_map = {}
        for k in k1:
            i1 = sorted(vm1[k].iterkeys())
            i2 = sorted(vm2[k].iterkeys())
            if i1 != i2:
                return False
            for i in range(0, len(i2)):
                input_val = i2[i]
                v1 = vm1[k][input_val]
                v2 = vm2[k][input_val]
                t1 = canonicalize_tree(v1)
                t2 = canonicalize_tree(v2)
                if not isomorphic_trees(t1, t2, l_map):
                    return False
        return True

    def add_classification(self, cluster, classif, tag = None):
        blob = {
            "key": cluster.key,
            "classification": classif,
        }
        if tag is not None:
            blob["tag"] = tag
        if type(cluster) == InconsistentValue:
            blob.update({
                "units": sorted(cluster.units),
                "value": cluster.failing,
            })
        elif type(cluster) == FlowReport:
            blob.update({
                "f1": cluster.report.inputs[0],
                "f2": cluster.report.inputs[1],
            })
        elif type(cluster) == LostStaticReport:
            blob.update({
                "props": cluster.properties,
                "fields": cluster.target_fields
            })
        elif type(cluster) == LostHeapReport:
            pass
        else:
            raise RuntimeError("Unhandled report type")
        self._by_key[cluster.key] = blob

    def _classify_flow(self, cluster_res, cluster):
        if self._matches_flow(cluster_res, cluster):
            return (cluster_res["classification"], cluster_res.get("tag", None))
        return None

    def _matches_flow(self, cluster_res, cluster):
        return (self._functions_match(cluster_res["f1"], cluster.report.inputs[0]) and \
                self._functions_match(cluster_res["f2"], cluster.report.inputs[1])) or\
            (self._functions_match(cluster_res["f2"], cluster.report.inputs[0]) and\
             self._functions_match(cluster_res["f1"], cluster.report.inputs[1]))

    def _functions_match(self, f1, f2):
        k1 = sorted(f1.iterkeys())
        k2 = sorted(f2.iterkeys())
        l_map = {}
        if k2 != k1:
            return False
        for k in k1:
            t1 = canonicalize_tree(f1[k])
            t2 = canonicalize_tree(f2[k])
            if not isomorphic_trees(t1, t2, l_map):
                return False
        return True

    def _classify_heap(self, cluster_res, cluster):
        if cluster_res["key"] == cluster.key:
            return (cluster_res["classification"], cluster_res.get("tag", None))
        return None

    def _classify_static(self, cluster_res, cluster):
        if cluster_res["props"] != cluster.properties or cluster_res["fields"] != cluster.target_fields:
            return None
        return (cluster_res["classification"], cluster_res.get("tag", None))

    def write(self, out_file):
        write_out = sorted(self._by_key.itervalues(), key = lambda it: it["key"])
        if type(out_file) == str:
            with open(out_file, 'w') as f:
                yaml.dump(write_out, f)
        else:
            yaml.dump(write_out, out_file)

def parse_classify(classify_f):
    with open(classify_f, 'r') as f:
        return parse_classify_blob(yaml.load(f))

def _is_sane(cls):
    for f in ["f1", "f2"]:
        t = cls[f]
        assert type(t) == dict
        for v in t.itervalues():
            if type(v) is not list:
                return False
    return True

def parse_classify_blob(b):
    to_parse = []
    for cls in b:
        if "f1" not in cls:
            to_parse.append(cls)
            continue
        if _is_sane(cls):
            to_parse.append(cls)
    return Classification(to_parse)


def canonicalize_tree(tree):
    assert type(tree) == list
    if type(tree[0]) == list:
        sub_canon = map(canonicalize_tree, tree)
        # sort by branch id, this is stable between runs of the tool
        return sorted(sub_canon, key = lambda k: k[0][1])
    if type(tree[-1]) == list:
        to_ret = list(tree)
        to_ret[-1] = canonicalize_tree(to_ret[-1])
        return to_ret
    return tree

def isomorphic_trees(t1, t2, l_map):
    assert type(t1) == list
    assert type(t2) == list
    if len(t1) != len(t2):
        return False
    if type(t1[0]) == list:
        return all(map(lambda e1,e2: isomorphic_trees(e1, e2, l_map), t1, t2))
    for i in range(0, len(t1)):
        e1 = t1[i]
        e2 = t2[i]
        if type(e1) == list:
            assert i == len(t1) - 1, "%d %s %d" % (i, str(t1), len(t1) - 1)
            return isomorphic_trees(e1, e2, l_map)
        if e1[0] != e2[0]:
            return False
        if e1[0] == "L" or e1[0] == "P":
            continue
        elif e1[0] == "TR" or e1[0] == "CR":
            k1 = (e1[1], e1[2])
            k2 = (e2[1], e2[2])
            if k1 in l_map and l_map[k1] != k2:
                return False
            elif k1 not in l_map:
                l_map[k1] = k2
        elif e1[0] == "C" or e1[0] == "SYNC":
            k1 = e1[1]
            k2 = e2[1]
            if k1 in l_map and l_map[k1] != k2:
                return False
            elif k1 not in l_map:
                l_map[k1] = k2
        else:
            raise RuntimeError("Unhandled items: %s %s" % (str(e1), str(e2)))
    return True
