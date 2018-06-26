import sys

class StdoutOutput(object):
    def __init__(self):
        pass
    def newline(self):
        print ""
    def write(self, l):
        sys.stdout.write(l)

class CodeOutput(object):
    def __init__(self, output):
        self.output = output
        self.indent_string = ""
        self.indent_level = 0
    def newline(self):
        self.output.newline()
        return self
    def indent(self):
        self.indent_level += 1
        self.indent_string = "  " * self.indent_level
        return self
    def dedent(self):
        if self.indent_level == 0:
            raise RuntimeError("Unbalanced indentation")
        self.indent_level -= 1
        self.indent_string = "  " * self.indent_level
        return self
    def write(self, l, *args):
        self.output.write(self.indent_string)
        self.output.write(l % tuple(args))
        self.output.newline()
        return self

    def write_all(self, *lines):
        for l in lines:
            self.write(l)
        return self
