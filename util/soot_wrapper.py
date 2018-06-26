import servlet_def, os.path, sys, os

base_dir = os.path.realpath(os.path.join(os.path.dirname(sys.argv[0]), ".."))

cp = os.path.join(base_dir, "build/classes/eclipse") + ":" + os.path.join(base_dir, "soot-boomerang.jar") + \
     ":" + os.path.join(base_dir, "build/build-deps/*")

cmd = [
    "java",
    "-cp",
    cp,
    "-ea"
] + sys.argv[2:]

cmd.append("-allow-phantom-refs")
cmd.append("-soot-class-path")

s = servlet_def.load(sys.argv[1])

soot_cp = s["app_classes"]
if "jsp_dir" in s:
    soot_cp += ":" + s["jsp_dir"]

cmd.append(soot_cp)

print "executing"," ".join(cmd)

os.execvp("java", cmd)
