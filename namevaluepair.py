#!/usr/bin/env python

import hexdump
import struct

def encode_name_value_pair(name, value):
    """
    Encodes a name/value pair.
    The encoded string is returned.
    """
    nameLength = len(name)
    if nameLength < 128:
        s = bytes([nameLength])
    else:
        s = struct.pack('!L', nameLength | 0x80000000)

    valueLength = len(value)
    if valueLength < 128:
        s += bytes([valueLength])
    else:
        s += struct.pack('!L', valueLength | 0x80000000)

    return s + name.encode("utf-8") + value.encode("utf-8")


if __name__ == "__main__":
    print(hexdump.dump(encode_name_value_pair("SCRIPT_FILENAME", "/usr/lib/git-core/git-http-backend")))
