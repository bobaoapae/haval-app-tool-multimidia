.class public Lcom/ts/androidauto/app/display/AppMediaButtonBridge;
.super Landroid/media/session/MediaSession$Callback;
.source "AppMediaButtonBridge.java"

# v3.0: Android Auto has no Android MediaSession, so a paused local app (YouTube) kept
# the OS media-button session and reacted to the steering wheel together with AA
# (each press paused one and started the other). This session is claimed while
# AapActivity is resumed, so AA wins the media button, and every wheel key is sent
# to the phone as the head unit's own hardkey through ImpulseHardKeyDispatcher
# (0x3ec play/pause toggle, 0x3eb next, 0x3ea previous). The phone applies a real
# toggle; v2.6's discrete onPlay/onPause could only ever pause.


# static fields
.field private static sContext:Landroid/content/Context;

.field private static sSession:Landroid/media/session/MediaSession;

# Echo guard: HardKeyPolicyManager feeds the injected hardkey back into Android as a new
# media key ~10 ms later; without this the bridge re-sent it forever (2026-10-13 19:46,
# ~14 hardkeys/s cycling play/next/previous).
.field private static sLastSentMs:J


# direct methods
.method public constructor <init>()V
    .locals 0

    invoke-direct {p0}, Landroid/media/session/MediaSession$Callback;-><init>()V

    return-void
.end method

