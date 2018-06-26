from pyparsing import Literal, Word, Keyword, ZeroOrMore, alphanums, delimitedList, Group, Optional, Suppress, OneOrMore, Forward, nums, StringEnd, FollowedBy, StringStart

package_or_class = Word(alphanums + "$" + "_")
class_name = delimitedList(package_or_class, delim=".", combine = True)

base_type = Keyword("void") ^ Keyword("int") ^ Keyword("long") ^ \
               Keyword("char") ^ Keyword("double") ^ Keyword("short") ^ \
               Keyword("byte") ^ Keyword("boolean") ^ class_name

full_type = base_type + ZeroOrMore(Literal("[]"))
full_type.setParseAction(lambda toks: "".join(toks))

method_name = package_or_class ^ Keyword("<init>")

arg_types = Group(Optional(delimitedList(full_type)))

sig_grammar = Literal("<").suppress() + base_type("defining_class") + Literal(":").suppress() + full_type("return_type") + method_name("method_name") + Literal("(").suppress() + arg_types("args") + Literal(")>").suppress()

field_with_type = package_or_class("field_name") + Literal("(").suppress() +  \
                  full_type("field_type") + Literal(")").suppress()

contents = Keyword("<<contents>>") + Suppress("(") + full_type("content_type") + Suppress(")")

all_sub = Literal("*(Any_subtype_of_java.lang.Object)")

class Field(object):
    def __init__(self, name, ty):
        self.name = name
        self.ty = ty
    def is_array(self):
        return False
    def is_contents(self):
        return False
    def is_all_sub(self):
        return False
    def __str__(self):
        return self.name + "(" + self.ty + ")"
    def __repr__(self):
        return str(self)
    def __hash__(self):
        return hash((self.name, self.ty))
    def __eq__(self, other):
        if type(other) != Field:
            return False
        return self.name == other.name and self.ty == other.ty

class ArrayField(Field):
    def __init__(self, name, ty):
        Field.__init__(self, name, ty)
    def is_array(self):
        return True

class Contents(Field):
    def __init__(self, name, ty):
        Field.__init__(self, name, ty)
    def is_contents(self):
        return True

class AllSub(object):
    def __init__(self):
        pass
    def is_all_sub(self):
        return True
    def __str__(self):
        return "*"
    def __repr__(self):
        return "*"

def field_cb(tok):
    if tok[0] == "array":
        return ArrayField(tok[0], tok[1])
    return Field(tok[0], tok[1])


def all_cb(tok):
    return AllSub()

def contents_cb(tok):
    return Contents(tok[0], tok[1])

field_element = field_with_type.addParseAction(field_cb) ^ contents.addParseAction(contents_cb) ^ all_sub.addParseAction(all_cb)

identifier = package_or_class

field_list = Suppress("[") + delimitedList(field_element) + Suppress("]")
ag_grammar = Optional(identifier)("base_id") + Optional(field_list)("fields")

site_identifier = Word(nums).addParseAction(lambda tok: int(tok[0]))

sync_node = (Literal("{s").addParseAction(lambda tok: "SYNC") + site_identifier + Suppress("}"))
transition_node = (Suppress("{") + Literal("t").addParseAction(lambda tok: "TR") + site_identifier + Suppress(",") + site_identifier + Suppress("}"))
call_node = (Literal("{").addParseAction(lambda tok: "C") + site_identifier + Suppress("}"))
compressed_node = (Suppress("{") + Literal("c").addParseAction(lambda tok: "CR") + site_identifier + Suppress(",") + delimitedList(site_identifier, delim = ";") + Suppress("}"))

integer = Group(Optional("-") + Word(nums)).addParseAction(lambda tok: "".join(tok[0].asList()))

def parse_numbered(tok):
    tok = tok[0].asList()
    return (int(tok[0]), int(tok[1]))

prime_entry = Word(nums).addParseAction(lambda tok: (int(tok[0]), 1)) ^ \
              Group(Word(nums) + Suppress(":") + integer).addParseAction(parse_numbered)

prime_list = Group(delimitedList(prime_entry, delim=",")).addParseAction(lambda tok: tok.asList())

param_node = (Literal("P{").addParseAction(lambda tok: "P") + prime_list + Suppress("}")).addParseAction(lambda toks: (toks[0], toks[1]))

lambda_node = Literal("L").addParseAction(lambda tok: ("L",))

primeable_node = sync_node ^ transition_node ^ call_node ^ compressed_node
prime_token = Group(primeable_node + ZeroOrMore(Literal("'")).addParseAction(lambda toks: len(toks))).addParseAction(lambda toks: tuple(*toks))
node_token = prime_token ^ param_node ^ lambda_node

node_choice = Forward()
transitive = Group(Suppress("(") + delimitedList(Group(OneOrMore(node_choice)), delim="|") + Suppress(")"))
node_choice <<= (transitive ^ node_token)

plain_string = node_token + ZeroOrMore(node_choice)

rec_tree = StringStart() + (plain_string ^ transitive.copy().addParseAction(lambda t: t[0])) + StringEnd()
