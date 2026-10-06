#!/usr/bin/env python3
"""Read-only linkage/access checks against actual OEM smali, never Java API stubs.

Input provenance and Android bootclasspath compilation belong to the pinned
builder. This validates OEM/helper symbolic references, not full DEX dataflow,
Android platform implementations, native behavior or vehicle readiness.
"""
from __future__ import annotations

import argparse
from dataclasses import dataclass, field
import hashlib
import json
from pathlib import Path
import re

OWNED_PREFIXES = ("Lcom/ts/androidauto/impulse/cluster/", "Limpulse/cluster/prototype/")
PROTOCOL = "Lbr/com/redesurftank/havalshisuku/api/AaClusterProtocol;"
COMPILER_METADATA = "Lcom/android/tools/r8/annotations/LambdaMethod;"
COMPILER_METADATA_SHA256 = "580b7431e46832fc80a158ea731b7c047701383b9109606512ff5cad56d27df6"
PLATFORM_PREFIXES = ("Ljava/", "Ljavax/", "Landroid/", "Ldalvik/", "Lorg/xml/", "Lorg/w3c/")
LISTENERS = ("Lcom/google/android/projection/protocol/VideoSink$ProjectionListener;", "Lcom/google/android/projection/protocol/InputSource$InputInjector;")
TYPE = r"(?:\[*L[^\s;(){}\",]+;|\[*[VZBSCIJFD])"
MEMBER_REF = re.compile(r"(?P<owner>\[*L[^\s;(){}\",]+;|\[+[ZBSCIJFD])->(?P<name>[^\s(:=,]+)(?P<tail>\([^)]*\)" + TYPE + r"|:" + TYPE + r")")
TYPE_REF = re.compile(r"(?<![\w$])L[^\s;(){}\",=:]+;")
GAL = "Lcom/google/android/projection/protocol/GalReceiver;"
PROVIDER = "Lcom/google/android/projection/protocol/CarServiceProvider;"
INTEGRATION = "Lcom/ts/androidauto/aap/sink/GalIntegration;"
BINDER = "Lcom/ts/androidauto/projectionservice/AndroidAutoService$LinkCommandBinder;"
INTEGRATION_HELPER = "Lcom/ts/androidauto/impulse/cluster/ClusterIntegration;"
BINDER_HELPER = "Lcom/ts/androidauto/impulse/cluster/ClusterBinder;"
PAIR = f"registerImpulseClusterPair({PROVIDER}{PROVIDER})Z"
TRANSACT = "onTransact(ILandroid/os/Parcel;Landroid/os/Parcel;I)Z"
# Incoming OEM calls must also link after assembly; helper-to-OEM verification
# alone would not catch a removed registration/retirement/Surface entry point.
HOOK_CALLS = {
    (INTEGRATION, "registerCarService()V"): (INTEGRATION_HELPER, f"register({GAL}I)V"),
    (INTEGRATION, "destroy()V"): (INTEGRATION_HELPER, f"retire({GAL})V"),
    (GAL, PAIR): (INTEGRATION_HELPER, f"registerPair(Landroid/util/SparseArray;JZ{PROVIDER}{PROVIDER})Z"),
    (BINDER, TRANSACT): (BINDER_HELPER, "dispatch(Landroid/content/Context;Landroid/os/Parcel;Landroid/os/Parcel;I)Z"),
}


class VerificationError(ValueError):
    def __init__(self, errors):
        self.errors = errors
        super().__init__("\n".join(errors))


@dataclass
class Member:
    owner: str
    signature: str
    flags: frozenset[str]
    line: int


@dataclass
class Class:
    name: str
    parent: str | None
    interfaces: tuple[str, ...]
    flags: frozenset[str]
    path: Path
    sha256: str
    methods: dict[str, Member] = field(default_factory=dict)
    fields: dict[str, Member] = field(default_factory=dict)
    lines: list[tuple[int, str, Member | None]] = field(default_factory=list)


