package com.adarena.support;

import org.springframework.test.context.TestPropertySource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Como @IntegrationTest, pero con SU PROPIA base de datos. Los tests de pujas guardan datos de
 * verdad (hace falta para probar la concurrencia) y abren y cierran rondas; así no interfieren
 * con los demás tests. La propiedad distinta hace que Spring cree un contexto (y un contenedor
 * PostgreSQL) aparte, compartido solo entre las clases con esta anotación.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@IntegrationTest
@TestPropertySource(properties = "test.database=isolated-arena")
public @interface IsolatedArenaTest {
}
