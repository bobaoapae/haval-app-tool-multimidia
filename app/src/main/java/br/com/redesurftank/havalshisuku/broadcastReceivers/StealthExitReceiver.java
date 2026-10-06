package br.com.redesurftank.havalshisuku.broadcastReceivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import br.com.redesurftank.havalshisuku.managers.StealthModeManager;

/**
 * Porta de serviço do Modo Concessionária (rede de segurança).
 *
 * Com o modo ativo o ícone do launcher some, então a saída normal é a sequência do volante
 * (3 toques CURTOS no botão 1, em até 8s). Se o volante falhar, este receiver reverte por
 * telnet/adb.
 *
 * ATENÇÃO — O BROADCAST TEM QUE SER EXPLÍCITO. Testado no carro: só com `-n <componente>`
 * funciona. A partir do Android 8 receivers declarados no manifest não recebem broadcasts
 * implícitos, então mandar só a action NÃO chega aqui (o comando "funciona" e nada acontece):
 *
 * <pre>
 * am broadcast -n br.com.redesurftank.havalshisuku/.broadcastReceivers.StealthExitReceiver \
 *     -a br.com.redesurftank.havalshisuku.STEALTH_EXIT
 * </pre>
 *
 * A action continua sendo conferida abaixo, então ela não pode ser omitida do comando.
 */
public class StealthExitReceiver extends BroadcastReceiver {
    private static final String ACTION = "br.com.redesurftank.havalshisuku.STEALTH_EXIT";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.w("StealthExitReceiver", "Received intent: " + (intent == null ? "null" : intent.getAction()));
        if (intent != null && ACTION.equals(intent.getAction())) {
            StealthModeManager.exit(context, "BROADCAST");
        }
    }
}
