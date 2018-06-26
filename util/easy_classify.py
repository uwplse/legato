import os, servlet_def, os.path, sys

classify_script = os.path.join(os.path.dirname(sys.argv[0]), "classify_results.py")
eval_dir = os.path.dirname(sys.argv[1])
serv_def = servlet_def.load(sys.argv[1])

args = [ "python", classify_script ] + sys.argv[2:] + [
    os.path.join(eval_dir, "reports.yml"),
    os.path.join(eval_dir, "classify.yml"),
    os.path.join(eval_dir, "sites.yml")
] + serv_def.get("app_source_dir", [])

os.execvp("python", args)
