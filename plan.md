Plan de implementación — YggSkins
Decisiones confirmadas
- Plataforma: solo Paper.
- Alcance v1: núcleo de SkinRestorer + proveedor Yggdrasil.
- Versiones: un único JAR para 1.21.x → 26.3.
- Skins personalizadas: MineSkin para propiedades firmadas + fallback sin firma con aviso.
- Implementación propia, sin copiar código de SkinRestorer (GPLv3).
1. Objetivo de la v1
Un plugin Paper llamado YggSkins que:
- Aplique skins a jugadores online sin reiniciar ni hacer relog.
- Lea skins desde el servidor authlib-injector/Yggdrasil configurado.
- Restaure la skin original del jugador con /skin clear.
- Permita skins personalizadas por URL usando MineSkin (propiedades firmadas por Mojang, aceptadas por clientes authlib-injector).
- Guarde la selección por UUID para que persista al reconectar.
- Funcione en Paper 1.21.x y 26.x con el mismo JAR.
Funciones incluidas
- /skin set <nombre> [classic|slim]
- /skin <nombre> como atajo
- /skin clear|reset
- /skin update|refresh
- /skin url <url> [classic|slim]
- /skin info
- /skin help
- /yggskins reload
- /yggskins status
- /yggskins info <jugador>
- /yggskins applyskin <jugador>
- /yggskins drop <jugador>
- /yggskins createcustom <nombre> <url|player:nombre> [variant]
- /yggskins setcustomname <nombre> <texto>
- /yggskins purgeolddata <días>
- Permisos yggskins.command.* y yggskins.admin.*
- PlaceholderAPI: skin actual, fuente y URL de textura.
- Almacenamiento SQLite por defecto, MySQL/MariaDB y PostgreSQL opcional.
Fuera de la v1
- Spigot, BungeeCord, Velocity, Fabric y NeoForge.
- GUI de skins, historial, favoritos, búsqueda, edición y /skull.
- API pública para otros plugins.
- Auto-update y dumps de diagnóstico.
2. Stack y compilación
- Gradle Kotlin DSL + wrapper.
- Java toolchain 21 y compilación con --release 21.
- Se compila contra io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT.
- El JAR resultante funciona en Java 25 y en Paper 26.3 porque solo usa APIs presentes en ambas versiones.
- Aplicación de skins con Player#setPlayerProfile(...); sin NMS.
- HTTP con java.net.http.HttpClient y Gson.
- Hilos virtuales de Java 21 para HTTP/DB; aplicación de skin siempre en el hilo principal.
- Dependencias sombreadas y reubicadas:
- Gson
- HikariCP
- sqlite-jdbc
- mariadb-java-client
- postgresql
- bStats opcional
- paper-api y PlaceholderAPI como provided.
Entorno actual
No hay Java, Maven ni Gradle instalados. En NixOS se usará:
nix shell nixpkgs#jdk21 nixpkgs#gradle
gradle wrapper --gradle-version 9.5.0
./gradlew build
Si hiciera falta probar en 26.3: nix shell nixpkgs#jdk25 nixpkgs#gradle.
3. Estructura del proyecto
YggSkins/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradle/wrapper/
└── src/
    ├── main/
    │   ├── java/dev/yggskins/
    │   │   ├── YggSkinsPlugin.java
    │   │   ├── command/
    │   │   │   ├── SkinCommand.java
    │   │   │   └── AdminCommand.java
    │   │   ├── config/
    │   │   │   ├── Config.java
    │   │   │   └── Messages.java
    │   │   ├── skin/
    │   │   │   ├── SkinProperty.java
    │   │   │   ├── SkinVariant.java
    │   │   │   ├── SkinSource.java
    │   │   │   ├── SkinService.java
    │   │   │   ├── SkinApplier.java
    │   │   │   └── SkinCache.java
    │   │   ├── provider/
    │   │   │   ├── SkinProvider.java
    │   │   │   ├── YggdrasilProvider.java
    │   │   │   ├── MineSkinProvider.java
    │   │   │   ├── MojangProvider.java
    │   │   │   └── CustomProvider.java
    │   │   ├── yggdrasil/
    │   │   │   ├── YggdrasilClient.java
    │   │   │   ├── YggdrasilMetadata.java
    │   │   │   ├── YggdrasilProfile.java
    │   │   │   └── SignatureVerifier.java
    │   │   ├── storage/
    │   │   │   ├── Storage.java
    │   │   │   ├── SqlStorage.java
    │   │   │   └── model/
    │   │   ├── listener/
    │   │   │   └── PlayerJoinListener.java
    │   │   ├── placeholder/
    │   │   │   └── YggSkinsExpansion.java
    │   │   └── util/
    │   │       ├── Http.java
    │   │       ├── Text.java
    │   │       ├── UuidUtil.java
    │   │       └── UrlValidator.java
    │   └── resources/
    │       ├── plugin.yml
    │       ├── config.yml
    │       ├── messages_es.yml
    │       ├── messages_en.yml
    │       └── hardcoded_skins.json
    └── test/
        └── java/dev/yggskins/...
