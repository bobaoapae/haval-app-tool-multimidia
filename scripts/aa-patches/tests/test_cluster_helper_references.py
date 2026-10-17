"""Synthetic declarations exercise the real static verifier; no OEM code fixture."""
import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "integration/verify_helper_references.py"
spec = importlib.util.spec_from_file_location("cluster_helper_verifier", SCRIPT)
v = importlib.util.module_from_spec(spec);sys.modules[spec.name]=v;spec.loader.exec_module(v)
H = "Lcom/ts/androidauto/impulse/cluster/Test;"
BASE = "Lcom/google/android/projection/protocol/SyntheticBase;"
CHILD = "Lcom/google/android/projection/protocol/SyntheticChild;"
INTERFACE = "Lcom/google/android/projection/protocol/SyntheticInterface;"


def method(signature, body="    return-void", flags="public"):
    return f".method {flags} {signature}\n    .locals 1\n{body}\n.end method\n"


def klass(name, body="", parent="Ljava/lang/Object;", flags="public", interfaces=()):
    return f".class {flags} {name}\n.super {parent}\n" + "".join(".implements " + x + "\n" for x in interfaces) + body


class HelperReferenceTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(prefix="cluster-references-");self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name);self.helpers=self.root/"helpers";self.stock=self.root/"stock";self.helpers.mkdir();self.stock.mkdir()
        self.write(self.stock,BASE,klass(BASE,method("existing()V")+method("<init>()V",flags="public constructor")+".field public value:I\n.field public static CONSTANT:I\n"))
        self.write(self.helpers,H,klass(H,method("run()V",f"    invoke-virtual {{p0}}, {BASE}->existing()V\n    return-void")))

    def write(self, root, name, text):
        path=root/(name[1:-1]+".smali");path.parent.mkdir(parents=True,exist_ok=True);path.write_text(text);return path
    def helper(self, body, parent="Ljava/lang/Object;", flags="public", interfaces=()):
        return self.write(self.helpers,H,klass(H,body,parent,flags,interfaces))
    def base(self, body, flags="public", interfaces=()):
        return self.write(self.stock,BASE,klass(BASE,body,flags=flags,interfaces=interfaces))
    def rejected(self, text=None):
        with self.assertRaises(ValueError) as error:v.verify(self.helpers,self.stock)
        if text:self.assertIn(text,str(error.exception))
    def passes(self):return v.verify(self.helpers,self.stock)

    def test_public_method_and_field_references_resolve(self):
        self.helper(method("run()V",f"    invoke-virtual {{p0}}, {BASE}->existing()V\n    iget v0, p0, {BASE}->value:I\n    sget v0, {BASE}->CONSTANT:I\n    return-void"))
        report=self.passes();self.assertEqual(1,report["oem_method_references"]);self.assertEqual(2,report["oem_field_references"])
    def test_inherited_method_resolves_against_real_superclass(self):
        self.write(self.stock,CHILD,klass(CHILD,parent=BASE));self.helper(method("run()V",f"    invoke-virtual {{p0}}, {CHILD}->existing()V\n    return-void"));self.passes()
    def test_inherited_field_resolves_against_real_superclass(self):
        self.write(self.stock,CHILD,klass(CHILD,parent=BASE));self.helper(method("run()V",f"    iget v0, p0, {CHILD}->value:I\n    return-void"));self.passes()
    def test_missing_method_does_not_match_name_only(self):
        self.helper(method("run()V",f"    invoke-virtual {{p0}}, {BASE}->existing(I)V\n    return-void"));self.rejected("unresolved method")
    def test_missing_field_type_does_not_match_name_only(self):
        self.helper(method("run()V",f"    iget-object v0, p0, {BASE}->value:Ljava/lang/String;\n    return-void"));self.rejected("unresolved field")
    def test_constructor_cannot_be_inherited(self):
        self.write(self.stock,CHILD,klass(CHILD,parent=BASE));self.helper(method("run()V",f"    invoke-direct {{v0}}, {CHILD}-><init>()V\n    return-void"));self.rejected("unresolved method")
    def test_private_oem_member_rejected(self):
        self.base(method("existing()V",flags="private"));self.rejected("inaccessible member")
    def test_package_private_oem_member_rejected(self):
        self.base(method("existing()V",flags="final"));self.rejected("inaccessible member")
    def test_protected_super_call_from_subclass_allowed(self):
        self.base(method("existing()V",flags="protected"));self.helper(method("existing()V",f"    invoke-super {{p0}}, {BASE}->existing()V\n    return-void"),parent=BASE);self.passes()
    def test_protected_call_from_non_subclass_rejected(self):
        self.base(method("existing()V",flags="protected"));self.rejected("inaccessible member")
    def test_protected_receiver_constraint_rejects_unknown_base_receiver(self):
        self.base(method("existing()V",flags="protected"));self.helper(method("run()V",f"    invoke-virtual {{v0}}, {BASE}->existing()V\n    return-void"),parent=BASE);self.rejected("inaccessible member")
    def test_protected_constructor_requires_actual_super_constructor(self):
        self.base(method("<init>()V",flags="protected constructor"));self.helper(method("run()V",f"    invoke-direct {{v0}}, {BASE}-><init>()V\n    return-void"),parent=BASE);self.rejected("inaccessible member")
    def test_protected_super_constructor_allowed(self):
        self.base(method("<init>()V",flags="protected constructor"));self.helper(method("<init>()V",f"    invoke-direct {{p0}}, {BASE}-><init>()V\n    return-void",flags="public constructor"),parent=BASE);self.passes()
    def test_static_method_called_as_instance_rejected(self):
        self.base(method("existing()V",flags="public static"));self.rejected("static/instance")
    def test_instance_method_called_static_rejected(self):
        self.helper(method("run()V",f"    invoke-static {{}}, {BASE}->existing()V\n    return-void"));self.rejected("static/instance")
    def test_static_field_called_as_instance_rejected(self):
        self.helper(method("run()V",f"    iget v0, p0, {BASE}->CONSTANT:I\n    return-void"));self.rejected("field kind")
    def test_instance_field_called_static_rejected(self):
        self.helper(method("run()V",f"    sget v0, {BASE}->value:I\n    return-void"));self.rejected("field kind")
    def test_field_opcode_must_match_primitive_or_reference_kind(self):
        self.helper(method("run()V",f"    iget-object v0, p0, {BASE}->value:I\n    return-void"));self.rejected("opcode/type mismatch")
    def test_member_category_must_match_instruction(self):
        self.helper(method("run()V",f"    iget v0, p0, {BASE}->existing()V\n    return-void"));self.rejected("method reference used by field")
    def test_invoke_direct_public_non_constructor_rejected(self):
        self.helper(method("run()V",f"    invoke-direct {{p0}}, {BASE}->existing()V\n    return-void"));self.rejected("invoke-direct requires")
    def test_invoke_interface_requires_interface_owner(self):
        self.helper(method("run()V",f"    invoke-interface {{p0}}, {BASE}->existing()V\n    return-void"));self.rejected("not an interface")
    def test_stub_definition_in_helper_tree_is_forbidden(self):
        self.write(self.helpers,BASE,klass(BASE));self.rejected("Forbidden helper/API-stub")
    def test_arbitrary_compiler_namespace_not_allowed(self):
        self.write(self.helpers,"Lcom/android/tools/r8/Other;",klass("Lcom/android/tools/r8/Other;"));self.rejected("Forbidden helper/API-stub")
    def test_lambda_metadata_exception_requires_exact_fingerprint(self):
        self.write(self.helpers,v.COMPILER_METADATA,klass(v.COMPILER_METADATA));self.rejected("metadata fingerprint")
    def test_identical_helpers_in_final_merged_tree_are_allowed(self):
        text=(self.helpers/(H[1:-1]+".smali")).read_text();self.write(self.stock,H,text);self.passes()
    def test_different_helper_collision_in_stock_is_rejected(self):
        self.write(self.stock,H,klass(H));self.rejected("different stock class")
    def test_strings_and_comments_are_not_symbol_references(self):
        self.helper(method("run()V",'    const-string v0, "Lnot/Real;->missing()V # fake"\n    # Lalso/NotReal;->bad:I\n    return-void'));self.passes()
    def test_missing_oem_type_without_method_call_rejected(self):
        self.helper(method("run()V","    new-instance v0, Lcom/google/android/projection/protocol/Missing;\n    return-void"));self.rejected("missing referenced class")
    def test_callback_signature_must_match_actual_listener(self):
        listener=v.LISTENERS[0];self.write(self.stock,listener,klass(listener,method("onCodecSetup(I)V"),flags="public abstract"));self.helper(method("onCodecSetup(J)V"),parent=listener);self.rejected("callback/override")
    def test_concrete_listener_override_is_checked(self):
        listener=v.LISTENERS[0];self.write(self.stock,listener,klass(listener,method("onCodecSetup(I)V"),flags="public abstract"));self.helper(method("onCodecSetup(I)V"),parent=listener);self.assertEqual(1,self.passes()["oem_override_checks"])
    def test_missing_interface_implementation_is_rejected(self):
        self.write(self.stock,INTERFACE,klass(INTERFACE,method("needed()V",flags="public abstract"),flags="public interface abstract"));self.helper("",interfaces=(INTERFACE,));self.rejected("missing concrete")
    def test_private_method_does_not_implement_public_interface(self):
        self.write(self.stock,INTERFACE,klass(INTERFACE,method("needed()V",flags="public abstract"),flags="public interface abstract"));self.helper(method("needed()V",flags="private"),interfaces=(INTERFACE,));self.rejected("missing concrete")
    def test_final_method_cannot_be_overridden(self):
        self.base(method("existing()V",flags="public final"));self.helper(method("existing()V"),parent=BASE);self.rejected("overrides final")
    def test_override_cannot_narrow_access(self):
        self.helper(method("existing()V",flags="protected"),parent=BASE);self.rejected("narrowed")
    def test_final_class_cannot_be_extended(self):
        self.base(method("existing()V"),flags="public final");self.helper("",parent=BASE);self.rejected("final class")
    def test_inheritance_cycles_fail_closed(self):
        self.base("",interfaces=(H,));self.helper("",parent=BASE);self.rejected("Inheritance cycle")
    def test_duplicate_class_or_method_refused(self):
        self.write(self.helpers,"Lduplicate/Path;",klass(H));self.rejected("duplicate class")
    def test_duplicate_method_refused(self):
        self.helper(method("run()V")+method("run()V"));self.rejected("Duplicate method")
    def test_platform_refs_are_reported_separately_not_falsely_resolved(self):
        self.helper(method("run()V","    invoke-static {}, Ljava/lang/System;->gc()V\n    return-void"));r=self.passes();self.assertEqual(1,r["platform_references_not_resolved"]);self.assertEqual(0,r["oem_method_references"])


