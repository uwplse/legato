import xml.etree.ElementTree as ET
import sys

inputs = [
    sys.argv[1] + "/subsonic-main/src/main/webapp/WEB-INF/applicationContext-sonos.xml",
    sys.argv[1] + "/subsonic-main/src/main/webapp/WEB-INF/applicationContext-security.xml",
    sys.argv[1] + "/subsonic-main/src/main/webapp/WEB-INF/applicationContext-service.xml",
    sys.argv[1] + "/subsonic-main/src/main/webapp/WEB-INF/applicationContext-cache.xml",
#    sys.argv[1] + "/subsonic-main/src/main/webapp/WEB-INF/web.xml",
    sys.argv[1] + "/subsonic-main/src/main/webapp/WEB-INF/subsonic-servlet.xml",
]

temp_counter = 0

referenced_serv = set()

class Factory(object):
    def __init__(self, id, factoryBean, factoryMethod, arg):
        self.id = id
        self.fact = factoryBean
        self.meth = factoryMethod
        self.arg = arg
    def init(self):
        pass
    def setup(self):
        referenced_serv.add(self.fact)
        print "Object %s = %s.%s(%s);" % (self.id, self.fact, self.meth, ",".join([a.value() for a in self.arg ]))

class New(object):
    def __init__(self, id, class_name, args, props):
        self.id = id
        self.class_name = class_name
        self.args = args
        self.props = props
    def init(self):
        print "%s %s = new %s(%s);" % (self.class_name, self.id, self.class_name, ",".join([ a.value() for a in self.args ]))
    def setup(self):
        for (k,v) in self.props.iteritems():
            setter = "set" + k[0:1].upper() + k[1:]
            print "%s.%s(%s);" % (self.id, setter, v.value())

class InlineValue(object):
    def __init__(self, rep):
        self.rep = rep
    def value(self):
        return '"' + self.rep + '"'

class RefValue(object):
    def __init__(self, rep):
        assert rep is not None
        self.rep = rep
    def value(self):
        return self.rep

class List(object):
    def __init__(self, vals):
        self.vals = vals
    def value(self):
        for v in self.vals:
            if v.value() is None:
                print type(v)
        return "Arrays.asList(" + ",".join([ v.value() for v in self.vals ]) + ")"

class InlineBean(object):
    def __init__(self, name):
        self.temp_name = name
    def value(self):
        referenced_serv.add(self.temp_name)
        return self.temp_name
    



def translate_bean_def(b, b_id):
    constr = []
    props = {}
    if "class" in b.attrib:
        cls = b.get("class")
        args = []
        params = {}
        for chld in b:
            if chld.tag == "{http://www.springframework.org/schema/beans}constructor-arg":
                args.append(translate_value(chld))
            else:
                assert chld.tag == "{http://www.springframework.org/schema/beans}property"
                params[chld.get("name")] = translate_value(chld)
        bean_mapping[b_id] = New(b_id, cls, args, params)
    elif "factory-bean":
        args = []
        for chld in b:
            assert chld.tag == "{http://www.springframework.org/schema/beans}constructor-arg"
            args.append(translate_value(chld))
        bean_mapping[b_id] = Factory(b_id, b.get("factory-bean"), b.get("factory-method"), args)

def translate_value(v):
    global temp_counter
    a = v.attrib
    if "value" in a:
        return InlineValue(a["value"])
    elif "ref" in a:
        referenced_serv.add(a["ref"])
        return RefValue(a["ref"])
    elif v.tag == "{http://www.springframework.org/schema/beans}ref":
        if v.get("bean") is None:
            return RefValue(v.get("local"))
        else:
            referenced_serv.add(v.get("bean"))
            return RefValue(v.get("bean"))
    elif v.tag == "{http://www.springframework.org/schema/beans}idref":
        return RefValue(v.get("local"))
    elif v.find("{http://www.springframework.org/schema/beans}list") is not None:
        v_list = []
        for c in v.find("{http://www.springframework.org/schema/beans}list"):
            v_list.append(translate_value(c))
        return List(v_list)
    elif v.find("{http://www.springframework.org/schema/beans}value") is not None:
        return InlineValue(v.find("{http://www.springframework.org/schema/beans}value").text)
    elif v.find("{http://www.springframework.org/schema/beans}bean") is not None:
        return translate_value(v.find("{http://www.springframework.org/schema/beans}bean"))
    elif v.tag == "{http://www.springframework.org/schema/beans}bean":
        v_name = "inline_bean_" + str(temp_counter)
        temp_counter += 1
        translate_bean_def(v, v_name)
        return InlineBean(v_name)
    elif v.find("{http://www.springframework.org/schema/beans}props") is not None:
        for prp in v.find("{http://www.springframework.org/schema/beans}props").findall("{http://www.springframework.org/schema/beans}prop"):
            referenced_serv.add(prp.text)
        return InlineValue("???")
    else:
        raise RuntimeError("Failed to handle: " + ET.tostring(v))

bean_mapping = {}
for i in inputs:
    f = ET.parse(i).getroot()
    beans = f.findall("{http://www.springframework.org/schema/beans}bean")
    for b in beans:
        b_id = b.get("id")
        translate_bean_def(b, b_id)

for v in bean_mapping.itervalues():
    v.init()

for v in bean_mapping.itervalues():
    v.setup()

print set(bean_mapping.iterkeys()) - referenced_serv
