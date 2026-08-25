package br.com.redesurftank.havalshisuku.managers

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * PIN de confirmação da saída do Modo Concessionária.
 *
 * A sequência do volante/setas prova INTENÇÃO (não foi sem querer); o PIN prova IDENTIDADE (é o
 * dono, não alguém que reparou no gesto). Por isso um não substitui o outro: o PIN só é pedido
 * depois que a sequência fecha.
 *
 * O número nunca é gravado — só o SHA-256 de (sal + PIN), com um sal aleatório por carro. Um PIN
 * de 4 dígitos tem só 10 mil combinações, então o hash sozinho não seria grande obstáculo pra quem
 * tivesse o arquivo em mãos; o que realmente segura aqui é o bloqueio progressivo por tentativa
 * errada, que torna a força bruta inviável em quem está sentado no carro.
 *
 * **Sem porta dos fundos.** Esquecer o PIN com o modo ativo se resolve reinstalando o app e
 * perdendo as configurações — o aviso disso está na tela que liga o PIN, e é decisão do dono.
 */
object StealthExitPin {

    private const val TAG = "StealthExitPin"
    private const val PREFS = "haval_prefs"

    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 8

    /** Erros tolerados antes de começar a atrasar as tentativas. */
    private const val FREE_ATTEMPTS = 3

    /** Espera após cada erro além dos tolerados: 30s, 60s, 120s... até o teto. */
    private const val LOCKOUT_BASE_MS = 30_000L
    private const val LOCKOUT_MAX_MS = 10 * 60_000L

    @Volatile private var failedAttempts = 0
    @Volatile private var lockedUntilMs = 0L

    private fun prefs(context: Context = App.getDeviceProtectedContext()) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean =
            prefs().getBoolean(SharedPreferencesKeys.ENABLE_STEALTH_EXIT_PIN.key, false) &&
                    !prefs().getString(SharedPreferencesKeys.STEALTH_EXIT_PIN_HASH.key, "").isNullOrEmpty()

    /** Grava o PIN (só o hash). Devolve null quando deu certo, ou o motivo da recusa. */
    fun setPin(pin: String): String? {
        val digits = pin.trim()
        if (digits.length < MIN_LENGTH) return "O PIN precisa de pelo menos $MIN_LENGTH dígitos."
        if (digits.length > MAX_LENGTH) return "O PIN pode ter no máximo $MAX_LENGTH dígitos."
        if (!digits.all { it.isDigit() }) return "Use apenas números."
        return try {
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
            prefs().edit {
                putString(SharedPreferencesKeys.STEALTH_EXIT_PIN_SALT.key, saltB64)
                putString(SharedPreferencesKeys.STEALTH_EXIT_PIN_HASH.key, hash(digits, saltB64))
            }
            reset()
            null
        } catch (t: Throwable) {
            Log.e(TAG, "falha ao gravar o PIN", t)
            "Não foi possível gravar o PIN."
        }
    }

    fun clearPin() {
        prefs().edit {
            remove(SharedPreferencesKeys.STEALTH_EXIT_PIN_HASH.key)
            remove(SharedPreferencesKeys.STEALTH_EXIT_PIN_SALT.key)
        }
        reset()
    }

    private fun hash(pin: String, saltB64: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(Base64.decode(saltB64, Base64.NO_WRAP))
        return Base64.encodeToString(md.digest(pin.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    /** Quanto falta do bloqueio, em ms. 0 = pode tentar. */
    fun lockoutRemainingMs(): Long =
            (lockedUntilMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)

    fun reset() {
        failedAttempts = 0
        lockedUntilMs = 0L
    }

    sealed class Result {
        object Ok : Result()
        data class Wrong(val remainingFreeAttempts: Int) : Result()
        data class Locked(val remainingMs: Long) : Result()
    }

    fun verify(pin: String): Result {
        val remaining = lockoutRemainingMs()
        if (remaining > 0) return Result.Locked(remaining)

        val saltB64 = prefs().getString(SharedPreferencesKeys.STEALTH_EXIT_PIN_SALT.key, "").orEmpty()
        val expected = prefs().getString(SharedPreferencesKeys.STEALTH_EXIT_PIN_HASH.key, "").orEmpty()
        if (saltB64.isEmpty() || expected.isEmpty()) {
            // PIN marcado como ligado mas sem hash: não trancar o dono por um estado inconsistente.
            Log.w(TAG, "PIN ligado sem hash gravado; liberando")
            return Result.Ok
        }

        val ok = try {
            MessageDigest.isEqual(
                    hash(pin.trim(), saltB64).toByteArray(Charsets.UTF_8),
                    expected.toByteArray(Charsets.UTF_8)
            )
        } catch (t: Throwable) {
            Log.e(TAG, "falha ao conferir o PIN", t)
            false
        }

        if (ok) {
            reset()
            return Result.Ok
        }

        failedAttempts++
        val over = failedAttempts - FREE_ATTEMPTS
        if (over > 0) {
            val wait = (LOCKOUT_BASE_MS shl (over - 1).coerceAtMost(5)).coerceAtMost(LOCKOUT_MAX_MS)
            lockedUntilMs = SystemClock.elapsedRealtime() + wait
            return Result.Locked(wait)
        }
        return Result.Wrong((FREE_ATTEMPTS - failedAttempts).coerceAtLeast(0))
    }
}
