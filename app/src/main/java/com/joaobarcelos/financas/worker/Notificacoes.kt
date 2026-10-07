package com.joaobarcelos.financas.worker

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.joaobarcelos.financas.MainActivity
import com.joaobarcelos.financas.R
import com.joaobarcelos.financas.domain.usecase.Alerta
import com.joaobarcelos.financas.domain.usecase.Notificador
import com.joaobarcelos.financas.domain.usecase.mensagem
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Mostra os alertas A1 a A5 como notificações do Android. O texto vem do domínio. */
class NotificacoesAndroid @Inject constructor(@ApplicationContext private val context: Context) : Notificador {
    override fun avisar(alerta: Alerta) {
        // Sem a permissão (Android 13+), o alerta fica registrado, mas não aparece
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val mensagem = alerta.mensagem()
        val abrirApp = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notificacao = NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setContentTitle(mensagem.titulo)
            .setContentText(mensagem.texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(mensagem.texto))
            .setContentIntent(abrirApp)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify("${alerta.codigo}-${alerta.periodoRef}".hashCode(), notificacao)
    }

    companion object {
        const val CANAL = "alertas"

        fun criarCanal(context: Context) {
            NotificationManagerCompat.from(context).createNotificationChannel(
                NotificationChannelCompat.Builder(CANAL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName("Alertas do Fôlego")
                    .setDescription("Resumo da semana, limite da semana, reserva invadida e fechamento do ciclo")
                    .build(),
            )
        }
    }
}
