package com.blue.open.Core.Kernel.Notificacion;

// Mail que dispara una entidad del proyecto (ej. registro, recuperar contrasena): arma el asunto y el cuerpo HTML
// que despues se envian con el puerto EnviadorDeMail
public interface NotificacionPorMail {
    String getAsunto();
    String getCuerpo();
}
