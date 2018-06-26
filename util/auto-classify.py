import yaml, sys, os, os.path
sys.path.append(os.path.realpath(os.path.dirname(sys.argv[0])))
from parse import sig_grammar

if sys.argv[1] == '-':
    summ = yaml.load(sys.stdin)
else:
    with open(sys.argv[1], 'r') as f:
        summ = yaml.load(f)

def infer_propagation(l):
    parsed_sig = sig_grammar.parseString(l["sig"])
    if parsed_sig.method_name == "<init>":
        l["target"] = "RECEIVER"
        return
    if parsed_sig.return_type == "void" and parsed_sig.method_name.startswith("set"):
        l["target"] = "RECEIVER"
        return
    if parsed_sig.return_type == "void" and len(parsed_sig.arg_types) == 0:
        l["target"] = "IDENTITY"
        return
    if parsed_sig.return_type != "void" and parsed_sig.method_name.startswith("get"):
        l["target"] = "RETURN"
        return

for i in range(0, len(summ)):
    l = summ[i]
    if type(l) is not dict:
        continue
    if "sig" not in l:
        continue
    if "target" not in l:
        continue
    if l["target"] != "???":
        continue
    infer_propagation(l)

print yaml.dump(summ, default_flow_style = False, width=float("inf"))
