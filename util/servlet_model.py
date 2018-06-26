import yaml, xml.etree.ElementTree as ET, sys, subprocess, tempfile
import os.path
import servlet_def
import re
import urlparse
import hashlib

NS = {
    "f": "http://java.sun.com/xml/ns/j2ee"
}

class NameAllocator(object):
    def __init__(self, prefix):
        self.prefix = prefix + "_%d"
        self.counter = 0
        self.mapping = {}
    def get_name(self, key):
        if key in self.mapping:
            return self.mapping[key]
        name = self.prefix % self.counter
        self.mapping[key] = name
        self.counter += 1
        return name

class Servlet(object):
    def __init__(self, type, var_name, name, dispatcher_class, patterns):
        self.dispatcher_class = dispatcher_class
        self.type = type
        self.var_name = var_name
        self.patterns = patterns
        self.name = name

class Listener(object):
    def __init__(self, type, f_name):
        self.field_name = f_name
        self.type = type

class Filter(object):
    def __init__(self, type, var_name, chain_type, chain_name, prev_handler, prev_type):
        self.type = type
        self.var_name = var_name
        self.chain_type = chain_type
        self.chain_name = chain_name
        self.prev_handler = prev_handler
        self.prev_type = prev_type

class Event(object):
    def __init__(self, notify_name, notify_args, event_type, event_args, listener_method, tag):
        self.notify_name = notify_name
        self.notify_args = notify_args
        self.event_type = event_type
        self.event_args = event_args
        self.listener_method = listener_method
        self.tag = tag

class DependencyTrackingModel(object):
    def __init__(self, classpath):
        self.digest_files = set()
        self.class_dir = classpath

    def add_digest_file(self, f):
        if f is None:
            return
        self.digest_files.add(os.path.realpath(f))

    def parse_and_digest(self, f):
        self.add_digest_file(f)
        return ET.parse(f).getroot()

    def add_digest_class(self, cls):
        self.add_digest_file(self._find_in_classpath(cls))

    def _find_in_classpath(self, cls):
        for cd in self.class_dir.split(":"):
            f = os.path.join(cd, cls.replace(".", "/") + ".class")
            if os.path.exists(f):
                return f
        return None

    def get_digest(self):
        f = sorted(list(self.digest_files))
        m = hashlib.sha1()
        for l in f:
            m.update(l)
            m.update("\0")
            f_digest = hashlib.sha1(open(l, 'rb').read()).hexdigest()
            m.update(f_digest)
            m.update("\0")
        return m.hexdigest()

