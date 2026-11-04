"""Inject the v3.2 media-button bridge into a decompiled AA App tree (v2.9 smali).

    python inject.py <apktool-tree> AppMediaButtonBridge.smali

AndroidAutoRemoteUiService.onCreate and AapActivity.onCreate install + claim the
session (idempotent); AapActivity.onResume re-claims it. Nothing releases it while
the App process lives: Impulse binds the service only briefly per command.
"""
import shutil
import sys

tree, bridge_src = sys.argv[1], sys.argv[2]
app = tree + "/smali/com/ts/androidauto/app"
shutil.copyfile(bridge_src, app + "/display/AppMediaButtonBridge.smali")
B = "Lcom/ts/androidauto/app/display/AppMediaButtonBridge;"
SENT = "# V31_MEDIA_BUTTON"
INSTALL = "\n    " + SENT + "\n    invoke-static {p0}, " + B + "->install(Landroid/content/Context;)V\n\n    invoke-static {}, " + B + "->claim()V\n"


def load(p):
    return open(p, encoding="utf-8").read()


def save(p, s):
    open(p, "w", encoding="utf-8", newline="").write(s)


def patch_method(s, sig, fn):
    a = s.index(".method " + sig)
    b = s.index(".end method", a)
    return s[:a] + fn(s[a:b]) + s[b:]


def after(anchor, text):
    def fn(body):
        assert body.count(anchor) == 1, anchor
        return body.replace(anchor, anchor + text, 1)
    return fn


p = app + "/AndroidAutoRemoteUiService.smali"
s = load(p)
assert SENT not in s
s = patch_method(s, "public onCreate()V", after(
    "    invoke-direct {p0}, Lcom/ts/androidauto/app/AndroidAutoRemoteUiService;->registerImpulseMediaCommandReceiver()V\n",
    INSTALL))
save(p, s)

p = app + "/display/AapActivity.smali"
s = load(p)
assert SENT not in s
# After the v2.9 FLAG_FULLSCREEN injection in onCreate.
s = patch_method(s, "protected onCreate(Landroid/os/Bundle;)V", after(
    "    invoke-virtual {v0, v1}, Landroid/view/Window;->addFlags(I)V\n", INSTALL))


def before_return(body):
    assert body.count("    return-void\n") == 1
    return body.replace("    return-void\n", "    " + SENT + "\n    invoke-static {}, " + B + "->claim()V\n\n    return-void\n", 1)


s = patch_method(s, "protected onResume()V", before_return)
save(p, s)
print("ok")
