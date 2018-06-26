import sys, yaml, subprocess, os, argparse
import legato_setup, servlet_def

parser = argparse.ArgumentParser()
parser.add_argument("--record", action="store_true")
parser.add_argument("--quiet", action="store_true")
parser.add_argument("--very-quiet", action="store_true")
parser.add_argument("spec")
parser.add_argument("extra_args", nargs="?")

args = parser.parse_args()

this_dir = os.path.dirname(sys.argv[0])
root_project_dir = os.path.join(this_dir, "..")

params = legato_setup.get_legato_parameters(root_project_dir, args.spec)
if params is None:
    sys.exit(1)

analysis_props = {}
if args.quiet:
    analysis_props["legato.log-long"] = "false"
if args.very_quiet:
    analysis_props["legato.very-quiet"] = "true"

# here we go

if args.record:
    params.add_extra_ops("track-sites:true,site-log:" + os.path.join(params.evaluation_dir, "sites.yml") + ",output:yaml,output-opt:" + os.path.join(params.evaluation_dir, "reports.yml"))

if args.extra_args is not None:
    params.add_extra_ops(args.extra_args)

command_line = ["time"] + params.to_base_cmd(**analysis_props)

import pipes
print "executing:"," ".join([ pipes.quote(s) for s in command_line ])

ret = subprocess.call(command_line)
if ret == 11 and args.record:
    with open(os.path.join(params.evaluation_dir, "_timeout.yml"), "w") as f:
        print >> f, "true"