class ListenerModel(DependencyTrackingModel):
    TAG_DEF = {
        "contextAttr": (
            "javax.servlet.ServletContextAttributeEvent",
            ["String", "Object"],
            True,
            "Context"
        ),
        "context": ("javax.servlet.ServletContextEvent", [], True, ""),

        "session": ("javax.servlet.http.HttpSessionEvent", ["SimpleSession"], False),
        "sessionAttr": ("javax.servlet.http.HttpSessionAttributeListener", ["SimpleSession", "String", "Object"], False, "Session"),
        "sessionAct": ("javax.servlet.http.HttpSessionEvent", ["SimpleSession"], False),

        "request": ("javax.servlet.ServletRequestEvent", ["SimpleHttpRequest"], True),
        "requestAttr": ("javax.servlet.ServletRequestAttributeEvent", ["SimpleHttpRequest", "String", "Object"], True)
    }

    NOTIFICATIONS = {
        "notifySessionDestroyed": "session",
        "notifySessionCreated": "session",

        "notifySessionWillPassivate": "sessionAct",
        "notifySessionDidActivate": "sessionAct",

        "notifyContextAttributeAdded": "contextAttr",
        "notifyContextAttributeReplaced": "contextAttr",
        "notifyContextAttributeRemoved": "contextAttr",

        "notifySessionAttributeRemoved": "sessionAttr",
        "notifySessionAttributeReplaced": "sessionAttr",
        "notifySessionAttributeAdded": "sessionAttr",

        "notifyRequestInitialized": "request", 
        "notifyRequestDestroyed": "request", 

        "notifyRequestAttributeRemoved": "requestAttr",
        "notifyRequestAttributeAdded": "requestAttr",
        "notifyRequestAttributeReplaced": "requestAttr",

        "notifyContextInitialized": "context",
        "notifyContextDestroyed": "context"
    }


    def _read_listeners(self, f):
        for l in f.findall("f:listener", NS):
            cls = l.find("f:listener-class",NS).text

            self.add_digest_class(cls)

            self.l_chain.append(cls)

    def __init__(self, f, build_dir, class_dir):
        DependencyTrackingModel.__init__(self, class_dir)
        self.build_dir = build_dir
        self.listener_field = NameAllocator("listener")

        self.l_chain = []
        self._read_listeners(f)

    def get_model(self):
        events = []
        for (notif,tag) in self.NOTIFICATIONS.iteritems():
            if len(self.TAG_DEF[tag]) == 3:
                (event_class, event_types, add_this) = self.TAG_DEF[tag]
                remove = "notify"
            else:
                (event_class, event_types, add_this, remove_prefix) = self.TAG_DEF[tag]
                remove = "notify" + remove_prefix
            types_and_index = zip(event_types, range(0, len(event_types)))
            arg_declr = [ "%s v%d" % et for et in types_and_index ]
            args = [ "v%d" % i for i in range(0, len(event_types)) ]
            if add_this:
                args = ["this"] + args
            event_method = notif.replace(remove, "", 1)
            event_method = event_method[0].lower() + event_method[1:]
            e = Event(notif, arg_declr, event_class, args, event_method, tag)
            events.append(e)
        to_return = {
            "events": events
        }
        to_return.update(self._classify_listeners())
        return to_return

    def _classify_listeners(self):
        to_return = {"listeners": {}, "all_listeners": []}
        if len(self.l_chain) == 0:
            return to_return

        sub_proc = [
            "java", "-classpath", "{0}/classes/eclipse:{0}/build-deps/*".format(self.build_dir), 
            "edu.washington.cse.instrumentation.analysis.utils.ClassifyListeners",
            "{0}/build-deps/javax.servlet-api-3.0.1.jar:{1}".format(self.build_dir,
                                                                    self.class_dir)
        ] + self.l_chain

        output = subprocess.check_output(sub_proc)
        output_lines = output.split("\n")
        i = 0
        while i < len(output_lines):
            if output_lines[i].startswith(">>>"):
                i+=1
                break
            i += 1
        listener_tags = {}
        while i < len(output_lines) and not output_lines[i].startswith(">>>"):
            (cls, tags) = output_lines[i].split(":")
            listener_tags[cls] = set(tags.split())
            i+=1
        
        for l in self.l_chain:
            l_obj = Listener(l, self.listener_field.get_name(l))
            to_return["all_listeners"].append(l_obj)
            for t in listener_tags[l]:
                if t not in to_return["listeners"]:
                    to_return["listeners"][t] = []
                to_return["listeners"][t].append(l_obj)

        return to_return

