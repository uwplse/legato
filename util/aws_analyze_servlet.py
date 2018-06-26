import yaml, subprocess, os.path, sys

this_dir = os.path.dirname(sys.argv[0])
servlet_root = os.path.dirname(sys.argv[1])
analyze_servlet_script = os.path.join(this_dir, "analyze_servlet.py")

subprocess.check_call(["python", analyze_servlet_script, "--quiet", "--very-quiet", "--record", sys.argv[1]])

if os.path.exists(os.path.join(servlet_root, "_timeout.yml")):
    with open(sys.argv[2], 'w') as out:
        yaml.dump({"timeout": True}, out)
else:
    site_loc = os.path.join(servlet_root, "sites.yml")
    report_loc = os.path.join(servlet_root, "reports.yml")
    assert os.path.exists(site_loc)
    assert os.path.exists(report_loc)
    with open(site_loc, 'r') as site_f:
        site_blob = yaml.load(site_f)
    with open(report_loc, 'r') as report_f:
        report_blob = yaml.load(report_f)
    with open(sys.argv[2], 'w') as out:
        yaml.dump_all([site_blob, report_blob], out)
