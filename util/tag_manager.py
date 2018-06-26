from pygments import highlight
from pygments.lexers import JavaLexer
from pygments.formatters import TerminalFormatter

import pyparsing

import sys, yaml, os, os.path, parse

type_parser = pyparsing.delimitedList(parse.package_or_class,  delim=".")

jl = JavaLexer()
tf = TerminalFormatter()

class TagManager(object):
    def __init__(self, store):
        self.store = store
    def method_for_tag(self, tag):
        if tag not in self.store:
            return None
        if "method" not in self.store[tag]:
            return None
        return self.store[tag]["method"]

    def dump_raw_tag(self, tag):
        if tag not in self.store:
            print "No such tag",tag
        print self.store[tag]["blob"]

    def print_tag(self, tag, context = 3, suppress_header = False):
        if tag == -1:
            return "[[ INITIAL CONTEXT ]]"
        if tag not in self.store:
            print "!! NO SOURCE !!"
            return
        s = self.store[tag]
        self._print_line_and_context(s["container"], s["file"], s["lnum"], context, suppress_header, s.get("unit-string", None))

    def location_str(self, tag):
        if tag not in self.store:
            return "[unknown]"
        return "line %d in method %s" % (self.sanitize_line(self.store[tag]["lnum"]), \
                                         self.store[tag]["container"])

    def sanitize_line(self, l):
        return "unknown" if l is None else l

    def _print_line_and_context(self, m, f_name, lnum, n_context, suppress_header, unit_string):
        if not suppress_header:
            print ">> In method",m
        if lnum is None:
            print "!!NO LINE!!",
            if unit_string is not None:
                print unit_string
            else:
                print ""
            return

        start = max(1, lnum - n_context)
        end = lnum + n_context
        with open(f_name, 'r') as f:
            curr_l = 1
            for l in f:
                if curr_l < start:
                    curr_l += 1
                    continue
                print (" " if curr_l != lnum else ">") + str(curr_l) +":",
                print highlight(l, jl, tf),
                curr_l += 1
                if curr_l > end:
                    return

def load_tags(o,src_path):
    if type(src_path) == str:
        src_dirs = src_path.split(os.pathsep)
    else:
        src_dirs = src_path
    with open(o, 'r') as f:
        tags = yaml.load(f)
    to_return = {}
    for t in tags:
        l_num = int(t["line"]) if t["line"] is not None else None
        cont_type = t["containing-type"]
        tok = type_parser.parseString(cont_type).asList()
        if "source-file" in t and t["source-file"].find("_jsp") == -1:
            f_path = os.path.join(*(tok[:-1] + [t["source-file"]]))
        else:
            file_name = tok[-1]
            first_dollar = file_name.find("$")
            if first_dollar == -1:
                f_path = os.path.join(*tok) + ".java"
            else:
                containing_type = os.path.join(*tok[-1])
                f_path = os.path.join(containing_type, file_name[:first_dollar]) + ".java"
        for src_dir in src_dirs:
            candidate = os.path.join(src_dir, f_path)
            if os.path.isfile(candidate):
                to_return[t["tag"]] = { "lnum": l_num, "container": t["containing-method"], "file": candidate, "blob": t }
                if "method" in t:
                    to_return[t["tag"]]["method"] = t["method"]
        if t["tag"] not in to_return:
            to_return[t["tag"]] = { "lnum": None, "container": t["containing-method"], "file": None, "blob": t }
    return TagManager(to_return)