class WebAppDefinition(DependencyTrackingModel):
    def __init__(self, job_def, build_dir):
        DependencyTrackingModel.__init__(self, job_def["app_classes"])
        web_xml = job_def.get("web_xml")

        self.output_package = job_def.get("output_package")

        self.f_chain = []

        self.pattern_order = []
        self.pattern_to_servlet = {}

        self.servlets = []

        f = self.parse_and_digest(web_xml)
        if not f.tag.startswith("{"):
            # SOOOO GOOD
            f.set("xmlns", "http://java.sun.com/xml/ns/j2ee")
            f = ET.fromstring(ET.tostring(f))

        self._read_filters(f)
        self.jsp_dir = None
        self._parse_jsp(job_def)
        self._read_servlets(job_def, f)
        self._read_error(job_def, f)
        self._listeners = ListenerModel(f, build_dir, self.class_dir)

        self.filter_names = NameAllocator("filter")
        self.servlet_names = NameAllocator("serv")
        self.dispatcher_allocator = NameAllocator("Dispatcher")
        self.filter_chain_classes = NameAllocator("FilterChain")

        self.ignored_tls = []

    def get_digest(self):
        this_digest = DependencyTrackingModel.get_digest(self)
        l_digest = self._listeners.get_digest()
        return hashlib.sha1(this_digest + "\0" + l_digest).hexdigest()

    def _read_error(self, job_def, f):
        self.error_servlets = []
        if "error_pages" in job_def:
            accum = set()
            for e_page in job_def["error_pages"]:
                if e_page in self.pattern_to_servlet:
                    accum.add(self.pattern_to_servlet[e_page])
                else:
                    # nope, we can only statically resolve error pages right now
                    return
            self.error_servlets = list(accum)

    def _read_servlets(self, job_def, f):
        jsp_servlets = set()
        ignore_serv = set(job_def.get("ignore_serv")) if "ignore_serv" in job_def else set()

        for s in f.findall("f:servlet", NS):
            servlet_name = s.find("f:servlet-name",NS).text
            if servlet_name in ignore_serv:
                continue
            if s.find('f:jsp-file',NS) is not None:
                jsp_servlets.add(servlet_name)
                continue
            serv_class = s.find("f:servlet-class",NS).text
            self.servlets.append((servlet_name, serv_class))

        for sm in f.findall("f:servlet-mapping", NS):
            serv_name = sm.find("f:servlet-name",NS).text
            if serv_name in ignore_serv or serv_name in jsp_servlets:
                continue
            patt = sm.find("f:url-pattern",NS).text
            self.pattern_order.append(patt)
            self.pattern_to_servlet[patt] = serv_name

    def _collect_jsp_classes(self, jsp_dir):
        f = subprocess.check_output(["find", jsp_dir, "-name",  "*_jsp.class"]).split()
        import os.path
        classes = []
        for c in f:
            self.add_digest_file(c)

            rel = os.path.relpath(c, jsp_dir)
            class_name = rel.replace("/", ".")[:-len(".class")]
            name = class_name
            self.servlets.append((name, class_name))

    def _parse_jsp(self, job_def):
        jsp_dir = job_def.get("jsp_dir", None)
        self.jsp_dir = jsp_dir
        if jsp_dir is not None:
            self._collect_jsp_classes(jsp_dir)

        if jsp_dir is not None and "jsp-mapping" in job_def:
            self.add_digest_file(job_def["jsp-mapping"])
            with open(job_def["jsp-mapping"], 'r') as frag_file:
                mapping_blob = frag_file.read()
            # do YOU know a better way to transform XML fragments?
            mapping_blob = "<mappings>" + mapping_blob + "</mappings>"
            mapping_tree = ET.fromstring(mapping_blob)
            for sm in mapping_tree.findall('servlet-mapping'):
                url_patt = sm.find("url-pattern").text
                servlet_name = sm.find("servlet-name").text
                if not self.has_servlet_definition(servlet_name):
                    continue
                self.pattern_order.append(url_patt)
                self.pattern_to_servlet[url_patt] = servlet_name

    def _read_filters(self, f):
        for p in f.findall("f:filter", NS):
            cls = p.find("f:filter-class", NS)
            if cls.text in self.f_chain:
                continue
            self.f_chain.append(cls.text)

    def has_servlet_definition(self, servlet_name):
        for (n,_) in self.servlets:
            if n == servlet_name:
                return True
        return False

    def add_servlet_def(self, servlet_name, servlet_class, patterns):
        self.servlets.append((servlet_name, servlet_class))
        for patt in patterns:
            self.pattern_order.append(patt)
            self.pattern_to_servlet[patt] = servlet_name

    def get_servlet_for_pattern(self, pattern):
        if pattern not in self.pattern_to_servlet:
            return None
        return self.pattern_to_servlet[pattern]

    def get_servlet_class(self, name):
        for (n, c) in self.servlets:
            if n == name:
                return c
        return None

    def remove_servlet(self, name):
        self.servlets = filter(lambda t: t[0] != name, self.servlets)
        to_delete_patterns = []
        for (patt, n) in self.pattern_to_servlet.iteritems():
            if n == name:
                to_delete_patterns.append(patt)
        for to_delete in to_delete_patterns:
            del self.pattern_to_servlet[to_delete]
            self.pattern_order.remove(to_delete)

    def get_servlets_for_class(self, cls):
        to_return = []
        for (n, c) in self.servlets:
            if c == cls:
                to_return.append(c)
        return to_return

    def add_ignored_tls(self, name):
        if name is None:
            return
        self.ignored_tls.append(name)

    def get_model(self):
        to_return = {
            "output_package": self.output_package,
            "servlet_handler": "all_handlers",
            "ignored_tls": self.ignored_tls,
            "error_servlets": self.error_servlets
        }
        to_return.update(self._listeners.get_model())
        servlets = []
        for (s_name,cls) in self.servlets:
            var_name = self.servlet_names.get_name(s_name)
            disp_name = self.dispatcher_allocator.get_name(s_name)
            servlets.append(Servlet(cls, var_name, s_name, disp_name, set()))
        to_return["servlets"] = servlets
        
        filters = []
        accum = ("AllServletsHandlers", "all_handlers")
        temp_counter = 0
        for f in reversed(self.f_chain):
            chain_name = "temp_%d" % temp_counter
            temp_counter += 1
            chain_type = self.filter_chain_classes.get_name(f)
            prev_handler = accum[1]
            prev_type = accum[0]
            filter_var = self.filter_names.get_name(f)
            filter_obj = Filter(f, filter_var, chain_type, chain_name, prev_handler, prev_type)
            accum = (prev_type, prev_handler)
            filters.append(filter_obj)
        to_return["final_handler"] = accum[1]
        to_return["filters"] = list(reversed(filters))
        return to_return
        
    def get_routing_info(self):
        routing = {"pattern_order": self.pattern_order, "mapping": {}, "router_names": {}}
        for patt in self.pattern_order:
            disp_raw = self.dispatcher_allocator.get_name(self.pattern_to_servlet[patt])
            class_name = self.output_package + ".PseudoMain$" + disp_raw
            routing["mapping"][patt] = class_name
        for (s_name, _) in self.servlets:
            disp_class = self.dispatcher_allocator.get_name(s_name)
            routing["router_names"][s_name] = self.output_package + ".PseudoMain$" + disp_class
        return routing