class FinalHookTest(unittest.TestCase):
    """Invented method bodies, real contract descriptors; no OEM implementations."""
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="cluster-final-hooks-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.original, self.hooked, self.final = [self.root / name for name in ("original", "hooked", "final")]
        for root in (self.original, self.hooked, self.final): root.mkdir()
        for owner in (v.INTEGRATION, v.GAL, v.BINDER):
            original = method("preserved()V", '    const-string v0, "keep # :label literal"\n    return-void')
            if owner == v.INTEGRATION:
                original += method("registerCarService()V") + method("destroy()V")
            text = klass(owner, ".field private static flag:Z = false\n" + original)
            self.write(self.original, owner, text)
            planned = original
            for (caller, signature), (target, entry) in v.HOOK_CALLS.items():
                if caller != owner: continue
                hook = method(signature, f"    invoke-static {{v0}}, {target}->{entry}\n    return-void")
                if owner == v.INTEGRATION: planned = planned.replace(method(signature), hook)
                else: planned += hook
            for root in (self.hooked, self.final):
                self.write(root, owner, klass(owner, ".field private static flag:Z = false\n" + planned))
        for owner in (v.INTEGRATION_HELPER, v.BINDER_HELPER):
            entries = "".join(method(sig, flags="public static") for target, sig in v.HOOK_CALLS.values() if target == owner)
            self.write(self.final, owner, klass(owner, entries))

    def path(self, root, owner): return root / (owner[1:-1] + ".smali")
    def write(self, root, owner, text):
        path = self.path(root, owner); path.parent.mkdir(parents=True, exist_ok=True); path.write_text(text)
    def replace(self, root, owner, before, after):
        path = self.path(root, owner); text = path.read_text()
        self.assertIn(before, text); path.write_text(text.replace(before, after))
    def passes(self): return v.verify_final_hooks(self.original, self.hooked, self.final)
    def rejected(self, message):
        with self.assertRaises(ValueError) as error: self.passes()
        self.assertIn(message, str(error.exception))

    def test_all_four_calls_and_other_original_methods_preserved(self):
        report = self.passes()
        self.assertEqual(4, report["incoming_helper_calls"])
        self.assertEqual(3, report["preserved_original_methods"])
        self.assertTrue(report["class_and_field_metadata_preserved"])
        self.assertFalse(report["deployment_ready"])

    def test_missing_hook_class_rejected(self):
        self.path(self.final, v.GAL).unlink(); self.rejected("Missing hook class")
    def test_missing_hook_method_rejected(self):
        target, entry = v.HOOK_CALLS[(v.GAL, v.PAIR)]
        self.replace(self.final, v.GAL, method(v.PAIR, f"    invoke-static {{v0}}, {target}->{entry}\n    return-void"), "")
        self.rejected("method inventory")
    def test_unexpected_original_hook_collision_rejected(self):
        self.replace(self.original, v.GAL, ".super Ljava/lang/Object;", ".super Ljava/lang/Object;\n" + method(v.PAIR))
        self.rejected("original hook method profile")
    def test_removed_registration_hook_rejected(self):
        target, entry = v.HOOK_CALLS[(v.INTEGRATION, "registerCarService()V")]
        self.replace(self.final, v.INTEGRATION, f"invoke-static {{v0}}, {target}->{entry}", "nop")
        self.rejected("Final hook differs")
    def test_registration_cannot_drop_viewing_distance_parameter(self):
        self.replace(self.final, v.INTEGRATION, f"register({v.GAL}I)V", f"register({v.GAL})V")
        self.rejected("Unexpected OEM-to-helper access")
    def test_duplicate_hook_call_rejected(self):
        target, entry = v.HOOK_CALLS[(v.INTEGRATION, "destroy()V")]
        call = f"invoke-static {{v0}}, {target}->{entry}"
        self.replace(self.final, v.INTEGRATION, call, call + "\n    " + call)
        self.rejected("exactly one incoming")
    def test_hook_cannot_call_instance_entry(self):
        self.replace(self.final, v.INTEGRATION_HELPER, ".method public static", ".method public")
        self.rejected("public static concrete")
    def test_private_entry_rejected(self):
        self.replace(self.final, v.INTEGRATION_HELPER, ".method public static", ".method private static")
        self.rejected("public static concrete")
    def test_missing_entry_rejected(self):
        self.path(self.final, v.BINDER_HELPER).unlink(); self.rejected("public static concrete")
    def test_native_entry_rejected(self):
        self.replace(self.final, v.BINDER_HELPER, ".method public static", ".method public static native")
        self.rejected("public static concrete")
    def test_abstract_entry_rejected(self):
        self.replace(self.final, v.BINDER_HELPER, ".method public static", ".method public static abstract")
        self.rejected("public static concrete")
    def test_entry_class_must_be_public(self):
        self.replace(self.final, v.BINDER_HELPER, ".class public", ".class final")
        self.rejected("public static concrete")
    def test_original_string_literal_change_not_scrubbed(self):
        self.replace(self.final, v.GAL, "keep # :label literal", "changed # :label literal")
        self.rejected("Original method changed")
    def test_planned_tree_cannot_alter_other_original_method(self):
        self.replace(self.hooked, v.BINDER, "keep # :label literal", "tampered")
        self.rejected("Original method changed")
    def test_extra_method_rejected(self):
        self.replace(self.final, v.BINDER, ".super Ljava/lang/Object;", ".super Ljava/lang/Object;\n" + method("extra()V"))
        self.rejected("method inventory")
    def test_original_field_access_cannot_change(self):
        self.replace(self.final, v.GAL, ".field private static", ".field public static")
        self.rejected("class/field metadata")
    def test_original_field_value_cannot_change(self):
        self.replace(self.final, v.GAL, ":Z = false", ":Z = true")
        self.rejected("class/field metadata")
    def test_only_redundant_static_false_normalization_allowed(self):
        self.replace(self.final, v.GAL, ":Z = false", ":Z"); self.passes()
    def test_original_class_parent_cannot_change(self):
        self.replace(self.final, v.GAL, ".super Ljava/lang/Object;", ".super Ljava/lang/Exception;")
        self.rejected("class/field metadata")
    def test_hook_method_access_cannot_change(self):
        self.replace(self.final, v.GAL, ".method public " + v.PAIR, ".method private " + v.PAIR)
        self.rejected("Final hook differs")
    def test_canonicalization_preserves_catch_priority(self):
        first = [":start", "nop", ":end", "return-void", ":handler", "throw v0",
                 ".catch Ljava/lang/Exception; {:start .. :end} :handler",
                 ".catchall {:start .. :end} :handler"]
        swapped = first[:-2] + list(reversed(first[-2:]))
        self.assertNotEqual(v.canonical_method(first), v.canonical_method(swapped))
    def test_label_names_catch_placement_and_comments_normalize(self):
        before = [".locals 1", ":start", 'const-string v0, ":start # literal"', ":end", "return-void", ":handler", "throw v0",
                  ".catchall {:start .. :end} :handler"]
        after = [".locals 1", ":renamed", 'const-string v0, ":start # literal" # comment', ":last",
                 ".catchall {:renamed .. :last} :catch", "return-void", ":catch", "throw v0"]
        self.assertEqual(v.canonical_method(before), v.canonical_method(after))
    def test_normalization_does_not_change_annotation_values_or_instruction_constants(self):
        for before, after in [('const-string v0, ":start"', 'const-string v0, ":other"'),
                              ("const/16 v0, 0x37", "const/16 v0, 0x38"),
                              ('value = "first"', 'value = "second"')]:
            self.assertNotEqual(v.canonical_method([before]), v.canonical_method([after]))
    def test_duplicate_method_label_refused(self):
        with self.assertRaises(ValueError): v.canonical_method([":same", "nop", ":same"])


if __name__ == "__main__":unittest.main()
