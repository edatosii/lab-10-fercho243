
package co.umariana.construccion;

import co.umariana.modelo.ArbolGenealogico;
import co.umariana.modelo.ArbolInvalidoException;
import co.umariana.modelo.Persona;
import co.umariana.modelo.Posicion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Construye un árbol genealógico a partir de un texto.
 */
public final class ConstructorArbol {

    private ConstructorArbol() {
    }

    private static class Registro {
        Persona persona;
        int numeroLinea;

        Registro(Persona persona, int numeroLinea) {
            this.persona = persona;
            this.numeroLinea = numeroLinea;
        }
    }

    public static ArbolGenealogico construir(String texto) {

        if (texto == null || texto.trim().isEmpty()) {
            throw new ArbolInvalidoException("El texto está vacío.");
        }

        String[] lineas = texto.split("\\r?\\n", -1);

        List<Registro> registros = new ArrayList<>();
        Set<String> posiciones = new HashSet<>();

        Registro sujeto = null;

        // 1. Leer y validar todas las líneas
        for (int i = 0; i < lineas.length; i++) {

            String linea = lineas[i].trim();

            if (linea.isEmpty()) {
                continue;
            }

            int numeroLinea = i + 1;
            String[] campos = lineas[i].split(";", -1);

            if (campos.length < 3 || campos.length > 4) {
                throw new ArbolInvalidoException(
                    "Línea " + numeroLinea + ": debe tener 3 o 4 campos."
                );
            }

            String posicion = campos[0].trim();
            String nombre = campos[1].trim();

            int nacimiento;
            Integer fallecimiento = null;

            try {
                nacimiento = Integer.parseInt(campos[2].trim());

                if (campos.length == 4 && !campos[3].trim().isEmpty()) {
                    fallecimiento = Integer.parseInt(campos[3].trim());
                }

            } catch (NumberFormatException e) {
                throw new ArbolInvalidoException(
                    "Línea " + numeroLinea + ": los años deben ser números enteros."
                );
            }

            if (!posiciones.add(posicion)) {
                throw new ArbolInvalidoException(
                    "Línea " + numeroLinea + ": la posición " + posicion + " está repetida."
                );
            }

            Persona persona = new Persona(
                posicion, nombre, nacimiento, fallecimiento
            );

            try {
                persona.validar();
            } catch (IllegalArgumentException e) {
                throw new ArbolInvalidoException(
                    "Línea " + numeroLinea + ": " + e.getMessage()
                );
            }

            Registro registro = new Registro(persona, numeroLinea);
            registros.add(registro);

            if (Posicion.esSujeto(posicion)) {
                sujeto = registro;
            }
        }

        // 2. Comprobar que exista el sujeto
        if (sujeto == null) {
            throw new ArbolInvalidoException(
                "Falta la persona con posición 1 (sujeto)."
            );
        }

        // 3. Crear el árbol con el sujeto
        ArbolGenealogico arbol;

        try {
            arbol = new ArbolGenealogico(sujeto.persona);
        } catch (IllegalArgumentException e) {
            throw new ArbolInvalidoException(
                "Línea " + sujeto.numeroLinea + ": " + e.getMessage()
            );
        }

        // 4. Separar ascendientes y descendientes
        List<Registro> ascendientes = new ArrayList<>();
        List<Registro> descendientes = new ArrayList<>();

        for (Registro registro : registros) {

            if (registro == sujeto) {
                continue;
            }

            String posicion = registro.persona.getPosicion();

            if (Posicion.esAscendiente(posicion)) {
                ascendientes.add(registro);
            } else if (Posicion.esDescendiente(posicion)) {
                descendientes.add(registro);
            }
        }

        // 5. Ordenar ascendientes por número Ahnentafel
        ascendientes.sort(
            Comparator.comparingInt(
                r -> Integer.parseInt(r.persona.getPosicion())
            )
        );

        // 6. Ordenar descendientes por generación
        
        descendientes.sort(
            Comparator.comparingInt(
                (Registro r) -> Posicion.generacion(r.persona.getPosicion())
            )
        );


        // 7. Agregar ascendientes
        for (Registro registro : ascendientes) {

            try {
                arbol.agregar(registro.persona);
            } catch (IllegalArgumentException e) {
                throw new ArbolInvalidoException(
                    "Línea " + registro.numeroLinea + ": " + e.getMessage()
                );
            }
        }

        // 8. Agregar descendientes
        for (Registro registro : descendientes) {

            try {
                arbol.agregar(registro.persona);
            } catch (IllegalArgumentException e) {
                throw new ArbolInvalidoException(
                    "Línea " + registro.numeroLinea + ": " + e.getMessage()
                );
            }
        }

        return arbol;
    }
}