4. Integración con authlib-injector
Detección automática del API Root
Orden de resolución:
1. yggdrasil.api-root en config.yml.
2. Argumentos JVM:
- ManagementFactory.getRuntimeMXBean().getInputArguments()
- Buscar -javaagent:...authlib-injector...=<url>.
3. Si no se detecta, el proveedor Yggdrasil queda desactivado y se muestra un aviso claro en consola.
También se leerá authlibinjector.yggdrasil.prefetched si existe, únicamente para diagnóstico/metadata.
Endpoints usados
GET  {api-root}/
POST {api-root}/api/profiles/minecraft
GET  {api-root}/sessionserver/session/minecraft/profile/{uuid}?unsigned=false
- POST /api/profiles/minecraft recibe ["nombre"] y devuelve [{id, name}].
- El perfil devuelve properties[]; se extrae la propiedad name=textures.
- Se conserva value + signature.
- Si unsigned=false no devuelve firma:
- con require-signature: true se rechaza con mensaje claro;
- con false se aplica y se avisa de que puede no verse en clientes authlib-injector.
Verificación de firma
- Se obtiene signaturePublickey desde GET {api-root}/.
- Se verifica la propiedad con SHA1withRSA antes de aplicarla, si verify-signature: true.
- Esto evita aplicar propiedades manipuladas y detecta claves rotadas.
Comportamiento al entrar
1. Se captura la propiedad textures original del GameProfile del jugador.
2. Se guarda en la base de datos como skin original.
3. Si hay una skin guardada para ese UUID, se aplica 1 tick después del join para sobrescribir la de authlib-injector.
4. Si no hay skin guardada, no se toca el perfil.
/skin clear
1. Se borra la selección guardada.
2. Se restaura la propiedad original guardada.
3. Si no existe, se vuelve a consultar al Yggdrasil por UUID/nombre.
4. Si tampoco se puede, se elimina la propiedad textures y se avisa al jugador.
5. Aplicación de la skin
PlayerProfile profile = player.getPlayerProfile();
profile.getProperties().removeIf(p -> p.getName().equals("textures"));
profile.getProperties().add(new ProfileProperty("textures", value, signature));
player.setPlayerProfile(profile);
player.sendHealthUpdate(); // con try/catch por compatibilidad
- Solo en el hilo principal.
- Se conservan las demás propiedades del perfil (uploadableTextures, etc.).
- No se usa NMS.
- Funciona en Paper 1.21.1, 1.21.11 y 26.3.
6. Skins personalizadas con MineSkin
Flujo
1. Validar URL:
- solo http/https;
- bloquear IPs privadas/localhost para evitar SSRF;
- longitud máxima;
- opcional lista de dominios permitidos.
2. POST https://api.mineskin.org/v2/generate
{
  "variant": "classic",
  "visibility": "public",
  "url": "https://ejemplo.com/skin.png"
}
3. Leer skin.texture.data.value y skin.texture.data.signature.
4. Guardar la propiedad firmada y aplicarla.
5. Si MineSkin falla:
- reintentos con backoff para 429/5xx;
- si allow-unsigned-fallback: true, generar propiedad sin firma y avisar;
- si no, mostrar error.
Se soportará api-key opcional y caché por url + variant para no regenerar la misma skin.
7. Almacenamiento
Tablas
schema_version(version)

players(
  uuid PK,
  skin_id FK NULL,
  original_value TEXT,
  original_signature TEXT,
  updated_at BIGINT
)

skins(
  id PK,
  value TEXT,
  signature TEXT,
  variant TEXT,
  source TEXT,
  source_key TEXT,
  created_at BIGINT
)

custom_skins(
  name PK,
  skin_id FK,
  display_name TEXT
)
- skins tendrá índice único por (source, source_key, variant) para deduplicar.
- SQLite por defecto en plugins/YggSkins/yggskins.db.
- MySQL/MariaDB y PostgreSQL con HikariCP.
- Migraciones automáticas al arrancar.
- Caché en memoria con TTL configurable.
8. Configuración inicial
config.yml incluirá, como mínimo:
locale: es

