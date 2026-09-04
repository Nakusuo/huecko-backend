/*
 * Inicialización de la base Mongo de Huecko.
 * Se ejecuta UNA sola vez, cuando el volumen `huecko-mongo-data` está vacío.
 *
 * Aquí solo se crea la colección. Los ÍNDICES los declara la aplicación
 * (@Indexed y @CompoundIndex en BloqueHorario, con auto-index-creation: true):
 * si se crearan también aquí con otro nombre pero la misma clave, MongoDB
 * rechazaría el índice duplicado y la app no arrancaría.
 *
 * Los documentos de demo los inserta `DemoDataSeeder`, porque necesitan el
 * UUID del usuario que vive en Postgres.
 */
const dbName = process.env.MONGO_INITDB_DATABASE || 'huecko';
const target = db.getSiblingDB(dbName);

target.createCollection('bloques_horario');

print(`[huecko] base '${dbName}' inicializada`);
