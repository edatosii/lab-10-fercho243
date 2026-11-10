package co.umariana.dao;
import co.umariana.modelo.Persona;
import co.umariana.conexion.ConexionBD;
import co.umariana.modelo.ArbolGenealogico;
import co.umariana.modelo.ResumenArbol;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO (Data Access Object): la única clase que habla con la base de datos. Guarda los árboles genealógicos
 * en las tablas "arboles" y "miembros" (ver db/schema.sql). Se construye igual que EstudianteDAO del laboratorio 9.
 *
 * INSTRUCCIONES PARA EL ESTUDIANTE (Etapa 4)
 * ------------------------------------------
 * 1. Cada método marcado con "TODO" lanza UnsupportedOperationException. Reemplace esa línea por su
 *    implementación con JDBC.
 * 2. NO modifique las pruebas (src/test). Las pruebas NO necesitan MySQL: usan una base de datos H2 en memoria.
 * 3. Esta etapa usa las anteriores: para guardar recorra arbol.miembros(); para cargar, reconstruya el árbol con
 *    new ArbolGenealogico(sujeto) y arbol.agregar(...).
 *
 * REGLAS (las pruebas las verifican)
 *  - Los métodos NO lanzan SQLException: la capturan, imprimen el mensaje con System.out.println y devuelven
 *    un valor "de error" (-1, false, lista vacía o null).
 *  - Use SIEMPRE PreparedStatement con parámetros (?), nunca concatene datos dentro del SQL.
 *  - Guardar un árbol son VARIAS sentencias (el árbol y todos sus miembros): hágalo en una TRANSACCIÓN
 *    (con.setAutoCommit(false); ... con.commit(); y con.rollback() si algo falla) para que nunca quede
 *    guardado "a medias". Antes de cerrar la conexión vuelva a dejar setAutoCommit(true).
 *  - El id de cada fila lo genera la base de datos (AUTO_INCREMENT).
 *  - Los miembros se guardan en el orden de arbol.miembros(): así, al leerlos ORDER BY id, cada persona
 *    aparece después de la que necesita para poder agregarla al árbol.
 */
public class ArbolDAO {

    // =====================================================================
    // ETAPA 4.1. Guardar un árbol nuevo (INSERT en dos tablas, con transacción)
    // =====================================================================

    /**
     * Guarda un árbol nuevo: una fila en "arboles" (con el nombre) y una fila en "miembros" por cada persona.
     *
     * @return el id que la base de datos le dio al árbol; -1 si el nombre es nulo, está en blanco o tiene más
     *         de 100 caracteres, si el árbol es null, o si hubo un error (en ese caso NO queda nada guardado)
     */
    
    public long guardar(String nombre, ArbolGenealogico arbol) {

        // Validar los datos recibidos
        if (nombre == null || nombre.isBlank() || nombre.length() > 100 || arbol == null) {
            return -1;
        }

        try (Connection con = ConexionBD.conectar()) {

            // Iniciar transacción
            con.setAutoCommit(false);

            try {
                long idArbol;

                // Insertar el árbol
                String sqlArbol = "INSERT INTO arboles (nombre) VALUES (?)";

                try (PreparedStatement ps = con.prepareStatement(
                        sqlArbol, Statement.RETURN_GENERATED_KEYS)) {

                    ps.setString(1, nombre);
                    ps.executeUpdate();

                    // Obtener el ID generado por la base de datos
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (!rs.next()) {
                            throw new SQLException("No se pudo obtener el ID del árbol");
                        }

                        idArbol = rs.getLong(1);
                    }
                }

                // Insertar los miembros del árbol
                String sqlMiembros = "INSERT INTO miembros "
                        + "(arbol_id, posicion, nombre, nacimiento, fallecimiento) "
                        + "VALUES (?, ?, ?, ?, ?)";

                try (PreparedStatement ps = con.prepareStatement(sqlMiembros)) {

                    for (var persona : arbol.miembros()) {

                        ps.setLong(1, idArbol);
                        ps.setString(2, persona.getPosicion());
                        ps.setString(3, persona.getNombre());
                        ps.setInt(4, persona.getNacimiento());

                        if (persona.getFallecimiento() == null) {
                            ps.setNull(5, java.sql.Types.INTEGER);
                        } else {
                            ps.setInt(5, persona.getFallecimiento());
                        }

                        ps.executeUpdate();
                    }
                }

                // Confirmar la transacción
                con.commit();

                return idArbol;

            } catch (SQLException e) {

                // Si algo falla, deshacer todos los cambios
                try {
                    con.rollback();
                } catch (SQLException errorRollback) {
                    System.out.println(errorRollback.getMessage());
                }

                throw e;

            } finally {

                // Restaurar el auto-commit
                try {
                    con.setAutoCommit(true);
                } catch (SQLException e) {
                    System.out.println(e.getMessage());
                }
            }

        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return -1;
        }
    }

    // =====================================================================
    // ETAPA 4.2. Listar (SELECT con JOIN y COUNT)
    // =====================================================================

    /**
     * @return un resumen por cada árbol guardado (id, nombre, cuántos miembros tiene y cuándo se creó),
     *         ordenados por id; lista vacía si no hay árboles o si hay un error
     */
    