def scrub(line: str) -> str:
    """Remove comments/string contents without mistaking quoted # or -> for code."""
    result = []
    quoted = escaped = False
    for char in line:
        if quoted:
            if escaped: escaped = False
            elif char == "\\": escaped = True
            elif char == '"': quoted = False; result.append('"')
        elif char == '#': break
        elif char == '"': quoted = True; result.append('"')
        else: result.append(char)
    return "".join(result).strip()


def read_tree(root: Path) -> dict[str, Class]:
    root = Path(root)
    if not root.is_dir() or root.is_symlink():
        raise VerificationError([f"Expected a non-symlink smali directory: {root}"])
    paths = sorted(root.rglob("*"))
    if any(p.is_symlink() for p in paths):
        raise VerificationError([f"Symlink in smali tree: {root}"])
    result = {}
    for path in paths:
        if not path.is_file() or path.suffix != ".smali": continue
        raw = path.read_bytes()
        lines = [(i, scrub(s)) for i, s in enumerate(raw.decode("utf-8").splitlines(), 1)]
        declarations = [(i, s.split()[1:]) for i, s in lines if s.startswith(".class ")]
        if len(declarations) != 1 or not declarations[0][1]:
            raise VerificationError([f"Expected exactly one class declaration: {path}"])
        _, declaration = declarations[0]
        name = declaration[-1]
        if not re.fullmatch(r"L[^\s;]+;", name) or name in result:
            raise VerificationError([f"Invalid/duplicate class definition: {name}"])
        parents = [s.split()[1] for _, s in lines if s.startswith(".super ")]
        if len(parents) > 1: raise VerificationError([f"Multiple superclasses: {name}"])
        klass = Class(name, parents[0] if parents else None,
                      tuple(s.split()[1] for _, s in lines if s.startswith(".implements ")),
                      frozenset(declaration[:-1]), path, hashlib.sha256(raw).hexdigest())
        current = None
        for number, line in lines:
            if line.startswith(".method "):
                if current: raise VerificationError([f"Nested method: {name}:{number}"])
                bits = line.split()[1:]; sig = bits[-1]
                if sig in klass.methods: raise VerificationError([f"Duplicate method: {name}->{sig}"])
                current = Member(name, sig, frozenset(bits[:-1]), number)
                klass.methods[sig] = current
            elif line == ".end method":
                if current is None: raise VerificationError([f"Unmatched method end: {name}:{number}"])
                current = None
            elif line.startswith(".field "):
                bits = line.split(" = ", 1)[0].split()[1:]; sig = bits[-1]
                if sig in klass.fields: raise VerificationError([f"Duplicate field: {name}->{sig}"])
                klass.fields[sig] = Member(name, sig, frozenset(bits[:-1]), number)
            klass.lines.append((number, line, current))
        if current: raise VerificationError([f"Unclosed method: {name}"])
        result[name] = klass
    if not result: raise VerificationError([f"No smali classes: {root}"])
    return result


def owned(name):
    return name == PROTOCOL or name.startswith(OWNED_PREFIXES)


def platform(name):
    return name.startswith("[") or name.startswith(PLATFORM_PREFIXES)


def package(name):
    return name.rsplit("/", 1)[0]


def parents(name, classes):
    info = classes.get(name)
    return (() if info is None else tuple(x for x in (info.parent,) + info.interfaces if x))


def ancestors(name, classes):
    result, visiting = set(), set()
    def walk(current):
        if current in visiting: raise VerificationError([f"Inheritance cycle at {current}"])
        if current in result: return
        visiting.add(current); result.add(current)
        for parent in parents(current, classes): walk(parent)
        visiting.remove(current)
    walk(name)
    return result


