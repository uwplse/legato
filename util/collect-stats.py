import sys, yaml, subprocess, os
import legato_setup

this_dir = os.path.dirname(sys.argv[0])

root_project_dir = os.path.join(this_dir, "..")

params = legato_setup.get_legato_parameters(root_project_dir, sys.argv[1])

base_command_line = params.to_base_cmd()

base_command_line.append("-s")

import tempfile
out_file = tempfile.NamedTemporaryFile()
base_command_line.append(out_file.name)

subprocess.check_call(base_command_line)

with open(out_file.name, 'r') as f:
    temp_stats = yaml.load(f)

line_count = subprocess.check_output(["wc", "-l", params.resource_file]).split()[0]

temp_stats["resource-lines"] = int(line_count)

if len(sys.argv) > 2:
    with open(sys.argv[2], 'w') as f:
        yaml.dump(temp_stats, f)
else:
    print yaml.dump(temp_stats)
