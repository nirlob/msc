# MSC — Model Sources Collector

Sistema para que sitios web externos recojan contenido generado en conversaciones entre el usuario y un LLM, evitando que soluciones útiles se pierdan en el chat.

## Idea

- Ciertas webs exponen un endpoint público de ingesta.
- El usuario, durante una conversación con el LLM, le pide que una respuesta concreta se envíe al endpoint de la web X.
- El LLM, a través de un plugin de opencode, hace un POST con la respuesta (y metadata) al endpoint de esa web.
- La web receptora decide qué hacer con esa "fuente": indexarla, mostrarla, moderarla, etc.
- El usuario también puede enviar texto propio (campo `source-user`) sin que medie una respuesta del modelo.

## Arquitectura

```
+-----------------+      msc_source tool       +-----------------+      POST /sources      +----------------+
|  opencode + LLM |  ----------------------->  |  msc-api (Node) |  ----------------------> | sources.json   |
+-----------------+                            +-----------------+                        +----------------+
        |                                              |
        |  lee config de targets:                      |  persiste entradas en disco
        |  ~/.config/opencode/opencode.jsonc           |
        v
{ "msc": { "<target>": { "url": "http://..." }, ... } }
```

- **Plugin**: `.opencode/plugins/msc.ts` registra la tool `msc_source(target, source-model, source-user, used-model)`.
- **API**: `msc-api/` es un servicio Express mínimo que persiste entradas en `msc-api/sources.json` (gitignored).
- **Targets**: configurados por el usuario en `~/.config/opencode/opencode.jsonc` bajo la clave `msc`.

## Esquema de una entrada

```json
{
  "receivedAt": "2026-08-28T23:17:52.068Z",
  "schemaVersion": "1.0",
  "modelResponse": "Para hacer un POST en fetch...",
  "userText": "",
  "usedModel": "minimax/MiniMax-M3",
  "title": "Cómo hacer un POST con fetch en JavaScript",
  "language": "es",
  "tags": ["javascript", "fetch", "http"],
  "prompt": "¿Cómo hago un POST con fetch?"
}
```

- `modelResponse` — texto de la respuesta del LLM que se quiere guardar.
- `userText` — texto enviado directamente por el usuario sin mediar respuesta del modelo.
- `usedModel` — LLM que ejecuta la tool.
- `title` — título corto autogenerado por el asistente para listados/SEO.
- `language` — código de idioma del contenido (`es`, `en`...). Autogenerado.
- `tags` — array de etiquetas autogeneradas.
- `prompt` — prompt que generó `modelResponse`, para contexto/auditoría.
- `schemaVersion` — versión del esquema, lo añade el server. Sirve para migraciones futuras.
- `receivedAt` — timestamp del servidor, no del cliente.

## Convenciones

- **Sin framework de tests** todavía. Para validar cambios de la API: reiniciar `msc-api` y probar con `curl POST /sources`.
- **Sin TypeScript/build** en `msc-api/`; es Node plano con `require` y CommonJS.
- El plugin se recarga solo reiniciando opencode. El servidor API hay que reiniciarlo manualmente (`kill <pid> && node index.js &`).
- `msc-api/sources.json` está en `.gitignore` — no commitear datos de prueba.
- Campos nuevos en el body deben añadirse en este orden: declarar en el schema de la tool, enviar en el `fetch`, destructurar y guardar en `routes.js`.

## Comandos

```bash
# API
cd msc-api && node index.js          # arranca en :3000
curl -s http://localhost:3000/health

# Probar endpoint
curl -X POST http://localhost:3000/sources \
  -H "Content-Type: application/json" \
  -d '{"sourceModel":"...","sourceUser":"...","usedModel":"..."}'
```

## Pendiente / ideas

- Ampliar `usedModel` a objeto estructurado `{ provider, model, name, capabilities }` consultando `client.config.providers`.
- Añadir `id` (uuid), `type`, `tags`, `metadata` a las entradas.
- Añadir `ip`, `userAgent`, `serverId`, `env`, `schemaVersion`, `contentHash`, `status`.
- Tests automatizados (candidato: `node:test` builtin para no añadir deps).
- Múltiples instancias de `msc-api` / sharding del `sources.json`.
- Plugin equivalente para otros runtimes (no opencode).