def resolve(owner, signature, classes, is_method):
    """Methods search the class chain before interfaces; fields follow DEX lookup."""
    info = classes.get(owner)
    if info is None: return None
    table = info.methods if is_method else info.fields
    if signature in table: return table[signature]
    if is_method and signature.startswith(("<init>(", "<clinit>(")): return None
    if not is_method:
        seen = {owner}
        def field_search(name):
            if name in seen or name not in classes: return None
            seen.add(name); cls = classes[name]
            if signature in cls.fields: return cls.fields[signature]
            for parent in cls.interfaces + ((cls.parent,) if cls.parent else ()):
                found = field_search(parent)
                if found: return found
            return None
        for parent in info.interfaces + ((info.parent,) if info.parent else ()):
            found = field_search(parent)
            if found: return found
        return None
    chain, seen = [], set()
    current = owner
    while current in classes and current not in seen:
        seen.add(current); cls = classes[current]; chain.append(cls)
        if signature in cls.methods: return cls.methods[signature]
        current = cls.parent
    def interface_search(name):
        if name in seen or name not in classes: return None
        seen.add(name); cls = classes[name]
        member = cls.methods.get(signature)
        if member is not None and not ({"static", "private"} & member.flags): return member
        for parent in cls.interfaces:
            found = interface_search(parent)
            if found: return found
        return None
    for cls in chain:
        for parent in cls.interfaces:
            found = interface_search(parent)
            if found: return found
    return None


def receiver_register(line, method):
    opcode = line.split()[0]
    if opcode.startswith("invoke-"):
        regs = re.search(r"\{([^}]+)\}", line)
        return None if not regs else re.split(r",|\s+\.\.\s+", regs[1])[0].strip()
    if opcode.startswith(("iget", "iput")):
        parts = line.split(",")
        return parts[1].strip() if len(parts) >= 3 else None
    return None


def accessible(member, caller, owner, line, method, classes):
    flags = member.flags
    if "public" in flags or caller == member.owner: return True
    if "private" in flags: return False
    if package(caller) == package(member.owner): return True
    if "protected" not in flags or member.owner not in ancestors(caller, classes): return False
    if "static" in flags or line.startswith("invoke-super"): return True
    if "<init>(" in member.signature and line.startswith("invoke-direct"):
        return method is not None and method.signature.startswith("<init>(") and receiver_register(line, method) == "p0"
    # Check the protected receiver restriction, not merely subclass membership.
    if caller in ancestors(owner, classes): return True
    return method is not None and "static" not in method.flags and receiver_register(line, method) == "p0"


def visibility(flags):
    return 3 if "public" in flags else 2 if "protected" in flags else 0 if "private" in flags else 1


