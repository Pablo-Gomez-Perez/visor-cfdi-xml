package com.kathsoft.visor.app.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Pruebas del almacenamiento de contraseñas sin acceso a MySQL. */
class PasswordHasherTest {

    @Test
    void contrasenaValidaProduceHashesConSalesDistintas() {
        char[] clave = "unaClaveLargaSegura!27".toCharArray();
        String primero = PasswordHasher.crearHash(clave);
        String segundo = PasswordHasher.crearHash(clave);
        assertNotEquals(primero, segundo);
        assertTrue(PasswordHasher.verificar(clave, primero));
        assertFalse(PasswordHasher.verificar("claveIncorrecta!27".toCharArray(), primero));
    }

    @Test
    void rechazaContrasenasCortasYHashesDaniados() {
        assertThrows(IllegalArgumentException.class,
                () -> PasswordHasher.crearHash("123456789".toCharArray()));
        assertFalse(PasswordHasher.verificar("unaClaveLargaSegura!27".toCharArray(), "invalido"));
    }
}
