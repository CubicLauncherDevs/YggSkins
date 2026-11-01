# YggSkins

![Build](https://github.com/CubicLauncherDevs/YggSkins/actions/workflows/build.yml/badge.svg)

Plugin de skins para Paper 1.21.x y 26.x con soporte para servidores Yggdrasil (authlib-injector),
skins personalizadas por URL mediante MineSkin y persistencia en SQLite, MySQL/MariaDB o PostgreSQL.

## Compilación

Requiere JDK 21. En NixOS:

```bash
nix shell nixpkgs#jdk21
./gradlew build
```

El artefacto queda en `build/libs/YggSkins-1.0.0.jar` y funciona tanto en Paper 1.21.x (Java 21)
como en Paper 26.x (Java 25), porque solo usa APIs presentes en ambas versiones.

## Instalación

1. Copia el JAR en `plugins/`.
2. Arranca el servidor: se generan `config.yml`, `messages_es.yml`, `messages_en.yml` y `hardcoded_skins.json`.
3. Si usas authlib-injector, no hace falta configurar nada: el API Root se detecta desde el
   argumento `-javaagent:authlib-injector.jar=<url>`. Si el agente solo está cargado en el proxy,
   define `yggdrasil.api-root` a mano.

## Comandos

| Comando | Permiso |
| --- | --- |
| `/skin help` | `yggskins.command` |
| `/skin set <nombre> [classic\|slim]` | `yggskins.command.set` |
| `/skin <nombre>` | `yggskins.command.set` |
| `/skin clear` `/skin reset` | `yggskins.command.clear` |
| `/skin update` `/skin refresh` | `yggskins.command.update` |
| `/skin url <url> [classic\|slim]` | `yggskins.command.url` |
| `/skin info` | `yggskins.command` |
| `/yggskins reload` | `yggskins.admin.reload` |
| `/yggskins status` | `yggskins.admin.status` |
| `/yggskins info <jugador>` | `yggskins.admin.info` |
| `/yggskins applyskin <jugador>` | `yggskins.admin.applyskin` |
| `/yggskins drop <jugador>` | `yggskins.admin.drop` |
| `/yggskins createcustom <nombre> <url\|player:nombre> [variant]` | `yggskins.admin.createcustom` |
| `/yggskins setcustomname <nombre> <texto>` | `yggskins.admin.createcustom` |
| `/yggskins purgeolddata <dias>` | `yggskins.admin.purge` |

`/skin` se registra por código para poder desactivarlo con `commands.register-skin-command: false`
si convive con otro plugin de skins. El alias `/skins` se controla con `commands.register-skins-alias`.

## Placeholders

Con PlaceholderAPI instalado: `%yggskins_skin%`, `%yggskins_source%`, `%yggskins_variant%`,
`%yggskins_texture%`, `%yggskins_signed%` y `%yggskins_has_skin%`.

## Cómo funciona

- Al entrar, se guarda la propiedad `textures` original del jugador (la que entrega authlib-injector).
- Si hay una skin guardada, se aplica un tick después del join con `Player#setPlayerProfile`.
- `/skin clear` borra la selección y restaura la propiedad original; si no existe, la consulta al
  sessionserver por UUID y, si tampoco hay, elimina la propiedad.
- Las skins se deduplican por `(source, source_key, variant)` y se cachean en memoria con TTL.
- Las URLs pasan por un validador que bloquea esquemas no HTTP, IPs privadas, loopback y enlaces locales.

## Skins de Yggdrasil en clientes sin authlib-injector

Las skins que entrega un servidor de autenticacion de terceros (LittleSkin, Ely.by, etc.) van
firmadas con la clave de ese servidor, no con la de Mojang. Un cliente vanilla solo acepta firmas
de Mojang, asi que la skin se aplica y se guarda, pero no se renderiza.

Dos formas de resolverlo:

1. Lanzar el cliente con authlib-injector apuntando al mismo servidor de autenticacion (lo normal
   en servidores que ya usan authlib-injector).
2. Activar la re-firma automatica con MineSkin:

```yaml
providers:
  mineskin:
    resign-yggdrasil: true
```

Con esa opcion el plugin descarga la textura original y la sube a MineSkin, que devuelve la misma
skin firmada por Mojang, de modo que tambien se ve en clientes vanilla. Descargar la imagen desde el
propio servidor evita el fallo de MineSkin cuando el servidor de autenticacion bloquea peticiones de
terceros. Consume una generacion de MineSkin por textura y variante (con cache en memoria), asi que
la textura debe ser accesible publicamente desde el servidor.

## Limitaciones conocidas

- Solo Paper: no hay Spigot, proxy ni mods en esta versión.
- Las skins de MineSkin dependen de un servicio externo (límites de tasa y disponibilidad).
- Si MineSkin devuelve una propiedad sin firma y `allow-unsigned-fallback` es `false`, la skin se rechaza.
- `org.sqlite` no se reubica en el JAR porque su librería nativa se enlaza por nombre de clase.

## Pruebas

```bash
./gradlew test
```

Incluye pruebas del cliente Yggdrasil contra un servidor HTTP local, verificación de firmas RSA,
parseo de texturas, validación de URLs, MineSkin (bloqueo de SSRF) y almacenamiento SQLite.
