package com.adarena.notification.service;

/** Forma de entregar un email: por SMTP de verdad o, sin configurar, escribiéndolo en el log. */
public interface MailDelivery {

    void send(String to, String subject, String html, String text) throws Exception;

    /** true si los emails salen de verdad (hay SMTP configurado). */
    boolean isReal();
}
