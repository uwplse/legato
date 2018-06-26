import sys, yaml, subprocess, os, tempfile, argparse

parser = argparse.ArgumentParser()
parser.add_argument("--iter", type=int, default=1)
parser.add_argument("--force-list", action="store_true")
parser.add_argument("--skip-record", action="store_true")
parser.add_argument("spec", type=str)
parser.add_argument("output", nargs='?')

args = parser.parse_args()

import legato_setup
root_project_dir = os.path.join(os.path.dirname(sys.argv[0]), "..")

params = legato_setup.get_legato_parameters(root_project_dir, args.spec)

def run_and_collect(flag, **kwargs):
    with tempfile.NamedTemporaryFile(mode = 'r') as out_file, \
         tempfile.TemporaryFile(mode = 'rw') as out_capture:
        base_command_line = params.to_base_cmd(**kwargs)
        base_command_line.append(flag)
        base_command_line.append(out_file.name)
        import pipes
        print "executing:"," ".join([ pipes.quote(s) for s in base_command_line ])
        ret = subprocess.call(base_command_line, stdout = out_capture)
        if ret != 11 and ret != 0:
            raise Error("Command failed")
        out_capture.seek(0)
        return (out_capture.read(), out_file.read())

def do_test_run(skip_record = False):
    (out_stream, memory_blob) = run_and_collect("-m", **{"legato.record-primes": "true", "legato.record-k": "true"})
    (_, timing_blob) = run_and_collect("-t")

    import re
    max_primes = -1
    max_k = -1
    if not skip_record:
        for l in out_stream.split("\n"):
            m = re.match("^Max primes: (\d+)", l)
            if m is not None:
                max_primes = int(m.group(1))
            m = re.match("Max k-sensitivity: (\d+)", l)
            if m is not None:
                max_k = int(m.group(1))

    output = yaml.load(timing_blob)
    output["primes"] = max_primes
    output["sensitivity"] = max_k
    output["memory"] = int(memory_blob.strip())
    return output

stats = []
for i in range(0, args.iter):
    stats.append(do_test_run(args.skip_record))

to_output = stats[0] if args.iter == 1 and not args.force_list else stats

if args.output is not None:
    with open(args.output, 'w') as f:
        print >> f, yaml.dump(to_output)
else:
    print yaml.dump(to_output)
