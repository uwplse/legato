import sys, yaml, subprocess, tempfile, os
import servlet_def as sd_load, legato_env

root_project_dir = os.path.join(os.path.dirname(sys.argv[0]), "..")

class_path = ":".join([
    os.path.join(root_project_dir, "build/build-deps/javax.servlet-api-3.0.1.jar"),
    os.path.join(root_project_dir, "build/build-deps/jsp-api-2.0.jar"),
    os.path.join(root_project_dir, "simple-servlet/build/libs/simple-servlet.jar"),
    "{0}/jasper.jar".format(legato_env.TOMCAT_PATH)
])

base_dir = os.path.dirname(sys.argv[1])

output_dir = os.path.join(base_dir, "generated")

cache_file = os.path.join(output_dir, "_digest")

if not os.path.exists(output_dir):
    os.makedirs(output_dir)

servlet_def = sd_load.load(sys.argv[1])

# This has to be here because we may instrument the application classes
# (to make unspecified public)
if "struts" in servlet_def:
    struts_sec = servlet_def["struts"]
    class_path += ":" + struts_sec["generated_dir"] + ":" + struts_sec["struts_jar"]

class_path += ":" + servlet_def["app_classes"]

if "jsp_dir" in servlet_def:
    class_path += ":" + servlet_def["jsp_dir"]

if "extra_libs" in servlet_def:
    el_section = servlet_def["extra_libs"]
    class_path += ":" + ":".join(el_section["jars"])

pkg_dir = os.path.join(output_dir, servlet_def["output_package"].replace(".", "/"))
if not os.path.exists(pkg_dir):
    os.makedirs(pkg_dir)

generated = os.path.join(pkg_dir, "PseudoMain.java")

generated_routing = os.path.join(pkg_dir, "routing.yml")
if not os.path.exists(generated) or not os.path.exists(generated_routing):
    if os.path.exists(cache_file):
        os.remove(cache_file)

util_dir = os.path.dirname(sys.argv[0])
if util_dir == "":
    util_dir = "."

generate_python = os.path.join(util_dir, "generate_servlet_model.py")

if "custom_compile" in servlet_def:
    subst = {
        "class_path": class_path,
        "this_dir": base_dir,
        "root_dir": root_project_dir,
        "util_dir": util_dir,
        "servlet_file": sys.argv[1]
    }
    for (k,v) in servlet_def.iteritems():
        if type(v) == str and k not in subst:
            subst[k.replace("-","_")] = v
    with open("/dev/null", "w") as out:
        for cmd_tmpl in servlet_def["custom_compile"]:
            import string
            tmpl = string.Template(cmd_tmpl)
            cmd = tmpl.substitute(subst)
            subprocess.check_call(cmd, shell=True, stdout=out, stderr=subprocess.STDOUT)
else:
    gen_proc = subprocess.Popen(["python", generate_python, sys.argv[1], cache_file],
                     stdout = subprocess.PIPE,
                     stderr = subprocess.PIPE)
    (f,r) = gen_proc.communicate()
    if gen_proc.returncode != 0:
        print f
        print r
        raise subprocess.CalledProcessError(gen_proc.returncode, 
                                            ["python", generate_python, sys.argv[1], cache_file])
    if f.strip() != "cached":
        with open(generated, "w") as f_disk:
            f_disk.write(f)
        with open(generated_routing, "w") as r_disk:
            r_disk.write(r)

        subprocess.check_call(["javac", "-cp", class_path, "-d", output_dir, generated])

# generate include exclude

include = [
    "javax.servlet.*", "org.apache.jasper.runtime.HttpJspBase"
]
exclude = [
    "org.apache.jasper.runtime.*"
]

if "struts" in servlet_def:
    include += [
        "org.apache.struts.action.ActionServlet","org.apache.struts.action.Action", "org.apache.struts.actions.DispatchAction" 
    ]
    exclude = [
        "org.apache.struts.*"
    ]

if "extra_libs" in servlet_def:
    extra_lib_section = servlet_def["extra_libs"]
    include += extra_lib_section.get("include", [])
    exclude += extra_lib_section.get("exclude", [])


line_spec = map(lambda il: "+" + il, include) + map(lambda el: "-" + el, exclude)
generated_include_exclude = os.path.join(pkg_dir, "include.list")
with open(generated_include_exclude, "w") as f:
    for l in line_spec:
        print >> f, l
