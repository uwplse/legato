import yaml, os.path
from string import Template as Tmpl

def _recursive_subst(t, m):
    if type(t) == str:
        return Tmpl(t).safe_substitute(m)
    elif type(t) == tuple:
        return tuple(map(lambda v: _recursive_subst(v, m), t))
    elif type(t) == list:
        return map(lambda v: _recursive_subst(v, m), t)
    elif type(t) == dict:
        new_dict = {}
        for (k,v) in t.iteritems():
            new_dict[_recursive_subst(k,m)] = _recursive_subst(v,m)
        return new_dict
    else:
        return t

def load(f_name):
    from legato_env import SOURCE_DIR, TOMCAT_PATH, TOMCAT_BIN
    with open(f_name, 'r') as f:
        to_subst = list(yaml.load_all(f))
    subst_env = { "source_dir": SOURCE_DIR, 
                  "tomcat_path": TOMCAT_PATH,
                  "tomcat_bin": TOMCAT_BIN,
                  "this_dir": (os.path.dirname(f_name) or ".") }
    if len(to_subst) == 2:
        subst_env.update(_recursive_subst(to_subst[1], subst_env))
    return _recursive_subst(to_subst[0], subst_env)
