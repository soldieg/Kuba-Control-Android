"""Check that the classes referenced by the local JieLi AAR are defined in the APK."""
import struct
import sys
from zipfile import ZipFile

REQUIRED = {
    b"Landroidx/collection/ArrayMap;",
    b"Landroidx/core/app/ActivityCompat;",
}


def defined_required_classes(dex):
    (strings, types, classes) = (struct.unpack_from("<II", dex, offset)
                                 for offset in (56, 64, 96))
    for index in range(classes[0]):
        type_index = struct.unpack_from("<I", dex, classes[1] + 32 * index)[0]
        string_index = struct.unpack_from("<I", dex, types[1] + 4 * type_index)[0]
        offset = struct.unpack_from("<I", dex, strings[1] + 4 * string_index)[0]
        while dex[offset] & 0x80:
            offset += 1
        descriptor = dex[offset + 1:dex.index(0, offset + 1)]
        if descriptor in REQUIRED:
            yield descriptor


with ZipFile(sys.argv[1]) as apk:
    found = {descriptor for path in apk.namelist()
             if path.startswith("classes") and path.endswith(".dex")
             for descriptor in defined_required_classes(apk.read(path))}
if missing := REQUIRED - found:
    sys.exit("Missing SDK classes in APK: " + ", ".join(c.decode() for c in sorted(missing)))
print("JieLi SDK AndroidX dependencies defined in APK.")