yggdrasil:
  enabled: true
  api-root: ""          # vacío = autodetectar desde -javaagent
  require-signature: true
  verify-signature: true
  timeout-seconds: 10
  cache-seconds: 300

providers:
  order: [yggdrasil, custom, mojang]
  mojang:
    enabled: false
  mineskin:
    enabled: true
    api-key: ""
    visibility: public
    allow-unsigned-fallback: true
    allowed-domains: []

storage:
  type: sqlite         # sqlite | mysql | postgresql
  sqlite:
    file: yggskins.db
  mysql:
    host: localhost
    port: 3306
    database: yggskins
    username: root
    password: ""
  postgresql:
    host: localhost
    port: 5432
    database: yggskins
    username: postgres
    password: ""

skins:
  refresh-on-join: false
  apply-delay-ticks: 1
  cooldown-seconds: 30
  default-skin: ""

commands:
  register-skin-command: true
  register-skins-alias: true
9. Comandos y permisos
Jugador
Comando	Permiso
/skin	yggskins.command
/skin help	yggskins.command
/skin set <nombre> [variant]	yggskins.command.set
/skin <nombre>	yggskins.command.set
/skin clear|reset	yggskins.command.clear
/skin update|refresh	yggskins.command.update
/skin url <url> [variant]	yggskins.command.url
/skin info	yggskins.command
Admin
Comando	Permiso
/yggskins reload	yggskins.admin.reload
/yggskins status	yggskins.admin.status
/yggskins info <jugador>	yggskins.admin.info
/yggskins applyskin <jugador>	yggskins.admin.applyskin
/yggskins drop <jugador>	yggskins.admin.drop
/yggskins createcustom ...	yggskins.admin.createcustom
/yggskins setcustomname ...	yggskins.admin.createcustom
/yggskins purgeolddata <días>	yggskins.admin.purge
- /skin se puede desactivar desde configuración para evitar conflicto con SkinRestorer.
- /skins se registrará como alias opcional.
- Autocompletado de jugadores online, skins custom y variantes.
10. Pruebas
Unitarias
- YggdrasilClient contra un HttpServer local:
- metadata;
- nombre → UUID;
- perfil firmado;
- perfil sin firma;
- 204, 429, 500 y timeout.
- Verificación de firma con un par RSA generado en el test.
- Parseo de Base64, variante slim/classic, URL de textura.
- Validación de URLs y bloqueo de IPs privadas.
- MineSkinProvider contra un mock de la API.
- SqlStorage con SQLite temporal: guardar, leer, borrar, deduplicar.
Manuales
- Paper 1.21.11 en offline mode.
- Paper 1.21.11 con authlib-injector y LittleSkin.
- Paper 26.3 con authlib-injector.
- /skin set, /skin clear, /skin update, /skin url.
- Persistencia tras reiniciar el servidor.
- MineSkin funcionando y MineSkin caído.
- Jugador sin firma en el perfil Yggdrasil.
Comandos de verificación
./gradlew test
./gradlew clean build
Resultado esperado: build/libs/YggSkins-1.0.0.jar.
11. Riesgos y limitaciones conocidas
- Firma en authlib-injector: las skins personalizadas sin firma pueden no mostrarse. Por eso MineSkin es el camino principal.
- MineSkin: depende de un servicio externo, límites de tasa y disponibilidad.
- authlib-injector en proxy: si solo está cargado en el proxy, la autodetección puede fallar en el backend; se resolverá con yggdrasil.api-root.
- Paper 26.x: requiere Java 25 en tiempo de ejecución, aunque el JAR esté compilado para Java 21.
- Sin Spigot/proxy/mods en v1: evita NMS y mantiene un único artefacto estable.
- Conflicto de comandos: si SkinRestorer está instalado, desactivar /skin en YggSkins o usar solo /yggskins.
12. Orden de implementación
1. Scaffolding Gradle, plugin.yml, configuración, mensajes y wrapper.
2. Modelo de skin, SkinApplier, listener de join y captura de skin original.
3. Cliente Yggdrasil, autodetección, verificación de firma y proveedor.
4. Comandos de jugador, admin, permisos y autocompletado.
5. MineSkin, validación de URLs y skins custom.
6. Almacenamiento SQLite/MySQL/PostgreSQL y caché.
7. PlaceholderAPI y bStats opcional.
8. Tests unitarios y matriz manual en 1.21.11 y 26.3.
9. Ajustes finales de compatibilidad y build reproducible.