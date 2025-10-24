import sys, io, builtins, traceback

_writer = None
_input_provider = None


class _Out(io.TextIOBase):
    def __init__(self, writer):
        self.writer = writer

    def write(self, s):
        if s:
            try:
                self.writer.write(str(s))
            except Exception:
                sys.__stderr__.write(s)
        return len(s)

    def flush(self):
        pass


class _In(io.TextIOBase):
    def __init__(self, provider):
        self.provider = provider

    def readline(self, *args, **kwargs):
        try:
            line = self.provider.readLine()
            return ("" if line is None else str(line)) + "\n"
        except Exception:
            return "\n"


def hook_io(writer, input_provider=None):
    global _writer, _input_provider
    _writer = writer
    _input_provider = input_provider

    sys.stdout = _Out(writer)
    sys.stderr = _Out(writer)

    if input_provider is not None:
        sys.stdin = _In(input_provider)

        def _input(prompt=""):
            if prompt:
                sys.stdout.write(str(prompt))
            return sys.stdin.readline().rstrip("\n")

        builtins.input = _input


def run_code(code: str):
    g = {"__name__": "__main__"}
    try:
        exec(code, g, g)
    except Exception:
        traceback.print_exc()