.method public static install(Landroid/content/Context;)V
    .locals 5

    :try_start_0
    sget-object v0, Lcom/ts/androidauto/app/display/AppMediaButtonBridge;->sSession:Landroid/media/session/MediaSession;

    if-nez v0, :cond_0

    invoke-virtual {p0}, Landroid/content/Context;->getApplicationContext()Landroid/content/Context;

    move-result-object v0

    sput-object v0, Lcom/ts/androidauto/app/display/AppMediaButtonBridge;->sContext:Landroid/content/Context;

    new-instance v1, Landroid/media/session/MediaSession;

    const-string v2, "HavalAaApp"

    invoke-direct {v1, v0, v2}, Landroid/media/session/MediaSession;-><init>(Landroid/content/Context;Ljava/lang/String;)V

    const/4 v2, 0x3

    invoke-virtual {v1, v2}, Landroid/media/session/MediaSession;->setFlags(I)V

    new-instance v2, Landroid/os/HandlerThread;

    const-string v3, "HavalAaMedia"

    invoke-direct {v2, v3}, Landroid/os/HandlerThread;-><init>(Ljava/lang/String;)V

    invoke-virtual {v2}, Landroid/os/HandlerThread;->start()V

    new-instance v3, Landroid/os/Handler;

    invoke-virtual {v2}, Landroid/os/HandlerThread;->getLooper()Landroid/os/Looper;

    move-result-object v4

    invoke-direct {v3, v4}, Landroid/os/Handler;-><init>(Landroid/os/Looper;)V

    new-instance v2, Lcom/ts/androidauto/app/display/AppMediaButtonBridge;

    invoke-direct {v2}, Lcom/ts/androidauto/app/display/AppMediaButtonBridge;-><init>()V

    invoke-virtual {v1, v2, v3}, Landroid/media/session/MediaSession;->setCallback(Landroid/media/session/MediaSession$Callback;Landroid/os/Handler;)V

    sput-object v1, Lcom/ts/androidauto/app/display/AppMediaButtonBridge;->sSession:Landroid/media/session/MediaSession;

    invoke-static {}, Lcom/ts/androidauto/app/display/AppMediaButtonBridge;->setPlaying()V

    const-string v0, "ImpulseAAMedia"

    const-string v1, "media session installed"

    invoke-static {v0, v1}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;)I
    :try_end_0
    .catch Ljava/lang/Throwable; {:try_start_0 .. :try_end_0} :catch_0

    :cond_0
    return-void

    :catch_0
    move-exception v0

    const-string v1, "ImpulseAAMedia"

    const-string v2, "media session install failed"

    invoke-static {v1, v2, v0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    return-void
.end method

.method private static setPlaying()V
    .locals 6

    sget-object v0, Lcom/ts/androidauto/app/display/AppMediaButtonBridge;->sSession:Landroid/media/session/MediaSession;

    if-eqz v0, :cond_0

    new-instance v1, Landroid/media/session/PlaybackState$Builder;

    invoke-direct {v1}, Landroid/media/session/PlaybackState$Builder;-><init>()V

    # PAUSE | PLAY | SKIP_TO_PREVIOUS | SKIP_TO_NEXT | PLAY_PAUSE
    const-wide/16 v2, 0x236

    invoke-virtual {v1, v2, v3}, Landroid/media/session/PlaybackState$Builder;->setActions(J)Landroid/media/session/PlaybackState$Builder;

    const/4 v2, 0x3

    const-wide/16 v3, 0x0

    const/high16 v5, 0x3f800000    # 1.0f

    invoke-virtual {v1, v2, v3, v4, v5}, Landroid/media/session/PlaybackState$Builder;->setState(IJF)Landroid/media/session/PlaybackState$Builder;

    invoke-virtual {v1}, Landroid/media/session/PlaybackState$Builder;->build()Landroid/media/session/PlaybackState;

    move-result-object v1

    invoke-virtual {v0, v1}, Landroid/media/session/MediaSession;->setPlaybackState(Landroid/media/session/PlaybackState;)V

    :cond_0
    return-void
.end method

.method public static claim()V
    .locals 3

    :try_start_0
    sget-object v0, Lcom/ts/androidauto/app/display/AppMediaButtonBridge;->sSession:Landroid/media/session/MediaSession;

    if-eqz v0, :cond_0

    invoke-static {}, Lcom/ts/androidauto/app/display/AppMediaButtonBridge;->setPlaying()V

    const/4 v1, 0x1

    invoke-virtual {v0, v1}, Landroid/media/session/MediaSession;->setActive(Z)V
    :try_end_0
    .catch Ljava/lang/Throwable; {:try_start_0 .. :try_end_0} :catch_0

    :cond_0
    return-void

    :catch_0
    move-exception v0

    const-string v1, "ImpulseAAMedia"

    const-string v2, "media session claim failed"

    invoke-static {v1, v2, v0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    return-void
.end method

.method public static deactivate()V
    .locals 2

    :try_start_0
    sget-object v0, Lcom/ts/androidauto/app/display/AppMediaButtonBridge;->sSession:Landroid/media/session/MediaSession;

    if-eqz v0, :cond_0

    const/4 v1, 0x0

    invoke-virtual {v0, v1}, Landroid/media/session/MediaSession;->setActive(Z)V
    :try_end_0
    .catch Ljava/lang/Throwable; {:try_start_0 .. :try_end_0} :catch_0

    :cond_0
    :catch_0
    return-void
.end method

.method public static release()V
    .locals 2

    :try_start_0
    sget-object v0, Lcom/ts/androidauto/app/display/AppMediaButtonBridge;->sSession:Landroid/media/session/MediaSession;

    if-eqz v0, :cond_0

    const/4 v1, 0x0

    sput-object v1, Lcom/ts/androidauto/app/display/AppMediaButtonBridge;->sSession:Landroid/media/session/MediaSession;

    invoke-virtual {v0}, Landroid/media/session/MediaSession;->release()V
    :try_end_0
    .catch Ljava/lang/Throwable; {:try_start_0 .. :try_end_0} :catch_0

    :cond_0
    :catch_0
    return-void
.end method

.method private static mapKey(I)I
    .locals 1

    # Only the wheel keys. Discrete PLAY/PAUSE (126/127) are left to the default
    # callbacks: a toggle could invert them.
    const/16 v0, 0x55

    if-eq p0, v0, :cond_toggle

    const/16 v0, 0x57

    if-eq p0, v0, :cond_next

    const/16 v0, 0x58

    if-eq p0, v0, :cond_prev

    const/4 v0, 0x0

    return v0

    :cond_toggle
    const/16 v0, 0x3ec

    return v0

    :cond_next
    const/16 v0, 0x3eb

    return v0

    :cond_prev
    const/16 v0, 0x3ea

    return v0
.end method

.method private static dispatchHardKey(I)V
    .locals 5

    sget-object v0, Lcom/ts/androidauto/app/display/AppMediaButtonBridge;->sContext:Landroid/content/Context;

    if-eqz v0, :cond_0

    invoke-static {}, Landroid/os/SystemClock;->uptimeMillis()J

    move-result-wide v2

    sput-wide v2, Lcom/ts/androidauto/app/display/AppMediaButtonBridge;->sLastSentMs:J

    new-instance v1, Landroid/content/Intent;

    const-string v2, "br.com.redesurftank.havalshisuku.AA_MEDIA_COMMAND"

    invoke-direct {v1, v2}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V

    const-string v2, "token"

    const-string v3, "impulse-aa-media-v1"

    invoke-virtual {v1, v2, v3}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    const-string v2, "keyCode"

    invoke-virtual {v1, v2, p0}, Landroid/content/Intent;->putExtra(Ljava/lang/String;I)Landroid/content/Intent;

    const-string v2, "targetDisplay"

    const/4 v3, 0x0

    invoke-virtual {v1, v2, v3}, Landroid/content/Intent;->putExtra(Ljava/lang/String;I)Landroid/content/Intent;

    invoke-static {v0, v1}, Lcom/ts/androidauto/app/ImpulseHardKeyDispatcher;->dispatch(Landroid/content/Context;Landroid/content/Intent;)Z

    move-result v4

    invoke-static {}, Landroid/os/SystemClock;->uptimeMillis()J

    move-result-wide v2

    sput-wide v2, Lcom/ts/androidauto/app/display/AppMediaButtonBridge;->sLastSentMs:J

    const-string v2, "ImpulseAAMedia"

    new-instance v3, Ljava/lang/StringBuilder;

    invoke-direct {v3}, Ljava/lang/StringBuilder;-><init>()V

    const-string v1, "media button -> hardkey "

    invoke-virtual {v3, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v3, p0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v1, " sent="

    invoke-virtual {v3, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;

    invoke-virtual {v3}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v3

    invoke-static {v2, v3}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;)I

    :cond_0
    return-void
.end method


# virtual methods
.method public onMediaButtonEvent(Landroid/content/Intent;)Z
    .locals 4

    # Swallow everything within 500 ms of our own hardkey: that is the echo.
    invoke-static {}, Landroid/os/SystemClock;->uptimeMillis()J

    move-result-wide v0

    sget-wide v2, Lcom/ts/androidauto/app/display/AppMediaButtonBridge;->sLastSentMs:J

    sub-long/2addr v0, v2

    const-wide/16 v2, 0x1f4

    cmp-long v0, v0, v2

    if-gez v0, :cond_not_echo

    const/4 v0, 0x1

    return v0

    :cond_not_echo
    const-string v0, "android.intent.extra.KEY_EVENT"

    invoke-virtual {p1, v0}, Landroid/content/Intent;->getParcelableExtra(Ljava/lang/String;)Landroid/os/Parcelable;

    move-result-object v0

    check-cast v0, Landroid/view/KeyEvent;

    if-eqz v0, :cond_super

    invoke-virtual {v0}, Landroid/view/KeyEvent;->getKeyCode()I

    move-result v1

    invoke-static {v1}, Lcom/ts/androidauto/app/display/AppMediaButtonBridge;->mapKey(I)I

    move-result v1

    if-eqz v1, :cond_super

    # Act once per press, on ACTION_UP: the head unit may deliver only the UP.
    invoke-virtual {v0}, Landroid/view/KeyEvent;->getAction()I

    move-result v2

    const/4 p1, 0x1

    if-ne v2, p1, :cond_handled

    invoke-virtual {v0}, Landroid/view/KeyEvent;->getRepeatCount()I

    move-result v2

    if-nez v2, :cond_handled

    # v3.5: absorb only. The car's native route already sends the wheel key to Android
    # Auto (as on a stock head unit); forwarding a hardkey as well toggled twice and
    # skipped two tracks. This session exists so YouTube does not get Android's copy.
    const-string v0, "ImpulseAAMedia"

    const-string v2, "media button absorbed; native route drives AA"

    invoke-static {v0, v2}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;)I

    :cond_handled
    const/4 v0, 0x1

    return v0

    :cond_super
    invoke-super {p0, p1}, Landroid/media/session/MediaSession$Callback;->onMediaButtonEvent(Landroid/content/Intent;)Z

    move-result v0

    return v0
.end method

# v3.4: Impulse decides which source owns the wheel. Android picks the media-button
# session from the app that last played through an Android player; AA's audio is not
# one here, so after YouTube played Android never gave the button back. Impulse sends
# AA_MEDIA_COMMAND with mediaButton=claim when AA becomes the current media and
# mediaButton=release when another app does. Claim uses FLAG_EXCLUSIVE_GLOBAL_PRIORITY
# (system uid only), which takes every media button while active.
.method public static onImpulseCommand(Landroid/content/Context;Landroid/content/Intent;)Z
    .locals 3

    const-string v0, "mediaButton"

    invoke-virtual {p1, v0}, Landroid/content/Intent;->getStringExtra(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    if-nez v0, :cond_has

    const/4 v1, 0x0

    return v1

    :cond_has
    invoke-static {p0}, Lcom/ts/androidauto/app/display/AppMediaButtonBridge;->install(Landroid/content/Context;)V

    const-string v1, "claim"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_release

    invoke-static {}, Lcom/ts/androidauto/app/display/AppMediaButtonBridge;->claimExclusive()V

    goto :goto_log

    :cond_release
    invoke-static {}, Lcom/ts/androidauto/app/display/AppMediaButtonBridge;->releaseExclusive()V

    :goto_log
    const-string v1, "ImpulseAAMedia"

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string p0, "media button ownership: "

    invoke-virtual {v2, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-static {v1, v2}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;)I

    const/4 v1, 0x1

    return v1
.end method

.method public static claimExclusive()V
    .locals 3

    sget-object v0, Lcom/ts/androidauto/app/display/AppMediaButtonBridge;->sSession:Landroid/media/session/MediaSession;

    if-eqz v0, :cond_0

    :try_start_0
    const v1, 0x10003

    invoke-virtual {v0, v1}, Landroid/media/session/MediaSession;->setFlags(I)V
    :try_end_0
    .catch Ljava/lang/Throwable; {:try_start_0 .. :try_end_0} :catch_0

    goto :goto_0

    :catch_0
    move-exception v1

    const-string v2, "ImpulseAAMedia"

    const-string v1, "exclusive media button refused; plain claim"

    invoke-static {v2, v1}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;)I

    :goto_0
    invoke-static {}, Lcom/ts/androidauto/app/display/AppMediaButtonBridge;->claim()V

    :cond_0
    return-void
.end method

.method public static releaseExclusive()V
    .locals 2

    sget-object v0, Lcom/ts/androidauto/app/display/AppMediaButtonBridge;->sSession:Landroid/media/session/MediaSession;

    if-eqz v0, :cond_0

    :try_start_0
    const/4 v1, 0x3

    invoke-virtual {v0, v1}, Landroid/media/session/MediaSession;->setFlags(I)V

    const/4 v1, 0x0

    invoke-virtual {v0, v1}, Landroid/media/session/MediaSession;->setActive(Z)V
    :try_end_0
    .catch Ljava/lang/Throwable; {:try_start_0 .. :try_end_0} :catch_0

    :cond_0
    :catch_0
    return-void
.end method
