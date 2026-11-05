# SPDX-License-Identifier: GPL-3.0-or-later
"""Verify the configured peer advertises an Apple Bluetooth SIG Device ID."""
import re
import socket
import struct
import sys

def read_element(data, offset=0):
    header = data[offset]
    offset += 1
    kind, width = header >> 3, header & 7
    if width < 5:
        length = 1 << width
    else:
        size_width = 1 << (width - 5)
        length = int.from_bytes(data[offset:offset + size_width], "big")
        offset += size_width
    end = offset + length
    if end > len(data):
        raise ValueError("Truncated SDP element")
    if kind == 6:
        values = []
        while offset < end:
            value, offset = read_element(data, offset)
            values.append(value)
        if offset != end:
            raise ValueError("Invalid SDP sequence length")
        return values, end
    return int.from_bytes(data[offset:end], "big"), end

def apple_peer(address):
    if not re.fullmatch(r"(?:[0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}", address):
        return False
    with socket.socket(socket.AF_BLUETOOTH, socket.SOCK_SEQPACKET, socket.BTPROTO_L2CAP) as sock:
        sock.settimeout(5)
        sock.connect((address, 1))
        # PnP Information UUID, maximum 512 bytes, attributes 0x0201–0x0205.
        request = bytes.fromhex("3503191200020035050a0201020500")
        sock.sendall(struct.pack(">BHH", 6, 1, len(request)) + request)
        reply = sock.recv(1024)
    if len(reply) < 8 or reply[:3] != b"\x07\x00\x01":
        return False
    length = int.from_bytes(reply[5:7], "big")
    if len(reply) != length + 8 or reply[-1] != 0:
        return False
    records, end = read_element(reply[7:-1])
    if end != length:
        return False
    for record in records:
        fields = dict(zip(record[::2], record[1::2]))
        if fields.get(0x0201) == 0x004C and fields.get(0x0204) == 1 and fields.get(0x0205) == 1:
            return True
    return False

if __name__ == "__main__":
    try:
        sys.exit(0 if len(sys.argv) == 2 and apple_peer(sys.argv[1]) else 1)
    except (OSError, ValueError, IndexError, TypeError, RecursionError):
        sys.exit(1)