class ActionServlet(object):
    def __init__(self, type, action_type, action_method, action_form, pseudo_url, action_def):
        self.type = type
        self.action_type = action_type
        self.action_method = action_method
        self.action_form = action_form
        self.pseudo_url = pseudo_url
        self.action_def = action_def

class StrutsAppDefinition(WebAppDefinition):
    STRUTS_URL_PATTERN = re.compile("^(/[^?]+)\.do")

    def __init__(self, job_def, build_dir):
        WebAppDefinition.__init__(self, job_def, build_dir)
        self.build_dir = build_dir
        struts_section = job_def["struts"]
        struts_config = self.parse_and_digest(struts_section["struts_config"])

        self.ignored_actions = set(struts_section.get("ignored_actions", []))
        self.ignored_routes = set(struts_section.get("ignored_routes", []))

        self.bean_mappings = {}
        self.global_mappings = {}
        self.action_specs = {}
        
        self._parse_form_types(struts_config)
        self._parse_global_mappings(struts_config)
        self._parse_actions(struts_config)
        
        self.tiles = {}

        if "tiles_config" in struts_section:
            tile_def = struts_section["tiles_config"]
            tiles_config = self.parse_and_digest(tile_def)
            self._parse_tiles(tiles_config)

        self.generated_dir = struts_section["generated_dir"]

        self._url_allocator = NameAllocator("/legato-struts-model")
        self._tile_allocator = NameAllocator("/legato-tile-model")
        self._servlet_name_allocator = NameAllocator("LegatoActionServlet$")
        self._forward_action_allocator = NameAllocator("LegatoForward$")

    def _parse_form_types(self, struts_doc):
        for el in struts_doc.find("form-beans").findall("form-bean"):
            type_name = el.get("type")
            form_name = el.get("name")
            self.bean_mappings[form_name] = type_name

    def _parse_global_mappings(self, struts_doc):
        for f_elem in struts_doc.find("global-forwards").findall("forward"):
            f_name = f_elem.get("name")
            f_path = f_elem.get("path")
            if f_name in self.ignored_routes:
                continue
            self.global_mappings[f_name] = f_path

    def _parse_actions(self, struts_doc):
        for action in struts_doc.find("action-mappings").findall("action"):
            path = action.get("path")
            if path in self.ignored_actions:
                continue
            type = action.get("type")
            self.add_digest_class(type)
            parameter_name = action.get("parameter", None)
            form_name = action.get("name", None)
            input_name = action.get("input", None)
            forwards = []
            for forward in action.findall("forward"):
                forwards.append({"name": forward.get("name"),
                                 "path": forward.get("path") })
            self.action_specs[path] = {
                "type": type,
                "parameter_name": parameter_name,
                "form_name": form_name,
                "input_name": input_name,
                "forwards": forwards
            }

    def _parse_tiles(self, tiles_config):
        for definition in tiles_config.findall("definition"):
            def_name = definition.get("name")
            path = definition.get("path", None)
            extends = definition.get("extends", None)
            assert path is None or extends is None
            assert path is not None or extends is not None
            body_defs = {}
            for p in definition.findall("put"):
                t_path = p.get("value")
                self.add_ignored_tls(self.get_servlet_for_pattern(t_path))
                body_defs[p.get("name")] = t_path
            self.add_ignored_tls(self.get_servlet_for_pattern(path))
            if path is not None:
                self.tiles[def_name] = {
                    "path": path,
                    "name": def_name,
                    "defs": body_defs,
                    "extends": extends
                }
            else:
                extended_defs = dict(self.tiles[extends]["defs"].iteritems())
                extended_defs.update(body_defs)
                self.tiles[def_name] = {
                    "path": self.tiles[extends]["path"],
                    "name": def_name,
                    "defs": extended_defs,
                    "extends": extends
                }

    def _generate_strut_actions(self):
        with tempfile.NamedTemporaryFile() as in_yaml, \
             tempfile.NamedTemporaryFile(mode='r') as out_yaml,\
             open("/dev/null", 'w') as out:
            print >> in_yaml, yaml.dump(self.action_specs)
            in_yaml.flush()
            sub_proc = [
                "java", "-ea", "-classpath", 
                "{0}/classes/eclipse:{0}/build-deps/*".format(self.build_dir),
                "edu.washington.cse.instrumentation.analysis.utils.GenerateStrutsActions",
                self.class_dir, in_yaml.name, out_yaml.name, self.generated_dir
            ]
            subprocess.check_call(sub_proc)
            results = yaml.load(out_yaml)
        mapping = {}
        servlet_def = []
        for a in results:
            action_path = a["path"]
            key = (a["path"], a["method"], a["param_method"], a["type"])
            servlet_name = self._servlet_name_allocator.get_name(key)
            servlet_url = self._url_allocator.get_name(key)
            servlet_class = self.output_package + ".PseudoMain." + servlet_name
            self.add_servlet_def(servlet_name, servlet_class, [servlet_url])

            # what action mapping spawned this?
            a_spec = self.action_specs[action_path]
            
            form_class = None
            if a_spec["form_name"] is not None and a_spec["form_name"] in self.bean_mappings:
                form_class = self.bean_mappings[a_spec["form_name"]]

            action_form = "new %s()" % form_class if form_class is not None else "null"
            action_servlet = ActionServlet(servlet_name, a["type"], a["method"], action_form, servlet_url, a_spec)
            servlet_def.append(action_servlet)
            if a["param_method"] is None:
                assert action_path not in mapping
                mapping[action_path] = action_servlet
            else:
                mapping[action_path] = mapping.get(action_path, {})
                mapping[action_path][a["param_method"]] = action_servlet
        return (mapping, servlet_def)

    def _generate_tile_defs(self):
        # todo: generate mapping classes etc.
        to_return = {}
        tile_spec = {
            "tiles": []
        }
        base_templates = set()
        for (def_name,tile_def) in self.tiles.iteritems():
            if tile_def["extends"] is None:
                continue
            to_return[def_name] = self._tile_allocator.get_name(def_name)
            parent_name = self.get_servlet_for_pattern(tile_def["path"])
            parent_class = self.get_servlet_class(parent_name)
            base_templates.add(parent_class)
            tile_spec["tiles"].append({
                "name": def_name,
                "base": parent_class,
                "defs": tile_def["defs"]
            })
        tile_spec["base-templates"] = list(base_templates)

        with tempfile.NamedTemporaryFile() as in_yaml, \
             tempfile.NamedTemporaryFile(mode='r') as out_yaml,\
             open("/dev/null", 'w') as out:
            print >> in_yaml, yaml.dump(tile_spec)
            in_yaml.flush()
            sub_proc = [
                "java", "-ea", "-classpath", 
                "{0}/classes/eclipse:{0}/build-deps/*".format(self.build_dir),
                "edu.washington.cse.instrumentation.analysis.utils.InstrumentTileServlets",
                self.jsp_dir, in_yaml.name, out_yaml.name, self.generated_dir
            ]
            subprocess.check_call(sub_proc)
            results = yaml.load(out_yaml)
        for (k,v) in results.iteritems():
            self.add_servlet_def(k, v, [ self._tile_allocator.get_name(k)])
        return to_return

    def _find_mapping(self, url, action_mapping, tile_mapping):
        if url in tile_mapping:
            return tile_mapping[url]
        patt_match = self.STRUTS_URL_PATTERN.match(url)
        if patt_match is not None:
            path = patt_match.group(1)
            if path not in self.action_specs:
                return None
            # do we match on the method name?
            action_spec = self.action_specs[path]
            assert path in action_mapping
            if action_spec["parameter_name"] is None:
                assert action_mapping[path] is not None
                return action_mapping[path].pseudo_url
            p_name = action_spec["parameter_name"]
            path_components = urlparse.urlparse(url)
            assert path_components.path.endswith(".do")
            qs = urlparse.parse_qs(path_components.query)
            if p_name not in qs:
                assert "unspecified" in action_mapping[path]
                return action_mapping[path]["unspecified"]
            assert len(qs[p_name]) == 1
            method_name = qs[p_name][0]
            if method_name not in action_mapping[path]:
                return None
            return action_mapping[path][method_name].pseudo_url
        else:
            return url

    def _generate_forward_actions(self, mapping, tile_mapping):
        url_to_pseudo_url = {}
        for f_path in self.global_mappings.itervalues():
            url_to_pseudo_url[f_path] = self._find_mapping(f_path, mapping, tile_mapping)
        for a_spec in self.action_specs.itervalues():
            for f in a_spec["forwards"]:
                p = f["path"]
                url_to_pseudo_url[p] = self._find_mapping(p, mapping, tile_mapping)
        forward_actions = []
        for pseudo_url in url_to_pseudo_url.itervalues():
            if pseudo_url is None:
                continue
            forward_actions.append({"type": self._forward_action_allocator.get_name(pseudo_url), "url": pseudo_url })
        return (url_to_pseudo_url, forward_actions)

    def forward_for_app_path(self, app_path, url_mapping):
        if url_mapping[app_path] is None:
            return None
        return self.output_package + ".PseudoMain$" + self._forward_action_allocator.get_name(url_mapping[app_path])

    def _instrument_forwarding(self, servlet_def, url_mapping):
        instrument_spec = {}
        forward_to_class_template = {}
        for (f_name, f_path) in self.global_mappings.iteritems():
            forward_to_class_template[f_name] = self.forward_for_app_path(f_path, url_mapping)
        for s in servlet_def:
            forward_to_class = dict(forward_to_class_template.iteritems())
            action_type = s.action_type
            if type in instrument_spec:
                instrument_spec[type]["method"].append(s.action_method)
                continue
            for k in s.action_def["forwards"]:
                forward_to_class[k["name"]] = self.forward_for_app_path(k["path"], url_mapping)
            instrument_spec[action_type] = {"forwards": forward_to_class, "method": [s.action_method], "debug": s.action_def }
        with tempfile.NamedTemporaryFile() as in_yaml, \
             open("/dev/null", 'w') as out:
            print >> in_yaml, yaml.dump(instrument_spec)
            in_yaml.flush()
            sub_proc = [
                "java", "-ea", "-classpath", 
                "{0}/classes/eclipse:{0}/build-deps/*".format(self.build_dir),
                "edu.washington.cse.instrumentation.analysis.utils.InstrumentStruts",
                self.generated_dir, in_yaml.name, self.output_package + ".PseudoMain$NonDetForward", self.output_package + ".PseudoMain$UrlDispatchActionForward"
            ]
            subprocess.check_call(sub_proc)

    def get_model(self):
        name = self.get_servlet_for_pattern("*.do")
        assert name is not None
        self.remove_servlet(name)
        (mapping, servlet_def) = self._generate_strut_actions()
        tile_mapping = self._generate_tile_defs()
        # todo: see below
        (url_to_pseudo_url, forward_actions) = self._generate_forward_actions(mapping, tile_mapping)
        
        self._instrument_forwarding(servlet_def, url_to_pseudo_url)

        to_return = WebAppDefinition.get_model(self)

        to_return["action_servlets"] = servlet_def
        to_return["forward_actions"] = forward_actions
        
        return to_return
