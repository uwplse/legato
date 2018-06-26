import os.path, yaml, subprocess, servlet_def, legato_env

LEGATO_CLASS = "edu.washington.cse.instrumentation.analysis.Legato"

class LegatoParams(object):
    def __init__(self, soot_class_path, analysis_class_path, resource_string, \
                 propagation_files, routing_file, include_exclude, main_class, resource_file, \
                 evaluation_dir, ignore_file, use_cha):
        self.soot_class_path = soot_class_path
        self.analysis_class_path = analysis_class_path
        self.resource_string = resource_string
        self.propagation_files = propagation_files
        self.routing_file = routing_file
        self.include_exclude = include_exclude
        self.main_class = main_class
        self.resource_file = resource_file
        self.evaluation_dir = evaluation_dir
        self.ignore_file = ignore_file
        self.extra_ops = None
        self.use_cha = use_cha
    def add_extra_ops(self, extra):
        if self.extra_ops is None:
            self.extra_ops = extra
        else:
            self.extra_ops += "," + extra
    def to_base_cmd(self, *args, **kwargs):
        cmd = ["java", "-Xmx10g", "-ea"]
        for a in args:
            cmd.append(a)
        for kv in kwargs.iteritems():
            cmd.append("-D%s=%s" % kv)
        cmd += ["-cp", self.analysis_class_path, LEGATO_CLASS, '', self.soot_class_path, self.propagation_files, self.resource_string, self.main_class, '-r', self.routing_file, "-e", self.include_exclude ]
        if self.use_cha:
            cmd.append("-h")
        analysis_ops = "enabled:true"
        if self.ignore_file is not None:
            analysis_ops += ",ignore-file:" + self.ignore_file
        if self.extra_ops is not None:
            analysis_ops += "," + self.extra_ops
        cmd.append(analysis_ops)
        return cmd
        
def get_legato_parameters(root_project_dir, servlet_file):
    servlet_spec = servlet_def.load(servlet_file)

    util_dir = os.path.join(root_project_dir, "util")
    eval_dir = os.path.dirname(servlet_file)

    propagation_model = [
        os.path.join(root_project_dir, "model/jcl-common.yml"),
        os.path.join(root_project_dir, "model/collections.yml"),
        os.path.join(root_project_dir, "model/io.yml")
    ]

    prop_file = os.path.join(eval_dir, "propagation.yml")
    resource_file = os.path.join(eval_dir, "resources.yml")

    if os.path.exists(prop_file):
        propagation_model.append(prop_file)

    if not os.path.exists(resource_file):
        print "Need at least resources.yml"
        return None

    subprocess.check_call(["python", os.path.join(util_dir, "compile_servlet_model.py"), servlet_file])

    soot_class_path = ":".join([
        os.path.join(root_project_dir, "build/build-deps/javax.servlet-api-3.0.1.jar"),
        os.path.join(root_project_dir, "build/build-deps/jsp-api-2.0.jar"),
        os.path.join(root_project_dir, "simple-servlet/build/libs/simple-servlet.jar"),
        os.path.join(eval_dir, "generated"),

        legato_env.TOMCAT_PATH
    ])

    if "jsp_dir" in servlet_spec:
        soot_class_path += ":" + servlet_spec["jsp_dir"]


    if "struts" in servlet_spec:
        soot_class_path += ":" + servlet_spec["struts"]["generated_dir"]
        soot_class_path += ":" + servlet_spec["struts"]["struts_jar"]

    if "extra_libs" in servlet_spec:
        el_sect = servlet_spec["extra_libs"]
        soot_class_path += ":" + ":".join(el_sect["jars"])

    soot_class_path += ":" + servlet_spec["app_classes"]

    main_class = servlet_spec["output_package"] + ".PseudoMain"

    resource_string = "hybrid:" + resource_file

    analysis_class_path = ":".join([
        os.path.join(root_project_dir, "build/classes/eclipse"),
        os.path.join(root_project_dir, "build/build-deps/*")
    ])

    # not how you should do this

    routing_file = subprocess.check_output(['find', os.path.join(eval_dir, 'generated'), '-name', 'routing.yml']).split()[0]

    include_exclude = subprocess.check_output(['find', os.path.join(eval_dir, 'generated'), '-name', 'include.list']).split()[0]

    ignore_file = os.path.join(eval_dir, "ignore.list")
    if not os.path.exists(ignore_file):
        ignore_file = None

    return LegatoParams(soot_class_path, analysis_class_path, resource_string, 
                        ":".join(propagation_model), routing_file, include_exclude, 
                        main_class, resource_file, eval_dir, ignore_file, 
                        servlet_spec.get("use_cha", False))
