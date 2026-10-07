package br.com.redesurftank.displaypilot;

/** Main-thread-only ownership. Lifecycle events never reopen a Presentation automatically. */
public final class PilotSession {
    public interface Output { void close(); }
    public interface Renderer {
        Output open(DisplayTarget target, Viewport viewport, Runnable dismissed) throws Exception;
    }
    public interface Status { void report(String message); }
    private final Renderer renderer;
    private final Status status;
    private Output output;
    private DisplayTarget active;
    private boolean foreground;
    private boolean cleanupFailed;
    private long generation;

    public PilotSession(Renderer renderer, Status status) {
        this.renderer = renderer;
        this.status = status;
    }

    public void resume() { foreground = true; }
    public boolean isOpen() { return output != null; }

    public void open(DisplayTarget target, Viewport viewport, boolean parked, boolean clearArea) {
        stop("Parado.");
        if (cleanupFailed) return;
        if (!foreground) { status.report("Abra o piloto na central antes de projetar."); return; }
        if (!parked || !clearArea) { status.report("Confirme carro parado e área livre de instrumentos."); return; }
        if (target == null || viewport == null) { status.report("Escolha um display e uma área válida."); return; }
        String rejection = target.rejection();
        if (rejection != null) { status.report(rejection); return; }
        final long token = ++generation;
        active = target;
        try {
            Output opened = renderer.open(target, viewport, () -> {
                if (generation == token) stop("Janela encerrada pelo Android. Reabra manualmente.");
            });
            if (opened == null) throw new IllegalStateException("Nenhuma janela criada.");
            if (generation != token || !foreground) {
                opened.close();
                return;
            }
            output = opened;
            status.report("SIMULAÇÃO aberta no display " + target.id + ". Pare se cobrir instrumentos.");
        } catch (Exception failure) {
            if (generation == token) {
                stop("Abertura recusada (" + failure.getClass().getSimpleName()
                        + "). Sem tentativa em outro display ou privilégio adicional.");
            }
        }
    }

    public void pause() {
        foreground = false;
        stop("Pausado: projeção encerrada. Reconfirme e abra manualmente.");
    }

    public void displayChanged(int displayId) {
        if (active != null && active.id == displayId) {
            stop("Display alterado ou desconectado. Selecione e confirme novamente.");
        }
    }

    public void stop(String reason) {
        ++generation; // Invalidate callbacks before dismiss() can synchronously call one.
        Output old = output;
        output = null;
        active = null;
        if (old != null) {
            try { old.close(); }
            catch (RuntimeException failure) { cleanupFailed = true; }
        }
        status.report(cleanupFailed
                ? "Falha ao encerrar a janela. Novas aberturas bloqueadas; feche o piloto pela interface do Android."
                : reason);
    }
}