def verify(helper_root: Path, stock_root: Path) -> dict:
    helpers, stock = read_tree(helper_root), read_tree(stock_root)
    errors, metadata = [], []
    for name, cls in helpers.items():
        if name == COMPILER_METADATA:
            if cls.sha256 != COMPILER_METADATA_SHA256:
                errors.append("Compiler metadata fingerprint mismatch: " + name)
            else: metadata.append({"class": name, "sha256": cls.sha256})
        elif not owned(name): errors.append("Forbidden helper/API-stub class definition: " + name)
        if name in stock and stock[name].sha256 != cls.sha256:
            errors.append("Helper replaces a different stock class: " + name)
    if errors: raise VerificationError(errors)
    # Identical helpers in a final merged decode are permitted and not counted as OEM.
    oem_names = set(stock) - set(helpers)
    classes = dict(stock); classes.update(helpers)
    for name in helpers: ancestors(name, classes)
    refs = oem_methods = oem_fields = helper_refs = platform_refs = override_checks = 0
    for caller, cls in helpers.items():
        for parent in parents(caller, classes):
            if parent not in classes and not platform(parent): errors.append(f"Missing superclass/interface {parent} of {caller}")
            elif parent in classes and "final" in classes[parent].flags: errors.append(f"Cannot extend final class {parent}: {caller}")
        for number, line, method in cls.lines:
            where = f"{caller}:{number}"
            if line.startswith(("invoke-custom", "invoke-polymorphic")):
                errors.append(where + ": unsupported dynamic invoke; cannot prove linkage")
            for type_name in TYPE_REF.findall(line):
                if type_name not in classes and not platform(type_name):
                    errors.append(where + ": missing referenced class " + type_name)
                elif type_name in classes and "public" not in classes[type_name].flags and package(type_name) != package(caller):
                    errors.append(where + ": inaccessible referenced class " + type_name)
            for match in MEMBER_REF.finditer(line):
                refs += 1
                owner, name, tail = match["owner"], match["name"], match["tail"]
                signature = name + tail
                is_method = tail.startswith("(")
                if platform(owner) and owner not in classes:
                    platform_refs += 1; continue
                found = resolve(owner, signature, classes, is_method)
                if found is None:
                    errors.append(where + f": unresolved {'method' if is_method else 'field'} {owner}->{signature}"); continue
                if found.owner in oem_names:
                    if is_method: oem_methods += 1
                    else: oem_fields += 1
                else: helper_refs += 1
                opcode = line.split()[0] if line else ""
                instruction = opcode.startswith(("invoke-", "iget", "iput", "sget", "sput"))
                if not instruction: continue  # Annotation references do not perform an access.
                if not accessible(found, caller, owner, line, method, classes):
                    errors.append(where + f": inaccessible member {found.owner}->{signature}")
                static = "static" in found.flags
                if is_method:
                    if not opcode.startswith("invoke-"):
                        errors.append(where + ": method reference used by field instruction")
                    kind = opcode.split("/", 1)[0]
                    if (kind == "invoke-static") != static: errors.append(where + ": static/instance method kind mismatch " + signature)
                    if name == "<clinit>": errors.append(where + ": explicit class-initializer invocation")
                    if name == "<init>" and kind != "invoke-direct": errors.append(where + ": constructor requires invoke-direct")
                    if kind == "invoke-direct" and name != "<init>" and "private" not in found.flags: errors.append(where + ": invoke-direct requires constructor/private method")
                    if kind in ("invoke-virtual", "invoke-interface", "invoke-super") and "private" in found.flags: errors.append(where + ": virtual invocation of private member")
                    if kind == "invoke-interface" and owner in classes and "interface" not in classes[owner].flags: errors.append(where + ": invoke-interface owner is not an interface")
                    if kind == "invoke-virtual" and owner in classes and "interface" in classes[owner].flags: errors.append(where + ": invoke-virtual owner is an interface")
                    if kind == "invoke-super" and (owner == caller or owner not in ancestors(caller, classes)): errors.append(where + ": invoke-super owner is not an ancestor")
                else:
                    if opcode.startswith("invoke-"):
                        errors.append(where + ": field reference used by invoke instruction")
                    suffix = opcode.split("-", 1)[1] if "-" in opcode else "normal"
                    field_type = tail[1:]
                    expected_suffix = "object" if field_type.startswith(("L", "[")) else "wide" if field_type in ("J", "D") else {"Z":"boolean", "B":"byte", "C":"char", "S":"short"}.get(field_type, "normal")
                    if suffix != expected_suffix:
                        errors.append(where + ": field opcode/type mismatch " + signature)
                    if opcode.startswith(("sget", "sput")) != static: errors.append(where + ": static/instance field kind mismatch " + signature)
                    if opcode.startswith(("iput", "sput")) and "final" in found.flags:
                        allowed = "<clinit>()V" if static else "<init>("
                        if caller != found.owner or method is None or not method.signature.startswith(allowed): errors.append(where + ": illegal final-field write " + signature)
        lineage = ancestors(caller, classes) - {caller}
        oem_lineage = lineage & oem_names
        callback_class = any(name in lineage for name in LISTENERS)
        for signature, declared in cls.methods.items():
            if signature.startswith(("<init>(", "<clinit>(")) or "private" in declared.flags: continue
            inherited = None
            for parent in parents(caller, classes):
                inherited = resolve(parent, signature, classes, True)
                if inherited: break
            name = signature.split("(", 1)[0]
            known_name = any(any(s.split("(", 1)[0] == name for s in classes[a].methods) for a in oem_lineage)
            if inherited is None:
                if oem_lineage and (callback_class or name.startswith("on") or known_name): errors.append(f"{caller}: callback/override does not match actual OEM declaration: {signature}")
                continue
            if inherited.owner not in oem_names or "private" in inherited.flags: continue
            override_checks += 1
            if "final" in inherited.flags: errors.append(f"{caller}: overrides final OEM method {signature}")
            if ("static" in declared.flags) != ("static" in inherited.flags): errors.append(f"{caller}: override static/instance mismatch {signature}")
            if visibility(declared.flags) < visibility(inherited.flags): errors.append(f"{caller}: narrowed OEM override visibility {signature}")
        if "abstract" not in cls.flags and "interface" not in cls.flags:
            for ancestor in oem_lineage:
                for signature, required in classes[ancestor].methods.items():
                    if "abstract" not in required.flags or "static" in required.flags: continue
                    implementation = resolve(caller, signature, classes, True)
                    if implementation is None or "abstract" in implementation.flags or "static" in implementation.flags or visibility(implementation.flags) < visibility(required.flags):
                        errors.append(f"{caller}: missing concrete OEM interface/abstract method {signature}")
    if errors: raise VerificationError(sorted(set(errors)))
    return {"ok": True, "helper_classes": len(helpers), "compiler_metadata": metadata,
            "references": refs, "oem_method_references": oem_methods, "oem_field_references": oem_fields,
            "helper_references": helper_refs, "platform_references_not_resolved": platform_refs,
            "oem_override_checks": override_checks, "deployment_ready": False}


