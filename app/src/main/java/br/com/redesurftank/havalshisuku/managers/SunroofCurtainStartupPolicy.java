package br.com.redesurftank.havalshisuku.managers;

/**
 * Tempo da automação "abrir a cortina do teto ao ligar" (o armamento do boot).
 *
 * <p>Invariante: a cortina nunca é comandada menos de {@link #SETTLE_AFTER_READY_MS} depois de
 * se saber que o carro está READY. Cada gatilho já garante um tempo mínimo de READY ligado
 * ({@link #minimumReadyOnMs}); o settle só cobre o que falta.
 *
 * <p>O poll (leitura ao vivo em arm + {@link #READY_CHECK_DELAY_MS}) só consome o armamento se
 * nenhum evento de READY chegou depois do armamento, ou seja, o READY já estava ligado há pelo
 * menos 30 s: o settle de 20 s seria redundante. O tempo relativo ao boot no poll continua o
 * mesmo (arm + 30 s), só sem o settle extra, então o comando nunca cai na janela de ~25-45 s do
 * boot em que o módulo do teto ignorava comandos (commit 04a3c126).
 */
public final class SunroofCurtainStartupPolicy {
    private SunroofCurtainStartupPolicy() {}

    public static final long SETTLE_AFTER_READY_MS = 20_000L;
    public static final long READY_CHECK_DELAY_MS = 30_000L;
    public static final long ARM_FALLBACK_MS = 120_000L;

    public enum Trigger {
        /** READY acabou de ligar. */
        DRIVING_READY_EVENT("driving_ready_event"),
        /** READY confirmado por leitura ao vivo em arm + READY_CHECK_DELAY_MS, sem evento antes. */
        DRIVING_READY_POLL("driving_ready_poll"),
        /** READY nunca confirmado; tenta assim mesmo. */
        FALLBACK_TIMEOUT("fallback_timeout");

        public final String traceName;

        Trigger(String traceName) {
            this.traceName = traceName;
        }
    }

    /** Tempo mínimo que o READY já está ligado quando o gatilho dispara (0 = acabou de ligar ou não confirmado). */
    public static long minimumReadyOnMs(Trigger trigger) {
        return trigger == Trigger.DRIVING_READY_POLL ? READY_CHECK_DELAY_MS : 0L;
    }

    /** Espera antes da primeira avaliação da agenda: o que falta do settle depois do READY. */
    public static long settleMsFor(Trigger trigger) {
        return Math.max(0L, SETTLE_AFTER_READY_MS - minimumReadyOnMs(trigger));
    }
}