public List<ResumenArbol> listar() {

    List<ResumenArbol> lista = new ArrayList<>();

    String sql = "SELECT a.id, a.nombre, a.creado, COUNT(m.id) AS total "
            + "FROM arboles a "
            + "LEFT JOIN miembros m ON m.arbol_id = a.id "
            + "GROUP BY a.id, a.nombre, a.creado "
            + "ORDER BY a.id";

    try (Connection con = ConexionBD.conectar();
         PreparedStatement ps = con.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {

        while (rs.next()) {

            long id = rs.getLong("id");
            String nombre = rs.getString("nombre");
            int totalMiembros = rs.getInt("total");

            java.sql.Timestamp timestamp = rs.getTimestamp("creado");

            java.time.LocalDateTime creado = timestamp == null
                    ? null
                    : timestamp.toLocalDateTime();

            ResumenArbol resumen = new ResumenArbol(
                    id,
                    nombre,
                    totalMiembros,
                    creado
            );

            lista.add(resumen);
        }

    } catch (SQLException e) {
        System.out.println(e.getMessage());
        return new ArrayList<>();
    }

    return lista;
}


    // =====================================================================
    // ETAPA 4.3. Cargar un árbol (SELECT y reconstrucción del árbol)
    // =====================================================================

    /**
     * @return el árbol con ese id, reconstruido con todos sus miembros; null si no existe ese id o si hay un error
     */
    public ArbolGenealogico cargar(long id) {

    String sql = "SELECT posicion, nombre, nacimiento, fallecimiento "
            + "FROM miembros "
            + "WHERE arbol_id = ? "
            + "ORDER BY id";

    try (Connection con = ConexionBD.conectar();
         PreparedStatement ps = con.prepareStatement(sql)) {

        ps.setLong(1, id);

        try (ResultSet rs = ps.executeQuery()) {

            ArbolGenealogico arbol = null;

            while (rs.next()) {

                String posicion = rs.getString("posicion");
                String nombre = rs.getString("nombre");
                int nacimiento = rs.getInt("nacimiento");

                int valorFallecimiento = rs.getInt("fallecimiento");

                Integer fallecimiento = rs.wasNull()
                        ? null
                        : valorFallecimiento;

                Persona persona = new Persona(
                        posicion,
                        nombre,
                        nacimiento,
                        fallecimiento
                );

                if (arbol == null) {
                    arbol = new ArbolGenealogico(persona);
                } else {
                    arbol.agregar(persona);
                }
            }

            return arbol;
        }

    } catch (SQLException | IllegalArgumentException e) {
        System.out.println(e.getMessage());
        return null;
    }
}

    // =====================================================================
    // ETAPA 4.4. Actualizar (reemplazar los miembros de un árbol, con transacción)
    // =====================================================================

    /**
     * Guarda los cambios de un árbol ya existente (por ejemplo, después de agregar o eliminar miembros):
     * deja en la base de datos exactamente los miembros de {@code arbol}. El nombre del árbol NO cambia.
     *
     * @return true si se actualizó; false si no existe un árbol con ese id, si el árbol es null o si hubo un
     *         error (en ese caso NO se modifica nada: ni se borran los miembros anteriores)
     */
    public boolean actualizar(long id, ArbolGenealogico arbol) {

    if (arbol == null) {
        return false;
    }

    try (Connection con = ConexionBD.conectar()) {

        con.setAutoCommit(false);

        try {

            // Verificar que el árbol exista
            String sqlExiste = "SELECT id FROM arboles WHERE id = ?";

            try (PreparedStatement ps = con.prepareStatement(sqlExiste)) {

                ps.setLong(1, id);

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        con.rollback();
                        return false;
                    }
                }
            }

            // Eliminar los miembros anteriores del árbol
            String sqlEliminar = "DELETE FROM miembros WHERE arbol_id = ?";

            try (PreparedStatement ps = con.prepareStatement(sqlEliminar)) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }

            // Insertar los nuevos miembros
            String sqlInsertar = "INSERT INTO miembros "
                    + "(arbol_id, posicion, nombre, nacimiento, fallecimiento) "
                    + "VALUES (?, ?, ?, ?, ?)";

            try (PreparedStatement ps = con.prepareStatement(sqlInsertar)) {

                for (Persona persona : arbol.miembros()) {

                    ps.setLong(1, id);
                    ps.setString(2, persona.getPosicion());
                    ps.setString(3, persona.getNombre());
                    ps.setInt(4, persona.getNacimiento());

                    if (persona.getFallecimiento() == null) {
                        ps.setNull(5, java.sql.Types.INTEGER);
                    } else {
                        ps.setInt(5, persona.getFallecimiento());
                    }

                    ps.executeUpdate();
                }
            }

            // Confirmar los cambios
            con.commit();

            return true;

        } catch (SQLException e) {

            // Si algo falla, recuperar los miembros anteriores
            con.rollback();

            System.out.println(e.getMessage());

            return false;

        } finally {
            con.setAutoCommit(true);
        }

    } catch (SQLException e) {
        System.out.println(e.getMessage());
        return false;
    }
}

    // =====================================================================
    // ETAPA 4.5. Eliminar (DELETE)
    // =====================================================================

    /**
     * Elimina el árbol y todos sus miembros (la base de datos borra los miembros sola: ON DELETE CASCADE).
     *
     * @return true si se eliminó; false si no existía ese id o si hubo un error
     */
    public boolean eliminar(long id) {

    String sql = "DELETE FROM arboles WHERE id = ?";

    try (Connection con = ConexionBD.conectar();
         PreparedStatement ps = con.prepareStatement(sql)) {

        ps.setLong(1, id);

        int filasEliminadas = ps.executeUpdate();

        return filasEliminadas > 0;

    } catch (SQLException e) {
        System.out.println(e.getMessage());
        return false;
    }
}
}
