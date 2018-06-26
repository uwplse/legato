import parse

class LegatoReport(object):
    def __init__(self, blob):
        self._init_from(blob)

    def _init_from(self, blob):
        self._common_init(blob)
        if blob["report-type"] == "flow":
            self._init_from_flow(blob)
        elif blob["report-type"] == "lost-static":
            self._init_from_static(blob)
        elif blob["report-type"] == "lost-heap":
            self._init_from_heap(blob)
        else:
            self._init_from_value(blob)

    def _common_init(self, blob):
        self.key = blob["key"]
        self.report_type = blob["report-type"]
        self.failing = blob["failing"] if "failing" in blob else set()
        self.container = blob["containing-method"]
        self.context_tags = blob.get("context-num", [-1])
        self.target_tag = blob["target-num"]
        self.target_unit = blob["target-unit"]
        self.target_fact = blob["target"]
        self.blob = blob

    def _init_from_flow(self, blob):
        self.inputs = [ self._parse_tree_blob(blob["fst"]),\
                        self._parse_tree_blob(blob["snd"]) ]

    def _init_from_value(self, blob):
        self.inputs = [ self._parse_tree(t) for t in blob["vals"].itervalues() ]
        self.vals = {}
        for (k,v) in blob["vals"].iteritems():
            self.vals[k] = self._parse_tree(v)

        self.dup_contexts = blob["dup-contexts"] if "dup-contexts" in blob else []

    def _init_from_static(self, blob):
        self.inputs = [ self._parse_tree(blob["value"]) ]

    def _init_from_heap(self, blob):
        self.inputs = [ self._parse_tree(blob["value"]) ]

    def _parse_tree_blob(self, tblob):
        if tblob["type"] == "pp" or tblob["type"] == "t":
            return self._parse_tree(tblob["root"])
        elif tblob["type"] == "id":
            return {"*": [('L')]}

    def _parse_tree(self, tr):
        to_ret = {}
        for (k,v) in tr.iteritems():
            if v == u'\u22a5':
                to_ret[k] = "BOT"
            else:
                to_ret[k] = parse.rec_tree.parseString(v).asList()
        return to_ret
