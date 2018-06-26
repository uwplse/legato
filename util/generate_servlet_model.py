import yaml, xml.etree.ElementTree as ET, sys, subprocess
import os.path
from code_gen import CodeOutput, StdoutOutput
import servlet_def, servlet_model
from jinja2 import Environment, FileSystemLoader

loader = FileSystemLoader(os.path.join(os.path.dirname(sys.argv[0]), "templates"))
env = Environment(loader = loader)

build_dir = os.path.join(os.path.dirname(sys.argv[0]), "../build")
job_def = servlet_def.load(sys.argv[1])

template_name = "basic_servlet.java"
model_handler = servlet_model.WebAppDefinition
if "struts" in job_def:
    template_name = "struts_servlet.java"
    model_handler = servlet_model.StrutsAppDefinition

web_app = model_handler(job_def, build_dir)
template = env.get_template(template_name)

digest = web_app.get_digest()
if len(sys.argv) > 2 and os.path.exists(sys.argv[2]):
    with open(sys.argv[2], 'r') as f:
        cached = f.read().strip()
    if cached == digest:
        print "cached"
        sys.exit(0)

model = web_app.get_model()
print template.render(model)
routing = web_app.get_routing_info()

import yaml
print >> sys.stderr, yaml.dump(routing)
    
if len(sys.argv) > 2:
    with open(sys.argv[2], 'w') as f:
        print >> f, digest