def uncomment(line: str) -> str:
    """Keep literal string contents: changed constants must fail preservation."""
    quoted = escaped = False
    for index, char in enumerate(line):
        if quoted:
            if escaped: escaped = False
            elif char == "\\": escaped = True
            elif char == '"': quoted = False
        elif char == '"': quoted = True
        elif char == '#': return line[:index].strip()
    return line.strip()


def canonical_method(body: list[str]) -> tuple:
    """Only normalize whitespace/comments, label names and catch placement.

    Catch priority, registers, instructions, constants, annotations and debug
    directives remain significant. Apktool relocates catch declarations to the
    end of their protected range; this does not change the ordered handlers.
    This is an assembly roundtrip comparison, not a semantic optimizer.
    """
    lines = [uncomment(line) for line in body]
    lines = [line for line in lines if line]
    definitions = [line for line in lines if re.fullmatch(r":[\w$.-]+", line)]
    if len(definitions) != len(set(definitions)):
        raise VerificationError(["Duplicate method label"])
    labels = {name: f":label_{index}" for index, name in enumerate(definitions)}
    normalized, catches = [], []
    for line in lines:
        # Never rename text inside string constants or annotation values.
        pieces = re.split(r'("(?:[^"\\]|\\.)*")', line)
        for index in range(0, len(pieces), 2):
            pieces[index] = re.sub(r"(?<![\w;$])(:[\w$.-]+)",
                                   lambda m: labels.get(m[0], m[0]), pieces[index])
            pieces[index] = re.sub(r"\s+", " ", pieces[index])
        line = "".join(pieces)
        (catches if line.startswith((".catch ", ".catchall ")) else normalized).append(line)
    return tuple(normalized), tuple(catches)


def class_parts(cls: Class) -> tuple[tuple[str, ...], dict[str, tuple]]:
    outside, methods, body = [], {}, None
    signature = None
    for raw in cls.path.read_text(encoding="utf-8").splitlines():
        line = uncomment(raw)
        if line.startswith(".method "):
            signature = line.split()[-1]; body = [line]
        elif body is not None:
            body.append(line)
            if line == ".end method":
                methods[signature] = canonical_method(body); body = None
        elif line:
            # The only observed field normalization in the pinned assembler.
            line = re.sub(r"^(\.field[^\n]*\bstatic\b[^\n]*:Z) = false$", r"\1", line)
            outside.append(line)
    return tuple(outside), methods


