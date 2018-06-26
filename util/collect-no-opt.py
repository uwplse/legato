import sys, subprocess, os.path, yaml, tempfile, argparse
import legato_setup

parser = argparse.ArgumentParser()
parser.add_argument("-v", dest="verbose", action="store_true")
parser.add_argument("servlet_def")
parser.add_argument("out_file", nargs="?")

args = parser.parse_args()

root_dir = os.path.realpath(os.path.join(os.path.dirname(sys.argv[0]), ".."))

legato_params = legato_setup.get_legato_parameters(root_dir, args.servlet_def)

disable_opts = {
    "legato.enable-narrow": ("false", "narrow"),
    "legato.conservative-heap": ("true", "heap"),
    "legato.flow-sens": ("false", "flow")
}

output_values = {}

def run_with_opts(opts):
    out_file = tempfile.NamedTemporaryFile('r')
    cmd = legato_params.to_base_cmd(**opts)
    cmd.append("output:yaml,output-opt:" + out_file.name)
    if args.verbose:
        import pipes
        print "executing:"," ".join([ pipes.quote(s) for s in cmd ])
        subprocess.check_call(cmd)
    else:
        with open("/dev/null", "w") as out:
            subprocess.check_call(cmd, stdout = out, stderr = subprocess.STDOUT)
    rep = yaml.load(out_file)
    return len(rep)

for (k,(f_val,f_key)) in disable_opts.iteritems():
    output_values[f_key] = run_with_opts({k: f_val})

all_opts = {}
for (k,(f_val,_)) in disable_opts.iteritems():
    all_opts[k] = f_val
output_values["all"] = run_with_opts(all_opts)

if args.out_file is None:
    print yaml.dump(output_values)
else:
    with open(args.out_file, 'w') as out:
        yaml.dump(output_values, out)