def verify_final_hooks(original_root: Path, hooked_root: Path, final_root: Path) -> dict:
    """Prove four planned hooks survive the final assembled DEX unchanged.

    All inputs are decoded smali roots. The builder must first obtain hooked_root
    from the exact-profile patcher. final_root includes the actual helper DEX.
    This additionally preserves every other original method and all class/field
    metadata in the three changed OEM classes. verify() remains required for
    helper-to-OEM linkage; the builder separately preserves all untouched classes.
    No dependency is loaded or executed here.
    """
    original, hooked, final = map(read_tree, (original_root, hooked_root, final_root))
    errors, preserved, calls = [], 0, []
    changed = {INTEGRATION: {"registerCarService()V", "destroy()V"}, GAL: set(), BINDER: set()}
    added = {INTEGRATION: set(), GAL: {PAIR}, BINDER: {TRANSACT}}
    for owner in changed:
        if any(owner not in tree for tree in (original, hooked, final)):
            errors.append("Missing hook class: " + owner); continue
        old_outside, old = class_parts(original[owner])
        planned_outside, planned = class_parts(hooked[owner])
        actual_outside, actual = class_parts(final[owner])
        if not changed[owner] <= set(old) or added[owner] & set(old):
            errors.append("Unexpected original hook method profile: " + owner)
        expected = set(old) | added[owner]
        if set(planned) != expected or set(actual) != expected:
            errors.append("Hook class method inventory changed: " + owner)
        if old_outside != planned_outside or old_outside != actual_outside:
            errors.append("Hook class/field metadata changed: " + owner)
        for signature in set(old) - changed[owner]:
            if planned.get(signature) != old[signature] or actual.get(signature) != old[signature]:
                errors.append(f"Original method changed: {owner}->{signature}")
            else: preserved += 1
        for signature in changed[owner] | added[owner]:
            if signature not in planned or actual.get(signature) != planned[signature]:
                errors.append(f"Final hook differs from planned method: {owner}->{signature}")
        for number, line, method in final[owner].lines:
            for match in MEMBER_REF.finditer(line):
                target, signature = match["owner"], match["name"] + match["tail"]
                if not owned(target): continue
                source = (owner, method.signature if method else None)
                expected_call = HOOK_CALLS.get(source)
                if expected_call != (target, signature) or not line.startswith(("invoke-static ", "invoke-static/range ")):
                    errors.append(f"Unexpected OEM-to-helper access: {owner}:{number}"); continue
                entry_class = final.get(target)
                entry = None if entry_class is None else entry_class.methods.get(signature)
                if (entry is None or "public" not in entry_class.flags or
                        not {"public", "static"} <= entry.flags or {"abstract", "native"} & entry.flags):
                    errors.append(f"Missing public static concrete hook entry point: {target}->{signature}")
                calls.append(source)
    for source in HOOK_CALLS:
        if calls.count(source) != 1:
            errors.append(f"Expected exactly one incoming hook call: {source[0]}->{source[1]}")
    if errors: raise VerificationError(sorted(set(errors)))
    return {"ok": True, "hook_classes": len(changed), "hook_methods": len(HOOK_CALLS),
            "incoming_helper_calls": len(calls), "preserved_original_methods": preserved,
            "class_and_field_metadata_preserved": True, "deployment_ready": False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("helper_root", type=Path); parser.add_argument("stock_root", type=Path)
    parser.add_argument("--original-root", type=Path, help="with --hooked-root, also verify final hooks in stock_root")
    parser.add_argument("--hooked-root", type=Path)
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()
    try:
        result = verify(args.helper_root, args.stock_root)
        if bool(args.original_root) != bool(args.hooked_root):
            raise ValueError("Final hook verification requires both --original-root and --hooked-root")
        if args.original_root:
            result["final_hooks"] = verify_final_hooks(args.original_root, args.hooked_root, args.stock_root)
    except (OSError, ValueError) as exc:
        result = {"ok": False, "errors": getattr(exc, "errors", [str(exc)]), "deployment_ready": False}
    print(json.dumps(result, sort_keys=True) if args.json else json.dumps(result, indent=2))
    return 0 if result["ok"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
